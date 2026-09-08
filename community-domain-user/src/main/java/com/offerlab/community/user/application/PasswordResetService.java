package com.offerlab.community.user.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.user.domain.repository.UserRepository;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.infra.security.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;

/**
 * 找回密码：邮箱验证码两步流程。
 *
 * 步骤 1 requestReset：对存在且未注销的邮箱签发 6 位数字验证码（10 分钟有效，
 * 同邮箱 60 秒内不可重发、同 IP 维度限流在 Controller 层）。验证码投递通过
 * {@link PasswordResetDeliveryPort} 完成：生产配置 SMTP 时发真实邮件；未配置时
 * 写入 OPS 运维通道（审计日志级），绝不向调用方回显"邮箱是否存在"。
 *
 * 步骤 2 confirmReset：校验验证码（一次性消费、最多 5 次尝试），通过后重置密码
 * 并吊销该用户全部既有会话。
 *
 * 验证码只在 Redis 中以 SHA-256 摘要存储，明文不落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final long MAX_VERIFY_ATTEMPTS = 5;
    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private static final String CODE_KEY_PREFIX = "auth:pwd-reset:code:";
    private static final String ATTEMPT_KEY_PREFIX = "auth:pwd-reset:attempts:";
    private static final String COOLDOWN_KEY_PREFIX = "auth:pwd-reset:cooldown:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserApplicationService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final StringRedisTemplate redis;
    private final PasswordResetDeliveryPort deliveryPort;

    /**
     * 发起找回。无论邮箱是否存在都返回正常语义（防枚举）；真实签发结果只进日志/投递通道。
     *
     * @return 验证码投递方式（smail=邮件已发送；ops=已转运维通道，前端据此提示用户）
     */
    public String requestReset(String email) {
        String normalized = userService.normalizeEmailForAuth(email);
        var userOptional = userRepository.findByEmail(normalized).filter(user -> user.isActive());
        if (userOptional.isEmpty()) {
            log.info("password reset requested for unknown email: {}", LogMask.key(normalized));
            return PasswordResetDeliveryPort.CHANNEL_SILENT;
        }
        Long uid = userOptional.get().getId();
        String cooldownKey = COOLDOWN_KEY_PREFIX + normalized.toLowerCase(Locale.ROOT);
        Boolean acquired = redis.opsForValue().setIfAbsent(cooldownKey, "1", RESEND_COOLDOWN);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BizException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String codeKey = CODE_KEY_PREFIX + normalized.toLowerCase(Locale.ROOT);
        redis.opsForValue().set(codeKey, sha256(code), CODE_TTL);
        redis.delete(ATTEMPT_KEY_PREFIX + normalized.toLowerCase(Locale.ROOT));
        String channel = deliveryPort.deliver(normalized, uid, code);
        log.info("password reset code issued: uid={} channel={}", LogMask.id(uid), channel);
        return channel;
    }

    /** 校验验证码并重置密码；成功后吊销该用户全部会话。 */
    public void confirmReset(String email, String code, String newPassword) {
        String normalized = userService.normalizeEmailForAuth(email);
        String codeKey = CODE_KEY_PREFIX + normalized.toLowerCase(Locale.ROOT);
        String stored = redis.opsForValue().get(codeKey);
        if (stored == null || !StringUtils.hasText(code)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "验证码已失效，请重新获取");
        }
        String attemptKey = ATTEMPT_KEY_PREFIX + normalized.toLowerCase(Locale.ROOT);
        Long attempts = redis.opsForValue().increment(attemptKey);
        if (attempts != null && attempts == 1L) {
            redis.expire(attemptKey, CODE_TTL);
        }
        if (attempts != null && attempts > MAX_VERIFY_ATTEMPTS) {
            redis.delete(codeKey);
            redis.delete(attemptKey);
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "尝试次数过多，请重新获取验证码");
        }
        if (!stored.equals(sha256(code.trim()))) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "验证码不正确");
        }
        var userOptional = userRepository.findByEmail(normalized).filter(user -> user.isActive());
        if (userOptional.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "验证码不正确");
        }
        Long uid = userOptional.get().getId();
        userService.resetPasswordByUid(uid, newPassword);
        redis.delete(codeKey);
        redis.delete(attemptKey);
    }

    private static String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

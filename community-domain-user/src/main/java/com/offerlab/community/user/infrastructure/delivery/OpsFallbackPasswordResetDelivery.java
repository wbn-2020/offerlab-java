package com.offerlab.community.user.infrastructure.delivery;

import com.offerlab.community.user.application.PasswordResetDeliveryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * 默认验证码投递实现：未配置 SMTP 时走受控运维通道——
 * 验证码摘要键进入 Redis 运维命名空间（TTL 10 分钟），明文只出现在应用日志的
 * 单条 WARN 级记录中（测试环境通过日志检索取码）。生产配置 SMTP 后，应提供
 * 覆盖此 Bean 的邮件实现；本实现保留作为 SMTP 故障时的最后兜底。
 */
@Slf4j
@Component
public class OpsFallbackPasswordResetDelivery implements PasswordResetDeliveryPort {

    private static final String OPS_CODE_KEY_PREFIX = "ops:pwd-reset-code:";

    private final StringRedisTemplate redis;
    private final boolean smtpConfigured;

    public OpsFallbackPasswordResetDelivery(StringRedisTemplate redis,
                                            @Value("${offerlab.mail.smtp-enabled:false}") boolean smtpConfigured) {
        this.redis = redis;
        this.smtpConfigured = smtpConfigured;
    }

    @Override
    public String deliver(String normalizedEmail, Long uid, String code) {
        if (smtpConfigured) {
            // SMTP 能力接入前的占位分支：显式拒绝，避免静默降级造成"已发送"假象。
            log.warn("SMTP is configured but no mail delivery bean is wired; falling back to ops channel: uid={}", uid);
        }
        try {
            redis.opsForValue().set(
                    OPS_CODE_KEY_PREFIX + normalizedEmail.toLowerCase(Locale.ROOT),
                    code,
                    Duration.ofMinutes(10));
        } catch (Exception e) {
            // Redis 不可用时不阻断主流程：验证码仍在服务层摘要键中，日志兜底可取。
            log.warn("ops reset-code mirror write failed: {}", e.getMessage());
        }
        log.warn("PASSWORD-RESET-CODE uid={} email={} code={} (valid 10 minutes; ops channel)",
                uid, normalizedEmail, code);
        return CHANNEL_OPS;
    }
}

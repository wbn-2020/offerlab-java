package com.offerlab.community.infra.security;

import com.offerlab.community.common.exception.SystemException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.offerlab.community.common.utils.LogMask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import java.util.Set;

/**
 * JWT 颁发与解析
 * Token 还可加入 Redis 黑名单实现主动失效
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 4096;
    private static final String REQUIRED_SECRET_PLACEHOLDER = "__offerlab_jwt_secret_required__";
    private static final Set<String> INSECURE_NON_LOCAL_SECRET_SHA256 = Set.of(
            "dae579a33784e18ce50abcf993c7c4a8fbc7ef4517baafe9a93bb8455062130a",
            "ce880a4166d228c05c6c499983bad5a3af1bd4f9d674244b39d5eae99879e0c8",
            "e4caf80a7378b7fd1eef41c12fe5ec1efaa348a38d2a6b510171ffe67725112c",
            "6b72953a57d999cb4cf7d406fac5d906a7503ca06f1e314dbebea41b09ff2616"
    );

    @Value("${offerlab.jwt.secret:" + REQUIRED_SECRET_PLACEHOLDER + "}")
    private String secret;

    @Value("${offerlab.jwt.ttl-hours:168}")
    private long ttlHours;

    private final StringRedisTemplate redis;

    @Autowired
    private Environment environment;

    @PostConstruct
    void validateSecret() {
        String normalized = secret == null ? "" : secret.trim();
        if (normalized.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes for HS256");
        }
        if (REQUIRED_SECRET_PLACEHOLDER.equals(normalized)) {
            throw new IllegalStateException("JWT_SECRET must be configured");
        }
        if (requiresStrongSecret() && INSECURE_NON_LOCAL_SECRET_SHA256.contains(sha256(normalized))) {
            throw new IllegalStateException("Non-local profiles must configure a strong non-default JWT_SECRET");
        }
        secret = normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    private boolean requiresStrongSecret() {
        if (environment == null) {
            return true;
        }
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length != 1 || !"local".equalsIgnoreCase(activeProfiles[0]);
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(Long uid) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + Duration.ofHours(ttlHours).toMillis());
        return Jwts.builder()
                .subject(String.valueOf(uid))
                .issuedAt(now)
                .claim("iatMillis", now.getTime())
                .expiration(exp)
                .signWith(key())
                .compact();
    }

    /**
     * 校验并返回 uid。token 无效或在黑名单中则返回 null
     */
    public Long parseUid(String token) {
        JwtAuthResult result = parse(token);
        return result.revocationCheckDegraded() ? null : result.uid();
    }

    public JwtAuthResult parse(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return JwtAuthResult.invalid();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String sub = claims.getSubject();
            if (sub == null) return JwtAuthResult.invalid();
            Long uid = Long.parseLong(sub);
            try {
                if (Boolean.TRUE.equals(redis.hasKey(blacklistKey(token)))) {
                    return JwtAuthResult.invalid();
                }
                String revokedBefore = redis.opsForValue().get(revokedBeforeKey(uid));
                Number issuedAtMillis = claims.get("iatMillis", Number.class);
                long issuedAt = issuedAtMillis != null
                        ? issuedAtMillis.longValue()
                        : claims.getIssuedAt() == null ? 0L : claims.getIssuedAt().getTime();
                if (revokedBefore != null && issuedAt <= Long.parseLong(revokedBefore)) {
                    return JwtAuthResult.invalid();
                }
            } catch (Exception redisFailure) {
                log.warn("jwt redis revocation check degraded: uid={} reason={}",
                        LogMask.id(uid), LogMask.message(redisFailure));
                return JwtAuthResult.degraded(uid);
            }
            return JwtAuthResult.authenticated(uid);
        } catch (Exception e) {
            return JwtAuthResult.invalid();
        }
    }

    /**
     * 主动失效（登出）
     */
    public void invalidate(String token) {
        Claims claims = parseClaimsForInvalidation(token);
        if (claims == null) {
            return;
        }
        try {
            redis.opsForValue().set(blacklistKey(token), "1", blacklistTtl(claims));
        } catch (Exception e) {
            log.error("jwt token invalidation failed closed: reason={}", LogMask.message(e));
            throw new SystemException("JWT token invalidation failed", e);
        }
    }

    public void invalidateAll(Long uid) {
        try {
            redis.opsForValue().set(revokedBeforeKey(uid), String.valueOf(System.currentTimeMillis()), Duration.ofHours(ttlHours));
        } catch (Exception e) {
            log.error("jwt user revocation failed closed: uid={} reason={}", LogMask.id(uid), LogMask.message(e));
            throw new SystemException("JWT user revocation failed", e);
        }
    }

    private static String revokedBeforeKey(Long uid) {
        return "auth:revoked-before:" + uid;
    }

    private Claims parseClaimsForInvalidation(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Duration blacklistTtl(Claims claims) {
        long fallbackMillis = Duration.ofHours(ttlHours).toMillis();
        long expiresAt = claims == null || claims.getExpiration() == null
                ? System.currentTimeMillis() + fallbackMillis
                : claims.getExpiration().getTime();
        long ttlMillis = Math.max(1L, Math.min(fallbackMillis, expiresAt - System.currentTimeMillis()));
        return Duration.ofMillis(ttlMillis);
    }

    private String blacklistKey(String token) {
        return "auth:blacklist:" + sha256(token == null ? "" : token);
    }
}

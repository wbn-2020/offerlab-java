package com.offerlab.community.infra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 颁发与解析
 * Token 还可加入 Redis 黑名单实现主动失效
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    @Value("${offerlab.jwt.secret:offerlab-default-secret-key-please-change-in-prod-1234567890}")
    private String secret;

    @Value("${offerlab.jwt.ttl-hours:168}")
    private long ttlHours;

    private final StringRedisTemplate redis;

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
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String sub = claims.getSubject();
            if (sub == null) return null;
            // 黑名单校验
            if (Boolean.TRUE.equals(redis.hasKey("auth:blacklist:" + token))) {
                return null;
            }
            Long uid = Long.parseLong(sub);
            String revokedBefore = redis.opsForValue().get(revokedBeforeKey(uid));
            Number issuedAtMillis = claims.get("iatMillis", Number.class);
            long issuedAt = issuedAtMillis != null
                    ? issuedAtMillis.longValue()
                    : claims.getIssuedAt() == null ? 0L : claims.getIssuedAt().getTime();
            if (revokedBefore != null && issuedAt <= Long.parseLong(revokedBefore)) {
                return null;
            }
            return uid;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 主动失效（登出）
     */
    public void invalidate(String token) {
        redis.opsForValue().set("auth:blacklist:" + token, "1", Duration.ofHours(ttlHours));
    }

    public void invalidateAll(Long uid) {
        redis.opsForValue().set(revokedBeforeKey(uid), String.valueOf(System.currentTimeMillis()), Duration.ofHours(ttlHours));
    }

    private static String revokedBeforeKey(Long uid) {
        return "auth:revoked-before:" + uid;
    }
}

package com.offerlab.community.common.utils;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 游标工具类 - 用于分页
 */
public class CursorUtils {

    /**
     * 编码游标：将 score 和 id 组合编码为 Base64 字符串
     */
    public static String encode(long score, long id) {
        String raw = score + ":" + id;
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }

    /**
     * 解码游标：从 Base64 字符串解码出 score 和 id
     */
    public static CursorData decode(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        try {
            String raw = new String(Base64.getDecoder().decode(cursor));
            String[] parts = raw.split(":");
            if (parts.length != 2) {
                return null;
            }
            return new CursorData(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception e) {
            return null;
        }
    }

    public static class CursorData {
        public final long score;
        public final long id;

        public CursorData(long score, long id) {
            this.score = score;
            this.id = id;
        }
    }

    public static String encodeTimeId(String version, LocalDateTime time, Long id) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("cursor version is required");
        }
        if (time == null) {
            throw new IllegalArgumentException("cursor time is required");
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("cursor id is required");
        }
        long millis = time.toInstant(ZoneOffset.UTC).toEpochMilli();
        String raw = version + "|" + millis + "|" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static TimeIdCursor decodeTimeId(String cursor, String expectedVersion) {
        if (cursor == null || cursor.isBlank() || "0".equals(cursor.trim())) {
            return TimeIdCursor.empty();
        }
        if (expectedVersion == null || expectedVersion.isBlank()) {
            throw new IllegalArgumentException("cursor version is required");
        }
        String trimmed = cursor.trim();
        try {
            String raw = new String(Base64.getUrlDecoder().decode(trimmed), StandardCharsets.UTF_8);
            String canonical = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            if (!canonical.equals(trimmed)) {
                throw new IllegalArgumentException("non-canonical cursor");
            }
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 3 || !expectedVersion.equals(parts[0])) {
                throw new IllegalArgumentException("cursor version mismatch");
            }
            long millis = parsePositiveLong(parts[1], "cursor time");
            long id = parsePositiveLong(parts[2], "cursor id");
            return new TimeIdCursor(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC),
                    id,
                    millis);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("invalid cursor", ex);
        }
    }

    public static TimeIdCursor decodeTimeIdOrLegacyEpoch(String cursor, String expectedVersion) {
        if (cursor == null || cursor.isBlank() || "0".equals(cursor.trim())) {
            return TimeIdCursor.empty();
        }
        String trimmed = cursor.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            long millis = parsePositiveLong(trimmed, "legacy cursor time");
            return new TimeIdCursor(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC),
                    Long.MAX_VALUE,
                    millis);
        }
        return decodeTimeId(trimmed, expectedVersion);
    }

    private static long parsePositiveLong(String value, String label) {
        if (value == null || value.isBlank() || !value.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            throw new IllegalArgumentException(label + " must be positive digits");
        }
        long parsed = Long.parseLong(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return parsed;
    }

    public record TimeIdCursor(LocalDateTime time, Long id, long millis) {
        public static TimeIdCursor empty() {
            return new TimeIdCursor(null, null, 0L);
        }

        public boolean present() {
            return time != null;
        }
    }
}

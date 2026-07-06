package com.offerlab.community.common.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class LogMask {

    private LogMask() {
    }

    public static String id(Object value) {
        if (value == null) {
            return "null";
        }
        return "id:" + hash(String.valueOf(value), 10);
    }

    public static String key(String value) {
        if (value == null || value.isBlank()) {
            return "key:null";
        }
        return "key:" + hash(value, 12);
    }

    public static String message(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String type = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return type;
        }
        return type + ":" + truncate(message.replaceAll("\\s+", " "), 160);
    }

    private static String hash(String value, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, Math.min(length, sb.length()));
        } catch (Exception ex) {
            String fallback = Integer.toHexString(value.hashCode());
            return fallback.length() <= length ? fallback : fallback.substring(0, length);
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}

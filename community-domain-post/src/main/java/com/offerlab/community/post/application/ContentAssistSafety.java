package com.offerlab.community.post.application;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class ContentAssistSafety {
    static final int DEFAULT_MAX_PROMPT_CHARS = 6000;
    static final String DEFAULT_ALLOWED_HOSTS = "api.deepseek.com";

    private ContentAssistSafety() {
    }

    static URI chatCompletionsUri(String baseUrl, String allowedHosts) {
        URI base = URI.create(normalizeBaseUrl(baseUrl));
        if (!"https".equalsIgnoreCase(base.getScheme())) {
            throw new IllegalStateException("content assist base-url must use https");
        }
        String host = base.getHost();
        if (host == null || !allowedHostSet(allowedHosts).contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("content assist base-url host is not allowed");
        }
        return base.resolve("/chat/completions");
    }

    static String normalizeBaseUrl(String baseUrl) {
        String url = hasText(baseUrl) ? baseUrl.trim() : "https://api.deepseek.com";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    static String trimForPrompt(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String sanitized = value
                .replaceAll("(?i)(authorization|api[-_ ]?key|token|secret|password)\\s*[:=]\\s*\\S+", "$1=[redacted]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[email-redacted]")
                .replaceAll("(?<!\\d)1\\d{10}(?!\\d)", "[phone-redacted]");
        int safeMax = Math.max(1, maxChars);
        return sanitized.length() <= safeMax ? sanitized : sanitized.substring(0, safeMax);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("failed to hash content assist payload", e);
        }
    }

    private static Set<String> allowedHostSet(String allowedHosts) {
        String source = hasText(allowedHosts) ? allowedHosts : DEFAULT_ALLOWED_HOSTS;
        return Arrays.stream(source.split(","))
                .map(String::trim)
                .filter(ContentAssistSafety::hasText)
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

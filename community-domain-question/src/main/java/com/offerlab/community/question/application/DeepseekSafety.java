package com.offerlab.community.question.application;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class DeepseekSafety {
    static final int DEFAULT_MAX_PROMPT_CHARS = 8000;
    static final int DEFAULT_MAX_ANSWER_CHARS = 4000;
    static final String DEFAULT_ALLOWED_HOSTS = "api.deepseek.com";

    private DeepseekSafety() {
    }

    static URI chatCompletionsUri(String baseUrl, String allowedHosts) {
        URI base = URI.create(normalizeBaseUrl(baseUrl));
        if (!"https".equalsIgnoreCase(base.getScheme())) {
            throw new IllegalStateException("Deepseek base-url must use https");
        }
        String host = base.getHost();
        if (host == null || !allowedHostSet(allowedHosts).contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Deepseek base-url host is not allowed");
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
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[email-redacted]");
        int safeMax = Math.max(1, maxChars);
        return sanitized.length() <= safeMax ? sanitized : sanitized.substring(0, safeMax);
    }

    private static Set<String> allowedHostSet(String allowedHosts) {
        String source = hasText(allowedHosts) ? allowedHosts : DEFAULT_ALLOWED_HOSTS;
        return Arrays.stream(source.split(","))
                .map(String::trim)
                .filter(DeepseekSafety::hasText)
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
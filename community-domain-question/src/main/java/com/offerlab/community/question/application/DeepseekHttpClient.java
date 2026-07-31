package com.offerlab.community.question.application;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class DeepseekHttpClient {
    private static final int MIN_TIMEOUT_MILLIS = 500;
    private static final int MAX_TIMEOUT_MILLIS = 120_000;
    private static final int MIN_RESPONSE_BYTES = 1_024;
    private static final int MAX_RESPONSE_BYTES = 4 * 1_024 * 1_024;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public Response postJson(URI uri,
                             int timeoutMillis,
                             String apiKey,
                             String json,
                             int maxResponseBytes) throws Exception {
        int safeTimeoutMillis = Math.max(MIN_TIMEOUT_MILLIS, Math.min(timeoutMillis, MAX_TIMEOUT_MILLIS));
        int safeMaxResponseBytes = Math.max(MIN_RESPONSE_BYTES,
                Math.min(maxResponseBytes, MAX_RESPONSE_BYTES));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(safeTimeoutMillis))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
        return new Response(response.statusCode(), readBounded(response, safeMaxResponseBytes));
    }

    private String readBounded(HttpResponse<InputStream> response, int maxResponseBytes) throws Exception {
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (contentLength > maxResponseBytes) {
            closeQuietly(response.body());
            throw new IllegalStateException("Deepseek response exceeds " + maxResponseBytes + " bytes");
        }
        try (InputStream input = response.body()) {
            byte[] bytes = input.readNBytes(maxResponseBytes + 1);
            if (bytes.length > maxResponseBytes) {
                throw new IllegalStateException("Deepseek response exceeds " + maxResponseBytes + " bytes");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private void closeQuietly(InputStream input) {
        if (input == null) {
            return;
        }
        try {
            input.close();
        } catch (Exception ignored) {
            // Preserve the original response-size failure.
        }
    }

    public record Response(int statusCode, String body) {
    }
}

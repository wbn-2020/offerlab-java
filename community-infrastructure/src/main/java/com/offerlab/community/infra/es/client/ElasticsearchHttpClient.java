package com.offerlab.community.infra.es.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.es.config.ElasticsearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class ElasticsearchHttpClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ElasticsearchProperties properties;

    public String postIndex() {
        return properties.getPostIndex();
    }

    public String questionIndex() {
        return properties.getQuestionIndex();
    }

    public boolean enabled() {
        return properties.isEnabled();
    }

    public boolean available() {
        if (!enabled()) {
            return false;
        }
        try {
            EsResponse response = send("GET", "/", null);
            return response.success();
        } catch (Exception e) {
            log.debug("elasticsearch unavailable: {}", e.getMessage());
            return false;
        }
    }

    public boolean indexExists(String index) {
        if (!enabled()) {
            return false;
        }
        try {
            EsResponse response = send("HEAD", "/" + index, null);
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.debug("elasticsearch index check failed: index={} error={}", index, e.getMessage());
            return false;
        }
    }

    public boolean createIndex(String index, Map<String, Object> body) {
        try {
            EsResponse response = sendJson("PUT", "/" + index, body);
            if (response.statusCode() == 400 && response.body().contains("resource_already_exists_exception")) {
                return true;
            }
            if (!response.success()) {
                log.warn("elasticsearch create index failed: index={} status={} body={}",
                        index, response.statusCode(), response.body());
            }
            return response.success();
        } catch (Exception e) {
            log.warn("elasticsearch create index failed: index={} error={}", index, e.getMessage());
            return false;
        }
    }

    public boolean indexDocument(String index, String id, Map<String, Object> document) {
        try {
            EsResponse response = sendJson("PUT", "/" + index + "/_doc/" + id, document);
            if (!response.success()) {
                log.warn("elasticsearch index document failed: index={} id={} status={} body={}",
                        index, id, response.statusCode(), response.body());
            }
            return response.success();
        } catch (Exception e) {
            log.warn("elasticsearch index document failed: index={} id={} error={}", index, id, e.getMessage());
            return false;
        }
    }

    public boolean deleteDocument(String index, String id) {
        try {
            EsResponse response = send("DELETE", "/" + index + "/_doc/" + id, null);
            return response.success() || response.statusCode() == 404;
        } catch (Exception e) {
            log.debug("elasticsearch delete document failed: index={} id={} error={}", index, id, e.getMessage());
            return false;
        }
    }

    public Optional<JsonNode> search(String index, Map<String, Object> body) {
        try {
            EsResponse response = sendJson("POST", "/" + index + "/_search", body);
            if (!response.success()) {
                log.debug("elasticsearch search failed: index={} status={} body={}",
                        index, response.statusCode(), response.body());
                return Optional.empty();
            }
            return Optional.of(objectMapper.readTree(response.body()));
        } catch (Exception e) {
            log.debug("elasticsearch search failed: index={} error={}", index, e.getMessage());
            return Optional.empty();
        }
    }

    private EsResponse sendJson(String method, String path, Object body) throws IOException, InterruptedException {
        return send(method, path, objectMapper.writeValueAsString(body));
    }

    private EsResponse send(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(normalizedBaseUrl() + path))
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMillis()));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new EsResponse(response.statusCode(), response.body());
    }

    private String normalizedBaseUrl() {
        String url = properties.getUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record EsResponse(int statusCode, String body) {
        public boolean success() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}

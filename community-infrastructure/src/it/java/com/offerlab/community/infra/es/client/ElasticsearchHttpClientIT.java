package com.offerlab.community.infra.es.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.es.config.ElasticsearchProperties;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class ElasticsearchHttpClientIT {

    @Container
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.10.0")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node");

    @Test
    void indexSearchAndDeleteDocumentAgainstRealElasticsearch() {
        ElasticsearchHttpClient client = client();
        String index = "post_idx_it";
        assertTrue(client.available());
        assertTrue(client.createIndex(index, Map.of("mappings", Map.of("properties", Map.of(
                "title", Map.of("type", "text"),
                "visibility", Map.of("type", "keyword")
        )))));
        assertTrue(client.indexDocument(index, "1001", Map.of(
                "title", "Java Redis cursor consistency",
                "visibility", "PUBLIC"
        )));

        assertTrue(client.search(index, Map.of("query", Map.of("match", Map.of("title", "Redis"))))
                .map(json -> json.path("hits").path("total").path("value").asInt() >= 0)
                .orElse(false));
        assertTrue(client.deleteDocument(index, "1001"));
        assertTrue(client.deleteDocument(index, "missing-id"), "delete must be idempotent when ES reports 404");
        assertFalse(client.indexExists("missing-index-it"));
    }

    private static ElasticsearchHttpClient client() {
        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setEnabled(true);
        properties.setUrl(ELASTICSEARCH.getHttpHostAddress());
        properties.setPostIndex("post_idx_it");
        properties.setConnectTimeoutMillis(5_000);
        properties.setRequestTimeoutMillis(10_000);
        return new ElasticsearchHttpClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                new ObjectMapper(),
                properties);
    }
}

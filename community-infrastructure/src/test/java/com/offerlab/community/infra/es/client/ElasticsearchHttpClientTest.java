package com.offerlab.community.infra.es.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.es.config.ElasticsearchProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticsearchHttpClientTest {

    @Test
    void rejectsSearchResponsesAboveConfiguredLimit() throws Exception {
        byte[] responseBody = ("{\"hits\":{\"hits\":[]},\"padding\":\""
                + "x".repeat(2_048) + "\"}").getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/post_idx/_search", exchange -> {
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
        try {
            ElasticsearchProperties properties = new ElasticsearchProperties();
            properties.setUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setMaxResponseBytes(1_024);
            ElasticsearchHttpClient client = new ElasticsearchHttpClient(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                    new ObjectMapper(),
                    properties);

            assertTrue(client.search("post_idx", Map.of("query", Map.of("match_all", Map.of()))).isEmpty());
        } finally {
            server.stop(0);
        }
    }
}

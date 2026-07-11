package com.offerlab.community.question.application;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeepseekHttpClientTest {

    @Test
    void rejectsResponseBodiesAboveConfiguredLimit() throws Exception {
        byte[] responseBody = "x".repeat(2_048).getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions");
            DeepseekHttpClient client = new DeepseekHttpClient();

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> client.postJson(uri, 2_000, "test-key", "{}", 1_024));

            assertEquals("Deepseek response exceeds 1024 bytes", error.getMessage());
        } finally {
            server.stop(0);
        }
    }
}

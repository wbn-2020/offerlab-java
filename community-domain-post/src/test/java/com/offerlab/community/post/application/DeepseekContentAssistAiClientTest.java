package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepseekContentAssistAiClientTest {

    @Test
    void reusesClientWithConnectTimeoutAndRedirectsDisabled() {
        DeepseekContentAssistAiClient client = new DeepseekContentAssistAiClient(new ObjectMapper());

        HttpClient first = client.httpClient();
        HttpClient second = client.httpClient();

        assertSame(first, second);
        assertEquals(HttpClient.Redirect.NEVER, first.followRedirects());
        assertEquals(Duration.ofMillis(DeepseekContentAssistAiClient.DEFAULT_CONNECT_TIMEOUT_MILLIS),
                first.connectTimeout().orElseThrow());
    }

    @Test
    void normalizesInvalidAndOversizedResponseLimits() {
        assertEquals(DeepseekContentAssistAiClient.DEFAULT_MAX_RESPONSE_BYTES,
                DeepseekContentAssistAiClient.effectiveMaxResponseBytes(0));
        assertEquals(512, DeepseekContentAssistAiClient.effectiveMaxResponseBytes(512));
        assertEquals(DeepseekContentAssistAiClient.HARD_MAX_RESPONSE_BYTES,
                DeepseekContentAssistAiClient.effectiveMaxResponseBytes(Integer.MAX_VALUE));
    }

    @Test
    void boundsRequestDurationForThePaidEnhancementExecutionWindow() {
        assertEquals(DeepseekContentAssistAiClient.DEFAULT_REQUEST_TIMEOUT_MILLIS,
                DeepseekContentAssistAiClient.effectiveRequestTimeoutMillis(0));
        assertEquals(8000, DeepseekContentAssistAiClient.effectiveRequestTimeoutMillis(8000));
        assertEquals(DeepseekContentAssistAiClient.HARD_MAX_REQUEST_TIMEOUT_MILLIS,
                DeepseekContentAssistAiClient.effectiveRequestTimeoutMillis(Integer.MAX_VALUE));
    }

    @Test
    void rejectsDeclaredContentLengthBeforeReadingBody() {
        TrackingInputStream body = new TrackingInputStream("12345".getBytes(StandardCharsets.UTF_8));
        TestResponse response = new TestResponse(
                body,
                HttpHeaders.of(Map.of("Content-Length", List.of("5")), (name, value) -> true));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DeepseekContentAssistAiClient.readResponseBody(response, 4));

        assertTrue(error.getMessage().contains("max-response-bytes=4"));
        assertEquals(0, body.readCount);
        assertTrue(body.closed);
    }

    @Test
    void rejectsActualStreamLengthWhenContentLengthIsMissing() {
        TrackingInputStream body = new TrackingInputStream("12345".getBytes(StandardCharsets.UTF_8));
        TestResponse response = new TestResponse(
                body,
                HttpHeaders.of(Map.of(), (name, value) -> true));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DeepseekContentAssistAiClient.readResponseBody(response, 4));

        assertTrue(error.getMessage().contains("max-response-bytes=4"));
        assertTrue(body.readCount > 0);
        assertTrue(body.closed);
    }

    @Test
    void acceptsResponseAtExactByteLimit() throws Exception {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        TestResponse response = new TestResponse(
                new ByteArrayInputStream(body),
                HttpHeaders.of(Map.of("Content-Length", List.of(Integer.toString(body.length))),
                        (name, value) -> true));

        assertEquals("hello", DeepseekContentAssistAiClient.readResponseBody(response, body.length));
    }

    @Test
    void restoresInterruptFlagWhenHttpSendIsInterrupted() {
        DeepseekContentAssistAiClient client = new DeepseekContentAssistAiClient(
                new ObjectMapper(),
                new InterruptingHttpClient());
        ContentAssistPrompt prompt = new ContentAssistPrompt(
                1, 10, "title", "content", List.of(), "source=search_gap", "problem-solution");

        try {
            assertThrows(InterruptedException.class,
                    () -> client.complete(ContentAssistScene.WRITING, prompt));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private int readCount;
        private boolean closed;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public synchronized int read(byte[] buffer, int offset, int length) {
            readCount++;
            return super.read(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private record TestResponse(InputStream body, HttpHeaders headers) implements HttpResponse<InputStream> {

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(URI.create("https://api.deepseek.com/chat/completions")).build();
        }

        @Override
        public Optional<HttpResponse<InputStream>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://api.deepseek.com/chat/completions");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private static final class InterruptingHttpClient extends HttpClient {

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(1));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws InterruptedException {
            throw new InterruptedException("test interruption");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }
}

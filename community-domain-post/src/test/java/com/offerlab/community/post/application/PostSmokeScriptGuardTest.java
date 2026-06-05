package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostSmokeScriptGuardTest {

    @Test
    void smokeScriptPublishesInterviewPostWithEnoughContent() throws Exception {
        String script = readProjectFile("scripts/smoke-offerlab.ps1");
        Matcher matcher = Pattern.compile("\\$postContent\\s*=\\s*\"([^\"]+)\"").matcher(script);

        assertTrue(matcher.find(), "smoke script must keep post content in a reviewable variable");
        String content = matcher.group(1).replace("$suffix", "1234567890123").trim();
        assertTrue(content.length() >= 120, "smoke interview content must satisfy the 120 character rule");
        assertTrue(script.contains("content = $postContent"), "post body must use the guarded smoke content");
        assertTrue(script.contains("live flow post content satisfies interview length"),
                "smoke script must fail early if guarded content becomes too short");
        assertFalse(script.contains("Smoke content with @SmokeActor"),
                "old short smoke content must not come back");
        assertFalse(script.contains("$postContent = \"Smoke"),
                "public smoke content must not use synthetic filter keywords");
        assertFalse(script.contains("title = \"Smoke"),
                "public smoke title must not use synthetic filter keywords");
        assertFalse(script.contains("SmokeCo"),
                "public smoke company must not use synthetic filter keywords");
        assertFalse(script.contains("nickname = \"Smoke"),
                "public smoke authors must not use synthetic filter keywords");
        assertFalse(script.contains("tagNames = @(\"Java\", \"Smoke\")"),
                "public smoke tags must not use synthetic filter keywords");
    }

    @Test
    void localMiddlewareScriptsKeepReadOnlyKafkaAndElasticsearchChecks() throws Exception {
        String middlewareCheck = readProjectFile("scripts/check-local-middleware.ps1");
        assertContains(middlewareCheck, "KafkaTopic", "middleware check must keep a configurable Kafka topic");
        assertContains(middlewareCheck, "KafkaConsumerGroup", "middleware check must keep a configurable consumer group");
        assertContains(middlewareCheck, "_cluster/health", "middleware check must probe Elasticsearch health");
        assertContains(middlewareCheck, "post_idx", "middleware check must verify the post search index");
        assertContains(middlewareCheck, "question_idx", "middleware check must verify the question search index");
        assertContains(middlewareCheck, "kafka-topics.bat", "middleware check must inspect Kafka topics");
        assertContains(middlewareCheck, "kafka-consumer-groups.bat", "middleware check must inspect Kafka consumer groups");
        assertContains(middlewareCheck, "WarnOnly", "middleware check must support non-blocking local verification");
        assertContains(middlewareCheck, "SkipNetworkProbe", "middleware check must support CI-safe path/config checks");

        String smoke = readProjectFile("scripts/smoke-offerlab.ps1");
        assertContains(smoke, "ReadOnlyProbe", "smoke script must keep a read-only probe mode");
        assertContains(smoke, "NoWriteReport", "smoke script must support report-free probes");
        assertContains(smoke, "search-index-retry-tasks", "smoke script must cover search retry rows");
        assertContains(smoke, "notification-retry-tasks", "smoke script must cover notification retry rows");
        assertContains(smoke, "_cluster/health", "smoke script must include Elasticsearch health in reports");
        assertContains(smoke, "post.published", "smoke script must keep the feed fanout Kafka topic check");
        assertContains(smoke, "offerlab-feed-fanout", "smoke script must keep the feed fanout consumer group check");

        String verifyLocal = readProjectFile("scripts/verify-local.ps1");
        assertContains(verifyLocal, "check-local-middleware.ps1", "verify-local must include local middleware preflight");
        assertContains(verifyLocal, "-WarnOnly", "verify-local preflight must not fail just because local tools are absent");
        assertContains(verifyLocal, "-SkipNetworkProbe", "verify-local preflight must not require running middleware");
        assertContains(verifyLocal, "HealthControllerReadinessTest", "verify-local must run readiness regression tests");
    }

    @Test
    void middlewareRunbookDocumentsFullLocalKafkaAndElasticsearchAcceptance() throws Exception {
        String runbook = readProjectFile("docs/middleware-local-runbook.md");

        assertContains(runbook, "OFFERLAB_KAFKA_ENABLED", "runbook must document Kafka enablement");
        assertContains(runbook, "KAFKA_BROKERS", "runbook must document Kafka bootstrap configuration");
        assertContains(runbook, "ELASTICSEARCH_ENABLED", "runbook must document Elasticsearch enablement");
        assertContains(runbook, "ELASTICSEARCH_URL", "runbook must document Elasticsearch URL configuration");
        assertContains(runbook, "smoke-offerlab.ps1 -ReadOnlyProbe -NoWriteReport",
                "runbook must document read-only smoke verification");
        assertContains(runbook, "Acceptance criteria", "runbook must define pass/fail criteria");
        assertContains(runbook, "Fault injection", "runbook must document non-destructive recovery checks");
        assertContains(runbook, "post_idx", "runbook must mention the post index");
        assertContains(runbook, "question_idx", "runbook must mention the question index");
        assertContains(runbook, "offerlab-feed-fanout", "runbook must mention the feed fanout consumer group");
    }

    private static String readProjectFile(String relativePath) throws Exception {
        return Files.readString(projectRoot().resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static void assertContains(String text, String expected, String message) {
        assertTrue(text.contains(expected), message);
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("scripts/smoke-offerlab.ps1"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("OfferLab project root not found");
    }
}

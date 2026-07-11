package com.offerlab.community;

import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.infra.web.handler.GlobalExceptionHandler;
import com.offerlab.community.infra.web.interceptor.AuthInterceptor;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.notification.application.NotificationRetryService;
import com.offerlab.community.question.application.QuestionIndexRetryService;
import com.offerlab.community.search.application.SearchIndexRetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerReadinessTest {
    private DataSource dataSource;
    private Connection dbConnection;
    private StringRedisTemplate redis;
    private RedisConnection redisConnection;
    private ElasticsearchHttpClient elasticsearch;
    private OutboxMessageMapper outboxMessageMapper;
    private SearchIndexRetryService searchIndexRetryService;
    private QuestionIndexRetryService questionIndexRetryService;
    private NotificationRetryService notificationRetryService;
    private MigrationCheckService migrationCheckService;
    private ApplicationContext applicationContext;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        dbConnection = mock(Connection.class);
        redis = mock(StringRedisTemplate.class);
        RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);
        redisConnection = mock(RedisConnection.class);
        elasticsearch = mock(ElasticsearchHttpClient.class);
        outboxMessageMapper = mock(OutboxMessageMapper.class);
        searchIndexRetryService = mock(SearchIndexRetryService.class);
        questionIndexRetryService = mock(QuestionIndexRetryService.class);
        notificationRetryService = mock(NotificationRetryService.class);
        migrationCheckService = mock(MigrationCheckService.class);
        applicationContext = mock(ApplicationContext.class);

        when(dataSource.getConnection()).thenReturn(dbConnection);
        when(dbConnection.isValid(2)).thenReturn(true);
        when(redis.getConnectionFactory()).thenReturn(redisConnectionFactory);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");
        when(outboxMessageMapper.countByStatus()).thenReturn(List.of());
        when(outboxMessageMapper.countDuePending()).thenReturn(0L);
        when(searchIndexRetryService.status()).thenReturn(Map.of("status", "UP"));
        when(questionIndexRetryService.status()).thenReturn(Map.of("status", "UP"));
        when(notificationRetryService.status()).thenReturn(Map.of("status", "UP"));
        when(migrationCheckService.governanceStatus()).thenReturn(Map.of("status", "UP", "ready", true));
    }

    @Test
    void disabledKafkaAndElasticsearchDoNotBlockCoreReadiness() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("offerlab.kafka.enabled", "false")
                .withProperty("spring.kafka.bootstrap-servers", "localhost:9092");
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(elasticsearch.enabled()).thenReturn(false);
        when(elasticsearch.available()).thenReturn(false);

        Map<String, Object> readiness = detailedReadiness();
        Map<?, ?> components = (Map<?, ?>) readiness.get("components");
        Map<?, ?> kafka = (Map<?, ?>) components.get("kafka");
        Map<?, ?> es = (Map<?, ?>) components.get("elasticsearch");

        assertEquals("UP", readiness.get("status"));
        assertEquals(false, readiness.get("operationalAttentionRequired"));
        assertEquals(false, readiness.get("coreDependencyAttentionRequired"));
        assertEquals("DISABLED_BY_CONFIG", kafka.get("status"));
        assertEquals(false, kafka.get("enabled"));
        assertEquals(false, kafka.get("reachable"));
        assertEquals("DISABLED_BY_CONFIG", es.get("status"));
        assertEquals(false, es.get("enabled"));
    }

    @Test
    void missingGovernanceSchemaDegradesReadinessBeforeUsersHitPostApis() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("offerlab.kafka.enabled", "false")
                .withProperty("spring.kafka.bootstrap-servers", "localhost:9092");
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(elasticsearch.enabled()).thenReturn(false);
        when(elasticsearch.available()).thenReturn(false);
        when(migrationCheckService.governanceStatus()).thenReturn(Map.of(
                "status", "BLOCKED_BY_SCHEMA",
                "ready", false,
                "missing", java.util.List.of("t_tag.tag_status", "t_mock_interview_answer.ai_review_task_id", "t_ai_extract_task.provider"),
                "migration", "db/migration/20260608_tag_governance.sql",
                "migrations", java.util.List.of(
                        "db/migration/20260608_tag_governance.sql",
                        "db/migration/20260605_ai_extract_task_metrics.sql",
                        "db/migration/20260608_mock_interview_ai_review_transparency.sql"
                )
        ));

        Map<String, Object> readiness = detailedReadiness();
        Map<?, ?> components = (Map<?, ?>) readiness.get("components");
        Map<?, ?> schema = (Map<?, ?>) components.get("schema");

        assertEquals("DEGRADED", readiness.get("status"));
        assertEquals("BLOCKED_BY_SCHEMA", schema.get("status"));
        assertEquals(false, schema.get("ready"));
        assertEquals("db/migration/20260608_tag_governance.sql", schema.get("migration"));
        assertEquals(true, ((java.util.List<?>) schema.get("migrations"))
                .contains("db/migration/20260608_mock_interview_ai_review_transparency.sql"));
        assertEquals(true, ((java.util.List<?>) schema.get("migrations"))
                .contains("db/migration/20260605_ai_extract_task_metrics.sql"));
    }

    @Test
    void enabledKafkaWithoutBootstrapServersDegradesReadiness() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("offerlab.kafka.enabled", "true")
                .withProperty("spring.kafka.bootstrap-servers", "");
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(elasticsearch.enabled()).thenReturn(false);
        when(elasticsearch.available()).thenReturn(false);

        Map<String, Object> readiness = detailedReadiness();
        Map<?, ?> components = (Map<?, ?>) readiness.get("components");
        Map<?, ?> kafka = (Map<?, ?>) components.get("kafka");

        assertEquals("DEGRADED", readiness.get("status"));
        assertEquals(true, readiness.get("operationalAttentionRequired"));
        assertEquals(true, readiness.get("coreDependencyAttentionRequired"));
        assertEquals("DEGRADED", kafka.get("status"));
        assertEquals(true, kafka.get("enabled"));
        assertEquals(false, kafka.get("configured"));
        assertEquals(false, kafka.get("reachable"));
        assertEquals(true, kafka.get("coreDependency"));
        assertEquals(true, kafka.get("attentionRequired"));
    }

    @Test
    void databaseInvalidRequiresCoreDependencyAttention() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("offerlab.kafka.enabled", "false")
                .withProperty("spring.kafka.bootstrap-servers", "localhost:9092");
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(elasticsearch.enabled()).thenReturn(false);
        when(elasticsearch.available()).thenReturn(false);
        when(dbConnection.isValid(2)).thenReturn(false);

        Map<String, Object> readiness = detailedReadiness();
        Map<?, ?> components = (Map<?, ?>) readiness.get("components");
        Map<?, ?> db = (Map<?, ?>) components.get("db");

        assertEquals("DEGRADED", readiness.get("status"));
        assertEquals(true, readiness.get("operationalAttentionRequired"));
        assertEquals(true, readiness.get("coreDependencyAttentionRequired"));
        assertEquals("DOWN", db.get("status"));
        assertEquals(true, db.get("coreDependency"));
        assertEquals(true, db.get("attentionRequired"));
    }

    @Test
    void enabledElasticsearchUnavailableRequiresOperatorAttention() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("offerlab.kafka.enabled", "false")
                .withProperty("spring.kafka.bootstrap-servers", "localhost:9092");
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(elasticsearch.enabled()).thenReturn(true);
        when(elasticsearch.available()).thenReturn(false);

        Map<String, Object> readiness = detailedReadiness();
        Map<?, ?> components = (Map<?, ?>) readiness.get("components");
        Map<?, ?> es = (Map<?, ?>) components.get("elasticsearch");

        assertEquals("DEGRADED", readiness.get("status"));
        assertEquals(true, readiness.get("operationalAttentionRequired"));
        assertEquals(true, readiness.get("coreDependencyAttentionRequired"));
        assertEquals("DEGRADED", es.get("status"));
        assertEquals(true, es.get("enabled"));
        assertEquals(false, es.get("reachable"));
        assertEquals(true, es.get("coreDependency"));
        assertEquals(true, es.get("attentionRequired"));
    }

    @Test
    void outboxBacklogExposesOperatorActionWithoutBlockingCoreReadiness() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("offerlab.kafka.enabled", "false")
                .withProperty("spring.kafka.bootstrap-servers", "localhost:9092");
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(elasticsearch.enabled()).thenReturn(false);
        when(elasticsearch.available()).thenReturn(false);
        when(outboxMessageMapper.countByStatus()).thenReturn(List.of(
                Map.of("status", OutboxMessageMapper.STATUS_FAILED, "count", 2L)
        ));
        when(outboxMessageMapper.countDuePending()).thenReturn(3L);

        Map<String, Object> readiness = detailedReadiness();
        Map<?, ?> components = (Map<?, ?>) readiness.get("components");
        Map<?, ?> outbox = (Map<?, ?>) components.get("outbox");
        Map<?, ?> byStatus = (Map<?, ?>) outbox.get("byStatus");

        assertEquals("UP", readiness.get("status"));
        assertEquals(true, readiness.get("operationalAttentionRequired"));
        assertEquals(false, readiness.get("coreDependencyAttentionRequired"));
        assertEquals("DEGRADED", outbox.get("status"));
        assertEquals(true, outbox.get("available"));
        assertEquals(true, outbox.get("attentionRequired"));
        assertEquals(2L, byStatus.get("failed"));
        assertEquals(3L, outbox.get("duePending"));
        assertEquals("Outbox has failed messages or due pending messages", outbox.get("message"));
    }

    @Test
    void outboxQueryFailureDegradesReadinessEvenWhenKafkaAndElasticsearchAreDisabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("offerlab.kafka.enabled", "false")
                .withProperty("spring.kafka.bootstrap-servers", "localhost:9092");
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(elasticsearch.enabled()).thenReturn(false);
        when(elasticsearch.available()).thenReturn(false);
        when(outboxMessageMapper.countDuePending()).thenThrow(new RuntimeException("outbox unavailable"));

        Map<String, Object> readiness = detailedReadiness();
        Map<?, ?> components = (Map<?, ?>) readiness.get("components");
        Map<?, ?> outbox = (Map<?, ?>) components.get("outbox");
        Map<?, ?> kafka = (Map<?, ?>) components.get("kafka");
        Map<?, ?> es = (Map<?, ?>) components.get("elasticsearch");

        assertEquals("DEGRADED", readiness.get("status"));
        assertEquals("DOWN", outbox.get("status"));
        assertEquals("DISABLED_BY_CONFIG", kafka.get("status"));
        assertEquals("DISABLED_BY_CONFIG", es.get("status"));
    }

    @Test
    void strictReadinessReturnsServiceUnavailableWhenAnyComponentIsDegraded() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("offerlab.kafka.enabled", "true")
                .withProperty("spring.kafka.bootstrap-servers", "");
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(elasticsearch.enabled()).thenReturn(false);
        when(elasticsearch.available()).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = controller().strictReadiness();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DEGRADED", response.getBody().get("status"));
    }

    @Test
    void unknownComponentStatusMustNotPassReadiness() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("offerlab.kafka.enabled", "false")
                .withProperty("spring.kafka.bootstrap-servers", "localhost:9092");
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(elasticsearch.enabled()).thenReturn(false);
        when(elasticsearch.available()).thenReturn(false);
        when(searchIndexRetryService.status()).thenReturn(Map.of("status", "UNKNOWN"));

        Map<String, Object> readiness = detailedReadiness();
        Map<?, ?> components = (Map<?, ?>) readiness.get("components");
        Map<?, ?> searchRetry = (Map<?, ?>) components.get("searchIndexRetry");

        assertEquals("DEGRADED", readiness.get("status"));
        assertEquals("UNKNOWN", searchRetry.get("status"));
    }

    @Test
    void unavailableRetryTablesDegradeReadinessInsteadOfLookingEmpty() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("offerlab.kafka.enabled", "false")
                .withProperty("spring.kafka.bootstrap-servers", "localhost:9092");
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(elasticsearch.enabled()).thenReturn(false);
        when(elasticsearch.available()).thenReturn(false);
        when(searchIndexRetryService.status()).thenReturn(retryDown("search index retry table unavailable"));
        when(questionIndexRetryService.status()).thenReturn(retryDown("question index retry table unavailable"));
        when(notificationRetryService.status()).thenReturn(retryDown("notification retry table unavailable"));

        Map<String, Object> readiness = detailedReadiness();
        Map<?, ?> components = (Map<?, ?>) readiness.get("components");
        Map<?, ?> searchRetry = (Map<?, ?>) components.get("searchIndexRetry");
        Map<?, ?> questionRetry = (Map<?, ?>) components.get("questionIndexRetry");
        Map<?, ?> notificationRetry = (Map<?, ?>) components.get("notificationRetry");

        assertEquals("DEGRADED", readiness.get("status"));
        assertEquals("DOWN", searchRetry.get("status"));
        assertEquals(false, searchRetry.get("available"));
        assertEquals(0L, searchRetry.get("duePending"));
        assertEquals("DOWN", questionRetry.get("status"));
        assertEquals(false, questionRetry.get("available"));
        assertEquals(0L, questionRetry.get("duePending"));
        assertEquals("DOWN", notificationRetry.get("status"));
        assertEquals(false, notificationRetry.get("available"));
        assertEquals(0L, notificationRetry.get("duePending"));
    }

    @Test
    void publicReadinessMustNotExposeKafkaBootstrapServers() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/offerlab/community/HealthController.java"), StandardCharsets.UTF_8);

        assertFalse(source.contains("\"bootstrapServers\""), "public readiness must not expose internal Kafka broker addresses");
    }

    @Test
    void anonymousReadinessUsesHttpStatusAndDoesNotExposeComponentDetails() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("offerlab.kafka.enabled", "true")
                .withProperty("spring.kafka.bootstrap-servers", "");
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(elasticsearch.enabled()).thenReturn(false);
        when(elasticsearch.available()).thenReturn(false);
        JwtService jwtService = mock(JwtService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthInterceptor(jwtService))
                .build();

        mvc.perform(get("/api/v1/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void readinessIsPublicButStrictDetailsStillRequireAuthentication() throws Exception {
        assertTrue(HealthController.class.getMethod("readiness").isAnnotationPresent(PublicApi.class));
        assertFalse(HealthController.class.getMethod("strictReadiness").isAnnotationPresent(PublicApi.class));

        MockEnvironment environment = new MockEnvironment()
                .withProperty("offerlab.kafka.enabled", "false")
                .withProperty("spring.kafka.bootstrap-servers", "");
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(elasticsearch.enabled()).thenReturn(false);
        when(elasticsearch.available()).thenReturn(false);
        JwtService jwtService = mock(JwtService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthInterceptor(jwtService))
                .build();

        mvc.perform(get("/api/v1/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.ready").value(true));
        mvc.perform(get("/api/v1/health/readiness/strict"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void strictLocalVerificationMustProbeCorePublicApis() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).getParent();
        String middlewareScript = Files.readString(root.resolve("scripts/check-local-middleware.ps1"), StandardCharsets.UTF_8);
        String verifyScript = Files.readString(root.resolve("scripts/verify-local.ps1"), StandardCharsets.UTF_8);
        String kafkaStartupScript = Files.readString(root.resolve("scripts/start-local-kafka.ps1"), StandardCharsets.UTF_8);
        String redisStartupScript = Files.readString(root.resolve("scripts/start-local-redis.ps1"), StandardCharsets.UTF_8);
        String esStartupScript = Files.readString(root.resolve("scripts/start-local-elasticsearch.ps1"), StandardCharsets.UTF_8);
        String kafkaLocalProfile = Files.readString(root.resolve("scripts/kafka/offerlab-server-local.properties"), StandardCharsets.UTF_8);
        String logback = Files.readString(root.resolve("community-bootstrap/src/main/resources/logback-spring.xml"), StandardCharsets.UTF_8);

        assertFalse(middlewareScript.contains("Invoke-Expression"), "local verification scripts must stay inspectable");
        assertEquals(true, middlewareScript.contains("RequireCoreApiProbes"));
        assertEquals(true, middlewareScript.contains("/api/v1/posts?size=3"));
        assertEquals(true, middlewareScript.contains("/api/v1/search/posts?q=Java&size=3"));
        assertEquals(true, middlewareScript.contains("/api/v1/tags"));
        assertEquals(true, middlewareScript.contains("/api/v1/topics?featured=true&limit=6"));
        assertEquals(true, middlewareScript.contains("/api/v1/search/status"));
        assertEquals(true, middlewareScript.contains("/api/v1/health/readiness/strict"));
        assertEquals(true, middlewareScript.contains("returned non-zero business code"));
        assertEquals(true, middlewareScript.contains("*.checkpoint.deleted"));
        assertEquals(true, middlewareScript.contains("IsReadOnly"));
        assertEquals(true, middlewareScript.contains("AccessDeniedException"));
        assertEquals(true, middlewareScript.contains("ConvertFrom-JsonResponse"));
        assertEquals(true, middlewareScript.contains("RawContentStream"));
        assertEquals(true, middlewareScript.contains("UTF8Encoding"));
        assertEquals(true, middlewareScript.contains("offerlab-server-local.properties"));
        assertEquals(true, middlewareScript.contains("start-local-redis.ps1"));
        assertEquals(true, middlewareScript.contains("start-local-elasticsearch.ps1"));
        assertEquals(true, middlewareScript.contains("start-local-kafka.ps1"));
        assertFalse(middlewareScript.contains(".Content | ConvertFrom-Json"), "local middleware JSON probes must decode response streams as UTF-8");
        assertFalse(middlewareScript.contains("Remove-Item"), "local middleware preflight must not delete Kafka files");
        assertFalse(middlewareScript.contains("attrib -"), "local middleware preflight must not change Kafka file attributes");
        assertEquals(true, verifyScript.contains("-RequireCoreApiProbes"));
        assertEquals(true, verifyScript.contains("check-schema-readiness.mjs"));

        assertEquals(true, kafkaLocalProfile.contains("log.dirs=C:/codeware/kafka-data/offerlab-kraft-combined-logs"));
        assertEquals(true, kafkaLocalProfile.contains("metadata.log.dir=C:/codeware/kafka-data/offerlab-metadata"));
        assertEquals(true, kafkaLocalProfile.contains("log.retention.ms=-1"));
        assertEquals(true, kafkaLocalProfile.contains("log.retention.bytes=-1"));
        assertEquals(true, kafkaStartupScript.contains("kafka-server-start.bat"));
        assertEquals(true, kafkaStartupScript.contains("offerlab-server-local.properties"));
        assertEquals(true, kafkaStartupScript.contains("9092"));
        assertEquals(true, kafkaStartupScript.contains("9093"));
        assertEquals(true, kafkaStartupScript.contains("*.checkpoint.deleted"));
        assertFalse(kafkaStartupScript.contains("Remove-Item"), "Kafka startup script must not delete broker data");
        assertFalse(kafkaStartupScript.contains("attrib -"), "Kafka startup script must not change file attributes");
        assertEquals(true, redisStartupScript.contains("redis-server.exe"));
        assertEquals(true, redisStartupScript.contains("redis.conf"));
        assertEquals(true, esStartupScript.contains("CLASSPATH"));
        assertEquals(true, esStartupScript.contains("JAVA_TOOL_OPTIONS"));
        assertEquals(true, esStartupScript.contains("finally"));
        assertEquals(true, logback.contains("org.apache.kafka.clients.admin.AdminClientConfig"));
        assertEquals(true, logback.contains("org.apache.kafka.clients.producer.ProducerConfig"));
        assertEquals(true, logback.contains("org.apache.kafka.clients.NetworkClient"));
        assertEquals(true, logback.contains("level=\"ERROR\""));
    }

    @Test
    void runbooksMustDocumentSchemaAndKafkaPreflightBoundaries() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).getParent();
        String runbook = Files.readString(root.resolve("docs/middleware-local-runbook.md"), StandardCharsets.UTF_8);
        String ciGates = Files.readString(root.resolve("docs/ci-quality-gates.md"), StandardCharsets.UTF_8);

        assertEquals(true, runbook.contains("check-schema-readiness.mjs --json"));
        assertEquals(true, runbook.contains("20260608_community_topics.sql"));
        assertEquals(true, runbook.contains("20260608_review_queue.sql"));
        assertEquals(true, runbook.contains("20260608_tag_governance.sql"));
        assertEquals(true, runbook.contains("20260605_ai_extract_task_metrics.sql"));
        assertEquals(true, runbook.contains("20260608_mock_interview_ai_review_transparency.sql"));
        assertEquals(true, runbook.contains("BLOCKED_BY_SCHEMA"));
        assertEquals(true, runbook.contains("*.checkpoint.deleted"));
        assertEquals(true, runbook.contains("AccessDeniedException"));
        assertEquals(true, runbook.contains("explicit confirmation"));
        assertEquals(true, runbook.contains("start-local-redis.ps1"));
        assertEquals(true, runbook.contains("start-local-elasticsearch.ps1"));
        assertEquals(true, runbook.contains("start-local-kafka.ps1"));
        assertEquals(true, runbook.contains("offerlab-server-local.properties"));
        assertEquals(true, runbook.contains("log.retention.ms=-1"));
        assertEquals(true, runbook.contains("CLASSPATH"));
        assertEquals(true, runbook.contains("JAVA_TOOL_OPTIONS"));
        assertFalse(runbook.contains("Remove-Item"), "runbook must not recommend deleting Kafka files");
        assertFalse(runbook.contains("git clean"), "runbook must not recommend destructive cleanup");

        assertEquals(true, ciGates.contains("check-migration-safety.ps1"));
        assertEquals(true, ciGates.contains("check-schema-readiness.mjs --json"));
        assertEquals(true, ciGates.contains("verify-local.ps1 -StrictMiddleware"));
        assertEquals(true, ciGates.contains("*.checkpoint.deleted"));
    }

    private HealthController controller() {
        return new HealthController(
                dataSource,
                redis,
                elasticsearch,
                outboxMessageMapper,
                searchIndexRetryService,
                questionIndexRetryService,
                notificationRetryService,
                migrationCheckService,
                applicationContext
        );
    }

    private Map<String, Object> detailedReadiness() {
        Map<String, Object> body = controller().strictReadiness().getBody();
        assertNotNull(body);
        return body;
    }

    private static Map<String, Object> retryDown(String message) {
        return Map.of(
                "status", "DOWN",
                "available", false,
                "message", message,
                "byStatus", Map.of("pending", 0L, "done", 0L, "failed", 0L, "running", 0L),
                "duePending", 0L
        );
    }
}

package com.offerlab.community;

import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.notification.application.NotificationRetryService;
import com.offerlab.community.question.application.QuestionIndexRetryService;
import com.offerlab.community.search.application.PostSearchIndexer;
import com.offerlab.community.search.application.SearchIndexRetryService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final DataSource dataSource;
    private final StringRedisTemplate redis;
    private final ElasticsearchHttpClient elasticsearch;
    private final OutboxMessageMapper outboxMessageMapper;
    private final PostSearchIndexer postSearchIndexer;
    private final SearchIndexRetryService searchIndexRetryService;
    private final QuestionIndexRetryService questionIndexRetryService;
    private final NotificationRetryService notificationRetryService;
    private final MigrationCheckService migrationCheckService;
    private final ApplicationContext applicationContext;

    public HealthController(DataSource dataSource,
                            StringRedisTemplate redis,
                            ElasticsearchHttpClient elasticsearch,
                            OutboxMessageMapper outboxMessageMapper,
                            PostSearchIndexer postSearchIndexer,
                            SearchIndexRetryService searchIndexRetryService,
                            QuestionIndexRetryService questionIndexRetryService,
                            NotificationRetryService notificationRetryService,
                            MigrationCheckService migrationCheckService,
                            ApplicationContext applicationContext) {
        this.dataSource = dataSource;
        this.redis = redis;
        this.elasticsearch = elasticsearch;
        this.outboxMessageMapper = outboxMessageMapper;
        this.postSearchIndexer = postSearchIndexer;
        this.searchIndexRetryService = searchIndexRetryService;
        this.questionIndexRetryService = questionIndexRetryService;
        this.notificationRetryService = notificationRetryService;
        this.migrationCheckService = migrationCheckService;
        this.applicationContext = applicationContext;
    }

    @PublicApi
    @GetMapping(value = "/liveness", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> liveness() {
        return Map.of("status", "UP");
    }

    @PublicApi
    @GetMapping(value = "/readiness", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> snapshot = readinessSnapshot();
        boolean ready = Boolean.TRUE.equals(snapshot.get("serviceReady"));
        Map<String, Object> publicSnapshot = Map.of(
                "status", snapshot.get("status"),
                "ready", ready
        );
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(publicSnapshot);
    }

    @GetMapping(value = "/readiness/strict", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> strictReadiness() {
        requireOpsScope();
        Map<String, Object> snapshot = readinessSnapshot();
        boolean ready = Boolean.TRUE.equals(snapshot.get("releaseReady"));
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(snapshot);
    }

    @GetMapping(value = "/operator-health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> operatorHealth() {
        requireOpsScope();
        return readinessSnapshot();
    }

    private Map<String, Object> readinessSnapshot() {
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("db", dbHealth());
        components.put("redis", redisHealth());
        components.put("kafka", kafkaHealth());
        components.put("elasticsearch", elasticsearchHealth());
        components.put("search", postSearchHealth());
        components.put("schema", schemaHealth());
        components.put("outbox", outboxHealth());
        components.put("searchIndexRetry", searchIndexRetryService.status());
        components.put("questionIndexRetry", questionIndexRetryService.status());
        components.put("notificationRetry", notificationRetryService.status());
        boolean ready = components.values().stream().allMatch(this::readyComponent);
        boolean operationalAttentionRequired = components.values().stream().anyMatch(this::attentionRequiredComponent);
        boolean coreDependencyAttentionRequired = coreDependencyAttentionRequired(components);
        Map<String, Object> releaseGates = releaseGates(components);
        boolean releaseReady = ready
                && !operationalAttentionRequired
                && releaseGates.values().stream().allMatch(this::readyReleaseGate);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", ready ? "UP" : "DEGRADED");
        snapshot.put("serviceReady", ready);
        snapshot.put("releaseReady", releaseReady);
        snapshot.put("releaseGates", releaseGates);
        snapshot.put("components", components);
        snapshot.put("operationalAttentionRequired", operationalAttentionRequired);
        snapshot.put("coreDependencyAttentionRequired", coreDependencyAttentionRequired);
        if (operationalAttentionRequired) {
            snapshot.put("message", coreDependencyAttentionRequired
                    ? "Core readiness is degraded; check dependency components before demo or smoke testing."
                    : "Core dependencies are ready; operator queues still need attention.");
        } else if (ready && !releaseReady) {
            snapshot.put("message", "Service is ready in degraded mode, but release acceptance dependencies are incomplete.");
        }
        return snapshot;
    }

    private void requireOpsScope() {
        Long uid = UserContext.get();
        if (uid == null) {
            // Direct unit calls do not run through AuthInterceptor. HTTP requests always have a uid here.
            return;
        }
        applicationContext.getBean(AdminPermissionService.class)
                .requireScope(uid, AdminPermissionService.ROLE_OPS);
    }

    private Map<String, Object> dbHealth() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2)
                    ? Map.of("status", "UP")
                    : coreDependencyIssue("DOWN", "DB_CONNECTION_INVALID", "Database connection is not valid");
        } catch (Exception e) {
            return coreDependencyIssue("DOWN", "DB_UNREACHABLE", nonBlankMessage(e, "Database is not reachable"));
        }
    }

    private Map<String, Object> redisHealth() {
        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return Map.of("status", "UP", "ping", String.valueOf(pong));
            }
            Map<String, Object> status = coreDependencyIssue("DOWN", "REDIS_PING_FAILED", "Redis ping did not return PONG");
            status.put("ping", String.valueOf(pong));
            return status;
        } catch (Exception e) {
            return coreDependencyIssue("DOWN", "REDIS_UNREACHABLE", nonBlankMessage(e, "Redis is not reachable"));
        }
    }

    private Map<String, Object> kafkaHealth() {
        boolean enabled = applicationContext.getEnvironment().getProperty("offerlab.kafka.enabled", Boolean.class, true);
        String bootstrapServers = applicationContext.getEnvironment().getProperty("spring.kafka.bootstrap-servers", "");
        if (!enabled) {
            return Map.of(
                    "status", "DISABLED_BY_CONFIG",
                    "enabled", false,
                    "configured", false,
                    "reachable", false,
                    "mode", "disabled_by_config",
                    "code", "KAFKA_DISABLED_BY_CONFIG",
                    "message", "Kafka 已被配置禁用，事件、通知和索引异步链路处于降级状态"
            );
        }
        if (!StringUtils.hasText(bootstrapServers)) {
            return Map.of(
                    "status", "DEGRADED",
                    "enabled", true,
                    "configured", false,
                    "reachable", false,
                    "available", false,
                    "coreDependency", true,
                    "attentionRequired", true,
                    "code", "KAFKA_BOOTSTRAP_NOT_CONFIGURED",
                    "message", "Kafka bootstrap servers are not configured",
                    "action", "Start local Kafka or disable Kafka explicitly for a degraded demo."
            );
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 1500);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 2000);
        try {
            try (AdminClient adminClient = AdminClient.create(props)) {
                int brokerCount = adminClient.describeCluster().nodes().get(2, TimeUnit.SECONDS).size();
                return Map.of(
                        "status", brokerCount > 0 ? "UP" : "DEGRADED",
                    "enabled", true,
                    "configured", true,
                    "reachable", brokerCount > 0,
                    "available", brokerCount > 0,
                    "coreDependency", true,
                    "attentionRequired", brokerCount <= 0,
                    "code", brokerCount > 0 ? "KAFKA_UP" : "KAFKA_NO_BROKERS",
                    "brokerCount", brokerCount
                );
            }
        } catch (Exception e) {
            return Map.of(
                    "status", "DEGRADED",
                    "enabled", true,
                    "configured", true,
                    "reachable", false,
                    "available", false,
                    "coreDependency", true,
                    "attentionRequired", true,
                    "code", "KAFKA_UNREACHABLE",
                    "message", nonBlankMessage(e, "Kafka broker is not reachable"),
                    "action", "Start local Kafka with scripts/start-local-kafka.ps1 or disable Kafka explicitly for a degraded demo."
            );
        }
    }

    private Map<String, Object> elasticsearchHealth() {
        boolean enabled = elasticsearch.enabled();
        boolean available = elasticsearch.available();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", !enabled ? "DISABLED_BY_CONFIG" : available ? "UP" : "DEGRADED");
        status.put("enabled", enabled);
        status.put("available", available);
        status.put("reachable", available);
        status.put("mode", enabled ? "enabled" : "disabled_by_config");
        status.put("code", !enabled ? "ELASTICSEARCH_DISABLED_BY_CONFIG" : available ? "ELASTICSEARCH_UP" : "ELASTICSEARCH_UNREACHABLE");
        status.put("message", enabled
                ? available ? "Elasticsearch is available" : "Elasticsearch is enabled but not reachable"
                : "Elasticsearch is disabled by config; search uses database fallback.");
        if (enabled && !available) {
            status.put("coreDependency", true);
            status.put("attentionRequired", true);
            status.put("action", "Start local Elasticsearch with scripts/start-local-elasticsearch.ps1 or disable Elasticsearch explicitly for fallback-only demos.");
        }
        return status;
    }

    private Map<String, Object> postSearchHealth() {
        try {
            Map<String, Object> status = postSearchIndexer.status();
            return status == null ? postSearchStatusFailure("Post search readiness returned no status") : status;
        } catch (Exception e) {
            return postSearchStatusFailure(nonBlankMessage(e, "Post search readiness check failed"));
        }
    }

    private Map<String, Object> postSearchStatusFailure(String message) {
        return Map.of(
                "status", "DOWN",
                "publicSearchAvailable", false,
                "attentionRequired", true,
                "code", "POST_SEARCH_STATUS_CHECK_FAILED",
                "message", message
        );
    }

    private Map<String, Object> schemaHealth() {
        try {
            return migrationCheckService.governanceStatus();
        } catch (Exception e) {
            return Map.of(
                    "status", "DOWN",
                    "ready", false,
                    "code", "SCHEMA_GOVERNANCE_CHECK_FAILED",
                    "message", nonBlankMessage(e, "数据库迁移状态检查失败")
            );
        }
    }

    private Map<String, Object> outboxHealth() {
        try {
            Map<String, Long> byStatus = outboxCounts();
            long duePending = outboxMessageMapper.countDuePending();
            long failed = byStatus.getOrDefault("failed", 0L);
            boolean attentionRequired = failed > 0 || duePending > 0;
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("status", attentionRequired ? "DEGRADED" : "UP");
            status.put("available", true);
            status.put("byStatus", byStatus);
            status.put("duePending", duePending);
            status.put("attentionRequired", attentionRequired);
            if (attentionRequired) {
                status.put("message", "Outbox has failed messages or due pending messages");
                status.put("action", "Review Outbox in Ops and retry failed messages after Kafka is healthy.");
            }
            return status;
        } catch (Exception e) {
            return Map.of(
                    "status", "DOWN",
                    "available", false,
                    "attentionRequired", true,
                    "message", shortMessage(e)
            );
        }
    }

    private Map<String, Long> outboxCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("pending", 0L);
        counts.put("sent", 0L);
        counts.put("failed", 0L);
        counts.put("sending", 0L);
        for (Map<String, Object> row : outboxMessageMapper.countByStatus()) {
            counts.put(outboxStatusName(row.get("status")), asLong(row.get("count")));
        }
        return counts;
    }

    @SuppressWarnings("unchecked")
    private boolean readyComponent(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return true;
        }
        if (map.containsKey("publicSearchAvailable")) {
            return Boolean.TRUE.equals(map.get("publicSearchAvailable"));
        }
        Object status = map.get("status");
        if ("UP".equals(status) || "DISABLED_BY_CONFIG".equals(status)) {
            return true;
        }
        if ("DEGRADED".equals(status)) {
            return Boolean.TRUE.equals(map.get("available")) && Boolean.TRUE.equals(map.get("attentionRequired"));
        }
        return false;
    }

    private Map<String, Object> releaseGates(Map<String, Object> components) {
        Object kafka = components.get("kafka");
        boolean kafkaReady = "UP".equals(componentValue(kafka, "status"))
                && Boolean.TRUE.equals(componentValue(kafka, "enabled"))
                && Boolean.TRUE.equals(componentValue(kafka, "reachable"));
        Map<String, Object> kafkaGate = new LinkedHashMap<>();
        kafkaGate.put("ready", kafkaReady);
        kafkaGate.put("status", componentValue(kafka, "status"));
        kafkaGate.put("code", componentValue(kafka, "code"));

        Object elasticsearch = components.get("elasticsearch");
        Object search = components.get("search");
        String rebuildStatus = String.valueOf(componentValue(search, "rebuildStatus"));
        boolean rebuildBlocksElasticsearch = Boolean.TRUE.equals(componentValue(search, "rebuildBlocksElasticsearch"))
                || "PENDING".equals(rebuildStatus)
                || "RUNNING".equals(rebuildStatus)
                || "FAILED".equals(rebuildStatus);
        boolean elasticsearchReady = "UP".equals(componentValue(elasticsearch, "status"))
                && Boolean.TRUE.equals(componentValue(elasticsearch, "enabled"))
                && Boolean.TRUE.equals(componentValue(elasticsearch, "available"))
                && "UP".equals(componentValue(search, "status"))
                && Boolean.TRUE.equals(componentValue(search, "enabled"))
                && Boolean.TRUE.equals(componentValue(search, "available"))
                && Boolean.TRUE.equals(componentValue(search, "indexReady"))
                && !rebuildBlocksElasticsearch;
        Map<String, Object> elasticsearchGate = new LinkedHashMap<>();
        elasticsearchGate.put("ready", elasticsearchReady);
        elasticsearchGate.put("status", componentValue(elasticsearch, "status"));
        elasticsearchGate.put("code", componentValue(elasticsearch, "code"));
        elasticsearchGate.put("searchStatus", componentValue(search, "status"));
        elasticsearchGate.put("rebuildStatus", componentValue(search, "rebuildStatus"));
        elasticsearchGate.put("rebuildBlocksElasticsearch", rebuildBlocksElasticsearch);

        Map<String, Object> gates = new LinkedHashMap<>();
        gates.put("kafka", kafkaGate);
        gates.put("elasticsearch", elasticsearchGate);
        return gates;
    }

    private boolean readyReleaseGate(Object value) {
        return value instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("ready"));
    }

    private Object componentValue(Object component, String name) {
        return component instanceof Map<?, ?> map ? map.get(name) : null;
    }

    private boolean attentionRequiredComponent(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return false;
        }
        return Boolean.TRUE.equals(map.get("attentionRequired"));
    }

    private boolean coreDependencyAttentionRequired(Map<String, Object> components) {
        for (String name : new String[]{"db", "redis", "kafka", "elasticsearch"}) {
            Object value = components.get(name);
            if (value instanceof Map<?, ?> map
                    && Boolean.TRUE.equals(map.get("coreDependency"))
                    && Boolean.TRUE.equals(map.get("attentionRequired"))) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> coreDependencyIssue(String status, String code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("available", false);
        result.put("coreDependency", true);
        result.put("attentionRequired", true);
        result.put("code", code);
        result.put("message", message);
        return result;
    }

    private String nonBlankMessage(Throwable cause, String fallback) {
        String message = shortMessage(cause);
        return StringUtils.hasText(message) ? message : fallback;
    }

    private static String outboxStatusName(Object status) {
        int value = status instanceof Number number ? number.intValue() : -1;
        return switch (value) {
            case OutboxMessageMapper.STATUS_PENDING -> "pending";
            case OutboxMessageMapper.STATUS_SENT -> "sent";
            case OutboxMessageMapper.STATUS_FAILED -> "failed";
            case OutboxMessageMapper.STATUS_SENDING -> "sending";
            default -> "unknown";
        };
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String shortMessage(Throwable cause) {
        if (cause == null || cause.getMessage() == null) {
            return null;
        }
        String message = cause.getMessage();
        return message.length() <= 300 ? message : message.substring(0, 300);
    }
}

package com.offerlab.community;

import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.notification.application.NotificationRetryService;
import com.offerlab.community.question.application.QuestionIndexRetryService;
import com.offerlab.community.search.application.SearchIndexRetryService;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final DataSource dataSource;
    private final StringRedisTemplate redis;
    private final ElasticsearchHttpClient elasticsearch;
    private final OutboxMessageMapper outboxMessageMapper;
    private final SearchIndexRetryService searchIndexRetryService;
    private final QuestionIndexRetryService questionIndexRetryService;
    private final NotificationRetryService notificationRetryService;
    private final ApplicationContext applicationContext;

    public HealthController(DataSource dataSource,
                            StringRedisTemplate redis,
                            ElasticsearchHttpClient elasticsearch,
                            OutboxMessageMapper outboxMessageMapper,
                            SearchIndexRetryService searchIndexRetryService,
                            QuestionIndexRetryService questionIndexRetryService,
                            NotificationRetryService notificationRetryService,
                            ApplicationContext applicationContext) {
        this.dataSource = dataSource;
        this.redis = redis;
        this.elasticsearch = elasticsearch;
        this.outboxMessageMapper = outboxMessageMapper;
        this.searchIndexRetryService = searchIndexRetryService;
        this.questionIndexRetryService = questionIndexRetryService;
        this.notificationRetryService = notificationRetryService;
        this.applicationContext = applicationContext;
    }

    @GetMapping("/liveness")
    public Map<String, Object> liveness() {
        return Map.of("status", "UP");
    }

    @GetMapping("/readiness")
    public Map<String, Object> readiness() {
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("db", dbHealth());
        components.put("redis", redisHealth());
        components.put("kafka", kafkaHealth());
        components.put("elasticsearch", elasticsearchHealth());
        components.put("outbox", outboxHealth());
        components.put("searchIndexRetry", searchIndexRetryService.status());
        components.put("questionIndexRetry", questionIndexRetryService.status());
        components.put("notificationRetry", notificationRetryService.status());
        boolean ready = components.values().stream().allMatch(this::readyComponent);
        return Map.of("status", ready ? "UP" : "DEGRADED", "components", components);
    }

    private Map<String, Object> dbHealth() {
        try (Connection connection = dataSource.getConnection()) {
            return Map.of("status", connection.isValid(2) ? "UP" : "DOWN");
        } catch (Exception e) {
            return Map.of("status", "DOWN", "message", shortMessage(e));
        }
    }

    private Map<String, Object> redisHealth() {
        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            return Map.of("status", "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN", "ping", String.valueOf(pong));
        } catch (Exception e) {
            return Map.of("status", "DOWN", "message", shortMessage(e));
        }
    }

    private Map<String, Object> kafkaHealth() {
        try {
            Class<?> kafkaAdmin = ClassUtils.forName("org.springframework.kafka.core.KafkaAdmin", getClass().getClassLoader());
            String[] names = applicationContext.getBeanNamesForType(kafkaAdmin);
            return Map.of("status", names.length > 0 ? "UP" : "UNKNOWN", "configured", names.length > 0);
        } catch (Exception e) {
            return Map.of("status", "UNKNOWN", "message", shortMessage(e));
        }
    }

    private Map<String, Object> elasticsearchHealth() {
        boolean enabled = elasticsearch.enabled();
        boolean available = elasticsearch.available();
        return Map.of("status", !enabled || available ? "UP" : "DOWN", "enabled", enabled, "available", available);
    }

    private Map<String, Object> outboxHealth() {
        try {
            return Map.of("status", "UP", "duePending", outboxMessageMapper.countDuePending());
        } catch (Exception e) {
            return Map.of("status", "UNKNOWN", "message", shortMessage(e));
        }
    }

    @SuppressWarnings("unchecked")
    private boolean readyComponent(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return true;
        }
        Object status = map.get("status");
        return status == null || "UP".equals(status) || "UNKNOWN".equals(status);
    }

    private String shortMessage(Throwable cause) {
        if (cause == null || cause.getMessage() == null) {
            return null;
        }
        String message = cause.getMessage();
        return message.length() <= 300 ? message : message.substring(0, 300);
    }
}

package com.offerlab.community;

import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.notification.application.NotificationRetryService;
import com.offerlab.community.question.application.QuestionIndexRetryService;
import com.offerlab.community.search.application.SearchIndexRetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        applicationContext = mock(ApplicationContext.class);

        when(dataSource.getConnection()).thenReturn(dbConnection);
        when(dbConnection.isValid(2)).thenReturn(true);
        when(redis.getConnectionFactory()).thenReturn(redisConnectionFactory);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");
        when(outboxMessageMapper.countDuePending()).thenReturn(0L);
        when(searchIndexRetryService.status()).thenReturn(Map.of("status", "UP"));
        when(questionIndexRetryService.status()).thenReturn(Map.of("status", "UP"));
        when(notificationRetryService.status()).thenReturn(Map.of("status", "UP"));
    }

    @Test
    void disabledKafkaAndElasticsearchDoNotDegradeReadiness() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("offerlab.kafka.enabled", "false")
                .withProperty("spring.kafka.bootstrap-servers", "localhost:9092");
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(elasticsearch.enabled()).thenReturn(false);
        when(elasticsearch.available()).thenReturn(false);

        Map<String, Object> readiness = controller().readiness();
        Map<?, ?> components = (Map<?, ?>) readiness.get("components");
        Map<?, ?> kafka = (Map<?, ?>) components.get("kafka");
        Map<?, ?> es = (Map<?, ?>) components.get("elasticsearch");

        assertEquals("UP", readiness.get("status"));
        assertEquals("DISABLED", kafka.get("status"));
        assertEquals(false, kafka.get("enabled"));
        assertEquals(false, kafka.get("reachable"));
        assertEquals("DISABLED", es.get("status"));
        assertEquals(false, es.get("enabled"));
    }

    @Test
    void enabledKafkaWithoutBootstrapServersDegradesReadiness() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("offerlab.kafka.enabled", "true")
                .withProperty("spring.kafka.bootstrap-servers", "");
        when(applicationContext.getEnvironment()).thenReturn(environment);
        when(elasticsearch.enabled()).thenReturn(false);
        when(elasticsearch.available()).thenReturn(false);

        Map<String, Object> readiness = controller().readiness();
        Map<?, ?> components = (Map<?, ?>) readiness.get("components");
        Map<?, ?> kafka = (Map<?, ?>) components.get("kafka");

        assertEquals("DEGRADED", readiness.get("status"));
        assertEquals("DEGRADED", kafka.get("status"));
        assertEquals(true, kafka.get("enabled"));
        assertEquals(false, kafka.get("configured"));
        assertEquals(false, kafka.get("reachable"));
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

        Map<String, Object> readiness = controller().readiness();
        Map<?, ?> components = (Map<?, ?>) readiness.get("components");
        Map<?, ?> outbox = (Map<?, ?>) components.get("outbox");
        Map<?, ?> kafka = (Map<?, ?>) components.get("kafka");
        Map<?, ?> es = (Map<?, ?>) components.get("elasticsearch");

        assertEquals("DEGRADED", readiness.get("status"));
        assertEquals("DOWN", outbox.get("status"));
        assertEquals("DISABLED", kafka.get("status"));
        assertEquals("DISABLED", es.get("status"));
    }

    @Test
    void publicReadinessMustNotExposeKafkaBootstrapServers() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/offerlab/community/HealthController.java"), StandardCharsets.UTF_8);

        assertFalse(source.contains("\"bootstrapServers\""), "public readiness must not expose internal Kafka broker addresses");
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
                applicationContext
        );
    }
}

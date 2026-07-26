package com.offerlab.community;

import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import com.offerlab.community.notification.application.NotificationRetryService;
import com.offerlab.community.question.application.QuestionIndexRetryService;
import com.offerlab.community.search.application.SearchIndexRetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.boot.test.mock.mockito.MockBean;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        classes = CommunityApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.profiles.active=test",
                "spring.main.lazy-initialization=false",
                "spring.task.scheduling.enabled=false",
                "spring.flyway.enabled=false",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379",
                "spring.data.redis.password=",
                "spring.data.redis.database=0",
                "offerlab.jwt.secret=offerlab-context-test-secret-0123456789abcdef-0123456789abcdef",
                "offerlab.id.snowflake.worker-id=1",
                "offerlab.id.snowflake.datacenter-id=1",
                "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration,"
                        + "org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                        + "org.redisson.spring.starter.RedissonAutoConfigurationV2",
                "offerlab.kafka.enabled=false",
                "offerlab.elasticsearch.enabled=false"
        }
)
class CommunityApplicationContextTest {

    @MockBean
    private DataSource dataSource;
    @MockBean
    private RedisConnectionFactory redisConnectionFactory;
    @MockBean(name = "stringRedisTemplate")
    private StringRedisTemplate stringRedisTemplate;
    @MockBean(name = "redisTemplate")
    private RedisTemplate<String, Object> redisTemplate;
    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean
    private ElasticsearchHttpClient elasticsearchHttpClient;
    @MockBean
    private OutboxMessageMapper outboxMessageMapper;
    @MockBean
    private SearchIndexRetryService searchIndexRetryService;
    @MockBean
    private QuestionIndexRetryService questionIndexRetryService;
    @MockBean
    private NotificationRetryService notificationRetryService;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    void applicationContextEagerlyCreatesTheRealBeanGraph() {
        assertNotNull(applicationContext.getBean(HealthController.class));
    }
}

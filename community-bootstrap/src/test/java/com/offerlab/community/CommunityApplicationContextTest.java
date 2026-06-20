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
                "spring.main.lazy-initialization=true",
                "spring.task.scheduling.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration,"
                        + "org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration",
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
    void applicationContextLoadsWithMockedInfrastructure() {
        assertNotNull(applicationContext.getBean(HealthController.class));
    }
}

package com.offerlab.community.infra.redis.lua;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class FeedInboxLuaRedisIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Test
    void feedInboxLuaKeepsNewestPostsAndRefreshesTtl() throws Exception {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setResultType(Long.class);
            script.setScriptText(new ClassPathResource("lua/feed_inbox_add.lua")
                    .getContentAsString(StandardCharsets.UTF_8));

            String key = "feed:inbox:42";
            redis.execute(script, Collections.singletonList(key), "100", "1000", "3", "60");
            redis.execute(script, Collections.singletonList(key), "101", "2000", "3", "60");
            redis.execute(script, Collections.singletonList(key), "102", "3000", "3", "60");
            redis.execute(script, Collections.singletonList(key), "103", "4000", "3", "60");

            Set<String> newest = redis.opsForZSet().reverseRange(key, 0, 9);
            assertEquals(Set.of("103", "102", "101"), newest);
            assertEquals(3L, redis.opsForZSet().zCard(key));
            assertTrue(Boolean.TRUE.equals(redis.hasKey(key)));
            assertTrue(redis.getExpire(key) > 0, "feed inbox lua must set a TTL on each write");
        } finally {
            connectionFactory.destroy();
        }
    }
}

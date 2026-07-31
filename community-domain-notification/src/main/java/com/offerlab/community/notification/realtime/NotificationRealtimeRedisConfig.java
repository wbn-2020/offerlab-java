package com.offerlab.community.notification.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "offerlab.realtime", name = "websocket-enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "offerlab.redis", name = "pubsub-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRealtimeRedisConfig {

    @Bean
    public RedisMessageListenerContainer notificationRealtimeRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            NotificationRealtimeRedisListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(DefaultNotificationRealtimePublisher.REDIS_CHANNEL));
        return container;
    }
}

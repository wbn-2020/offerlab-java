package com.offerlab.community.notification.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.utils.LogMask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "offerlab.realtime", name = "websocket-enabled", havingValue = "true")
public class NotificationRealtimeRedisListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final DefaultNotificationRealtimePublisher publisher;

    public NotificationRealtimeRedisListener(ObjectMapper objectMapper,
                                             DefaultNotificationRealtimePublisher publisher) {
        this.objectMapper = objectMapper;
        this.publisher = publisher;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            return;
        }
        try {
            NotificationRealtimeEvent event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    NotificationRealtimeEvent.class);
            publisher.receiveRemote(event);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("invalid realtime redis message ignored: reason={}", LogMask.message(e));
        }
    }
}

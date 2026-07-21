package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.post.collaboration.api.CollaborationNeedStateChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "offerlab.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class CollaborationNeedNotificationConsumer {

    private static final String EVENT_TYPE = "COLLABORATION_NEED_STATE_CHANGED";

    private final NotificationEventListener notificationEventListener;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "collaboration.need.state-changed",
            groupId = "${offerlab.notification.collaboration-kafka-consumer-group:offerlab-notification-collaboration-need}",
            containerFactory = "kafkaListenerContainerFactory",
            autoStartup = "${offerlab.notification.kafka-consumer-enabled:true}"
    )
    public void onMessage(EventEnvelope<?> envelope, Acknowledgment ack) {
        if (envelope == null) {
            log.warn("collaboration need notification message skipped: empty envelope");
            ack.acknowledge();
            return;
        }
        try {
            String eventType = envelope.getEventType() == null
                    ? ""
                    : envelope.getEventType().trim().toUpperCase(Locale.ROOT);
            if (!EVENT_TYPE.equals(eventType)) {
                throw new IllegalArgumentException(
                        "unsupported collaboration need notification event type: " + eventType);
            }
            CollaborationNeedStateChangedEvent event = objectMapper.convertValue(
                    envelope.getPayload(), CollaborationNeedStateChangedEvent.class);
            notificationEventListener.handleCollaborationNeedStateChangedSynchronously(event);
            ack.acknowledge();
            log.info("collaboration need notification message acked: messageId={} eventId={} needId={}",
                    envelope.getMessageId(), event.getEventId(), event.getNeedId());
        } catch (RuntimeException e) {
            log.error("collaboration need notification message failed, will retry: messageId={} eventType={}",
                    envelope.getMessageId(), envelope.getEventType(), e);
            throw e;
        }
    }
}

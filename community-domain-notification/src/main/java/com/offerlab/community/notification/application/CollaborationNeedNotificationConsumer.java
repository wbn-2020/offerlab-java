package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.infra.mq.idempotent.IdempotentEventConsumer;
import com.offerlab.community.post.collaboration.api.CollaborationNeedStateChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "offerlab.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CollaborationNeedNotificationConsumer {

    private static final String EVENT_TYPE = "COLLABORATION_NEED_STATE_CHANGED";
    private static final String CONSUMER_NAME = "notification-collaboration-need";

    private final NotificationEventListener notificationEventListener;
    private final ObjectMapper objectMapper;
    private final IdempotentEventConsumer idempotentConsumer;

    @Autowired
    public CollaborationNeedNotificationConsumer(
            NotificationEventListener notificationEventListener,
            ObjectMapper objectMapper,
            IdempotentEventConsumer idempotentConsumer) {
        this.notificationEventListener = notificationEventListener;
        this.objectMapper = objectMapper;
        this.idempotentConsumer = idempotentConsumer;
    }

    CollaborationNeedNotificationConsumer(
            NotificationEventListener notificationEventListener,
            ObjectMapper objectMapper) {
        this(notificationEventListener, objectMapper, null);
    }

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
            final CollaborationNeedStateChangedEvent[] converted = new CollaborationNeedStateChangedEvent[1];
            boolean processed = consume(envelope, () -> {
                CollaborationNeedStateChangedEvent event = objectMapper.convertValue(
                        envelope.getPayload(), CollaborationNeedStateChangedEvent.class);
                converted[0] = event;
                notificationEventListener.handleCollaborationNeedStateChangedSynchronously(event);
            });
            ack.acknowledge();
            log.info("collaboration need notification message acked: messageId={} eventId={} needId={}",
                    envelope.getMessageId(),
                    converted[0] == null ? null : converted[0].getEventId(),
                    converted[0] == null ? null : converted[0].getNeedId());
            if (!processed) {
                log.debug("collaboration need notification duplicate skipped: messageId={}",
                        envelope.getMessageId());
            }
        } catch (RuntimeException e) {
            log.error("collaboration need notification message failed, will retry: messageId={} eventType={}",
                    envelope.getMessageId(), envelope.getEventType(), e);
            throw e;
        }
    }

    private boolean consume(EventEnvelope<?> envelope, Runnable handler) {
        if (idempotentConsumer == null) {
            handler.run();
            return true;
        }
        return idempotentConsumer.consume(envelope, CONSUMER_NAME, handler);
    }
}

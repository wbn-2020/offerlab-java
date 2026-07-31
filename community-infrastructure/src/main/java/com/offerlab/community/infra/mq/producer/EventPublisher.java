package com.offerlab.community.infra.mq.producer;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.infra.mq.outbox.OutboxMessage;
import com.offerlab.community.infra.mq.outbox.OutboxMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Publishes domain events to the transactional outbox and the local Spring event bus.
 *
 * <p>When {@code offerlab.kafka.enabled=false}, messages remain pending in the transactional
 * outbox while local Spring consumers keep working. Re-enabling Kafka resumes delivery without
 * losing events produced during the local-only interval.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final ApplicationEventPublisher delegate;
    private final OutboxMessageMapper outboxMapper;
    private final EventTopicResolver topicResolver;
    private final ObjectMapper objectMapper;
    /**
     * Publishes a domain event and always persists an outbox message.
     *
     * @param event event object
     */
    @Transactional
    public void publish(Object event) {
        try {
            persistOutbox(event);

            // 同时发布 Spring 本地事件，保持现有 FeedFanoutListener 工作
            delegate.publishEvent(event);
            log.debug("spring event published: {}", event.getClass().getSimpleName());

        } catch (Exception e) {
            log.error("failed to publish event: eventType={}", safeEventType(event), e);
            throw new RuntimeException("Event publish failed", e);
        }
    }

    private void persistOutbox(Object event) throws Exception {
        EventTopicResolver.TopicMapping mapping = topicResolver.resolve(event);
        String messageId = IdUtil.getSnowflakeNextIdStr();
        EventEnvelope<?> envelope = EventEnvelope.builder()
                .messageId(messageId)
                .eventType(mapping.eventType)
                .timestamp(System.currentTimeMillis())
                .traceId(getTraceId())
                .version("v1")
                .schemaVersion("1")
                .sourceType(mapping.sourceType)
                .sourceId(mapping.sourceId)
                .actorUid(mapping.actorUid)
                .occurredAt(System.currentTimeMillis())
                .visibilityScope("INTERNAL")
                .idempotencyKey(messageId)
                .retryCount(0)
                .payload(event)
                .build();
        String payload = objectMapper.writeValueAsString(envelope);
        LocalDateTime now = LocalDateTime.now();
        OutboxMessage outbox = OutboxMessage.builder()
                .id(IdUtil.getSnowflakeNextId())
                .aggregateType(mapping.topic.split("\\.")[0])
                .aggregateId(mapping.aggregateId)
                .topic(mapping.topic)
                .payload(payload)
                .msgStatus(OutboxMessageMapper.STATUS_PENDING)
                .retryCount(0)
                .createTime(now)
                .updateTime(now)
                .build();

        outboxMapper.insert(outbox);
        log.debug("outbox message saved: topic={} aggregateId={} messageId={} registered={}",
                mapping.topic, mapping.aggregateId, envelope.getMessageId(), mapping.registered);
    }

    private static String safeEventType(Object event) {
        return event == null ? "null" : event.getClass().getSimpleName();
    }

    /**
     * 从 MDC 获取 traceId，如果没有则生成新的
     */
    private String getTraceId() {
        String traceId = org.slf4j.MDC.get("traceId");
        return traceId != null ? traceId : IdUtil.getSnowflakeNextIdStr();
    }
}

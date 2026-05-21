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
 * 事件发布器
 * 第二阶段：Outbox 事务消息模式
 * - 事件先写入 t_outbox_message 表（同业务事务内）
 * - 定时任务扫表投递到 Kafka
 * - 同时保持 Spring 本地事件发布，保证 FeedFanoutListener 继续工作
 *
 * 接口签名保持不变：public void publish(Object event)
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
     * 发布领域事件
     * 1. 写入 Outbox 表（同事务）
     * 2. 发布 Spring 本地事件（保持现有 Feed 扇出工作）
     *
     * @param event 事件对象
     */
    @Transactional
    public void publish(Object event) {
        try {
            // 解析事件类型和 Topic
            EventTopicResolver.TopicMapping mapping = topicResolver.resolve(event);

            // 构建 EventEnvelope
            EventEnvelope<?> envelope = EventEnvelope.builder()
                    .messageId(IdUtil.getSnowflakeNextIdStr())
                    .eventType(mapping.eventType)
                    .timestamp(System.currentTimeMillis())
                    .traceId(getTraceId())
                    .version("v1")
                    .retryCount(0)
                    .payload(event)
                    .build();

            // 序列化为 JSON
            String payload = objectMapper.writeValueAsString(envelope);

            // 写入 Outbox 表
            OutboxMessage outbox = OutboxMessage.builder()
                    .id(IdUtil.getSnowflakeNextId())
                    .aggregateType(mapping.topic.split("\\.")[0])
                    .aggregateId(mapping.aggregateId)
                    .topic(mapping.topic)
                    .payload(payload)
                    .msgStatus(0)  // 待发
                    .retryCount(0)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            outboxMapper.insert(outbox);
            log.debug("outbox message saved: topic={} aggregateId={} messageId={}",
                    mapping.topic, mapping.aggregateId, envelope.getMessageId());

            // 同时发布 Spring 本地事件，保持现有 FeedFanoutListener 工作
            delegate.publishEvent(event);
            log.debug("spring event published: {}", event.getClass().getSimpleName());

        } catch (Exception e) {
            log.error("failed to publish event: {}", event, e);
            throw new RuntimeException("Event publish failed", e);
        }
    }

    /**
     * 从 MDC 获取 traceId，如果没有则生成新的
     */
    private String getTraceId() {
        String traceId = org.slf4j.MDC.get("traceId");
        return traceId != null ? traceId : IdUtil.getSnowflakeNextIdStr();
    }
}

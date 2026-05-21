package com.offerlab.community.infra.mq.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 定时投递调度器
 * 扫描 t_outbox_message 表，将待发消息投递到 Kafka
 * 支持重试和失败处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxMessageMapper outboxMapper;
    private final KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** 最大重试次数 */
    private static final int MAX_RETRY = 5;

    /** 单次扫描数量 */
    private static final int BATCH_SIZE = 100;

    /**
     * 定时扫表投递
     * 每 1 秒执行一次
     */
    @Scheduled(fixedDelay = 1000)
    public void flush() {
        try {
            List<OutboxMessage> pending = outboxMapper.findPending(BATCH_SIZE);
            if (pending.isEmpty()) {
                return;
            }

            log.debug("outbox flush: found {} pending messages", pending.size());

            for (OutboxMessage msg : pending) {
                try {
                    // 反序列化 EventEnvelope
                    EventEnvelope envelope = objectMapper.readValue(msg.getPayload(), EventEnvelope.class);

                    // 构建 Kafka 消息，使用 aggregateId 作为 key 保证顺序
                    Message<EventEnvelope> kafkaMsg = MessageBuilder
                            .withPayload(envelope)
                            .setHeader(KafkaHeaders.TOPIC, msg.getTopic())
                            .setHeader(KafkaHeaders.KEY, msg.getAggregateId().toString())
                            .build();

                    // 发送到 Kafka
                    kafkaTemplate.send(kafkaMsg).get();

                    // 标记已发送
                    outboxMapper.markSent(msg.getId());
                    log.debug("outbox message sent: id={} topic={}", msg.getId(), msg.getTopic());

                } catch (Exception e) {
                    handleSendFailure(msg, e);
                }
            }
        } catch (Exception e) {
            log.error("outbox flush failed", e);
        }
    }

    /**
     * 处理发送失败
     * 重试次数 < MAX_RETRY：更新重试时间，状态保持 0
     * 重试次数 >= MAX_RETRY：标记为失败（状态 2），等待人工处理
     */
    private void handleSendFailure(OutboxMessage msg, Exception e) {
        int newRetryCount = msg.getRetryCount() + 1;

        if (newRetryCount >= MAX_RETRY) {
            // 超过最大重试次数，标记为失败
            outboxMapper.updateRetry(msg.getId(), 2, newRetryCount, null);
            log.error("outbox message failed after {} retries: id={} topic={}", MAX_RETRY, msg.getId(), msg.getTopic(), e);
        } else {
            // 计算下次重试时间：2^retryCount * 30 秒
            long delaySeconds = (long) Math.pow(2, newRetryCount) * 30;
            LocalDateTime nextRetryTime = LocalDateTime.now().plusSeconds(delaySeconds);
            outboxMapper.updateRetry(msg.getId(), 0, newRetryCount, nextRetryTime);
            log.warn("outbox message retry scheduled: id={} topic={} nextRetry={} delaySeconds={}",
                    msg.getId(), msg.getTopic(), nextRetryTime, delaySeconds, e);
        }
    }
}

package com.offerlab.community.infra.mq.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Flushes claimed outbox messages to Kafka.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "offerlab.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxMessageMapper outboxMapper;
    private final KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY = 5;
    private static final int BATCH_SIZE = 100;
    private static final int CLAIM_LEASE_SECONDS = 60;
    private final String owner = buildOwner();

    @Scheduled(fixedDelay = 1000)
    public void flush() {
        try {
            LocalDateTime lockUntil = LocalDateTime.now().plusSeconds(CLAIM_LEASE_SECONDS);
            int claimed = outboxMapper.claimPending(owner, lockUntil, BATCH_SIZE);
            if (claimed <= 0) {
                return;
            }

            List<OutboxMessage> messages = outboxMapper.findClaimed(owner, BATCH_SIZE);
            log.debug("outbox flush: owner={} claimed={} loaded={}", owner, claimed, messages.size());

            for (OutboxMessage msg : messages) {
                try {
                    EventEnvelope envelope = objectMapper.readValue(msg.getPayload(), EventEnvelope.class);
                    Message<EventEnvelope> kafkaMsg = MessageBuilder
                            .withPayload(envelope)
                            .setHeader(KafkaHeaders.TOPIC, msg.getTopic())
                            .setHeader(KafkaHeaders.KEY, String.valueOf(msg.getAggregateId()))
                            .build();

                    kafkaTemplate.send(kafkaMsg).get();

                    int updated = outboxMapper.markSent(msg.getId(), owner);
                    if (updated <= 0) {
                        log.warn("outbox message sent but mark-sent skipped: id={} owner={}", msg.getId(), owner);
                    } else {
                        log.debug("outbox message sent: id={} topic={} owner={}", msg.getId(), msg.getTopic(), owner);
                    }
                } catch (Exception e) {
                    handleSendFailure(msg, e);
                }
            }
        } catch (Exception e) {
            log.error("outbox flush failed: owner={}", owner, e);
        }
    }

    private void handleSendFailure(OutboxMessage msg, Exception e) {
        int newRetryCount = (msg.getRetryCount() == null ? 0 : msg.getRetryCount()) + 1;

        if (newRetryCount >= MAX_RETRY) {
            int updated = outboxMapper.updateRetry(msg.getId(), owner, OutboxMessageMapper.STATUS_FAILED, newRetryCount, null);
            log.error("outbox message failed after {} retries: id={} topic={} owner={} updated={}",
                    MAX_RETRY, msg.getId(), msg.getTopic(), owner, updated, e);
        } else {
            long delaySeconds = (long) Math.pow(2, newRetryCount) * 30;
            LocalDateTime nextRetryTime = LocalDateTime.now().plusSeconds(delaySeconds);
            int updated = outboxMapper.updateRetry(msg.getId(), owner, OutboxMessageMapper.STATUS_PENDING, newRetryCount, nextRetryTime);
            log.warn("outbox message retry scheduled: id={} topic={} owner={} nextRetry={} delaySeconds={} updated={}",
                    msg.getId(), msg.getTopic(), owner, nextRetryTime, delaySeconds, updated, e);
        }
    }

    private static String buildOwner() {
        String host = "unknown";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // best-effort identifier only
        }
        return host + ":" + ManagementFactory.getRuntimeMXBean().getName() + ":" + UUID.randomUUID();
    }
}

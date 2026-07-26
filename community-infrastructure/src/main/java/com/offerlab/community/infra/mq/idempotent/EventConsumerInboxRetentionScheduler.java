package com.offerlab.community.infra.mq.idempotent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumerInboxRetentionScheduler {

    private static final int RETENTION_DAYS = 30;
    private static final int BATCH_SIZE = 1000;
    private static final int MAX_BATCHES = 20;

    private final EventConsumerInboxMapper inboxMapper;

    @Scheduled(cron = "${offerlab.event-inbox.retention-cleanup-cron:0 40 * * * *}")
    public void cleanup() {
        try {
            LocalDateTime before = LocalDateTime.now().minusDays(RETENTION_DAYS);
            int deleted = 0;
            for (int batch = 0; batch < MAX_BATCHES; batch++) {
                int current = inboxMapper.deleteBefore(before, BATCH_SIZE);
                deleted += Math.max(0, current);
                if (current < BATCH_SIZE) {
                    break;
                }
            }
            if (deleted > 0) {
                log.info("event consumer inbox retention cleanup completed: deleted={}", deleted);
            }
        } catch (RuntimeException e) {
            log.warn("event consumer inbox retention cleanup failed", e);
        }
    }
}

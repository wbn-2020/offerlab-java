package com.offerlab.community.post.application;

import com.offerlab.community.post.infrastructure.persistence.mapper.ContentAssistEnhancedRequestMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ContentAssistEnhancedRequestPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class ContentAssistEnhancedRecoveryScheduler {
    private final ContentAssistEnhancedRequestMapper requestMapper;
    private final ContentAssistEnhancedService enhancedService;

    @Value("${offerlab.ai.content-assist.enhanced-request-timeout-seconds:120}")
    private long requestTimeoutSeconds;

    @Value("${offerlab.ai.content-assist.enhanced-cleanup-batch-size:100}")
    private int batchSize;

    @Value("${offerlab.ai.content-assist.enhanced-result-retention-hours:24}")
    private int resultRetentionHours;

    @Scheduled(fixedDelayString = "${offerlab.ai.content-assist.enhanced-recovery-delay-ms:10000}")
    public void recoverStaleRequestsAndClearExpiredResults() {
        int limit = Math.max(1, Math.min(batchSize, 1000));
        long timeoutSeconds = enhancedService.effectiveRecoveryTimeoutSeconds(requestTimeoutSeconds);
        for (ContentAssistEnhancedRequestPO request : requestMapper.selectStaleRunning(
                timeoutSeconds, limit)) {
            try {
                enhancedService.recoverStaleRequest(request, timeoutSeconds);
            } catch (RuntimeException ex) {
                log.warn("content assist enhanced request recovery failed: requestId={}", request.getId(), ex);
            }
        }
        enhancedService.clearExpiredResults(resultRetentionHours, limit);
    }
}

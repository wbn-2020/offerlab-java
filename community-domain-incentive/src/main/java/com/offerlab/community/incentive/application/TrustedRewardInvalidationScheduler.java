package com.offerlab.community.incentive.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "offerlab.incentive.invalidation-scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class TrustedRewardInvalidationScheduler {
    private static final int JOBS_PER_RUN = 10;

    private final AccountLedgerService accountLedgerService;
    private final String leaseOwner = "invalidation-" + UUID.randomUUID();

    @Scheduled(
            fixedDelayString = "${offerlab.incentive.invalidation-scheduler.delay-ms:5000}",
            initialDelayString = "${offerlab.incentive.invalidation-scheduler.initial-delay-ms:15000}")
    public void drainPendingJobs() {
        List<Long> jobIds;
        try {
            jobIds = accountLedgerService.claimPendingInvalidationJobs(JOBS_PER_RUN, leaseOwner);
        } catch (RuntimeException e) {
            log.error("failed to claim trusted reward invalidation jobs", e);
            return;
        }
        for (Long jobId : jobIds) {
            try {
                accountLedgerService.processClaimedInvalidationJob(jobId, leaseOwner);
            } catch (RuntimeException e) {
                log.error("trusted reward invalidation job failed: jobId={}", jobId, e);
                try {
                    accountLedgerService.releaseInvalidationJobAfterFailure(
                            jobId, leaseOwner, e.getMessage());
                } catch (RuntimeException releaseError) {
                    log.error("failed to release trusted reward invalidation job lease: jobId={}",
                            jobId, releaseError);
                }
            }
        }
    }
}

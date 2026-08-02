package com.offerlab.community.incentive.application;

import com.offerlab.community.incentive.api.quota.EntitlementReleaseCmd;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BenefitEntitlementUsagePO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EntitlementReservationRecoveryScheduler {
    private final EntitlementQuotaService entitlementQuotaService;

    @Value("${offerlab.incentive.entitlement-reservation.enabled:true}")
    private boolean enabled;

    @Value("${offerlab.incentive.entitlement-reservation.recovery-batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${offerlab.incentive.entitlement-reservation.recovery-delay-ms:5000}")
    public void releaseExpiredReservations() {
        if (!enabled) {
            return;
        }
        for (BenefitEntitlementUsagePO usage : entitlementQuotaService.expiredReservations(batchSize)) {
            try {
                entitlementQuotaService.release(new EntitlementReleaseCmd(
                        usage.getUserId(), usage.getId(), usage.getConsumerCode(), "RESERVATION_EXPIRED"));
            } catch (RuntimeException ex) {
                log.warn("entitlement reservation recovery failed: usageId={}, consumer={}",
                        usage.getId(), usage.getConsumerCode(), ex);
            }
        }
    }
}

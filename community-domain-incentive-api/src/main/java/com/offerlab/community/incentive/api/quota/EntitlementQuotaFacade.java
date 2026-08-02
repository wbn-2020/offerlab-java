package com.offerlab.community.incentive.api.quota;

public interface EntitlementQuotaFacade {
    EntitlementCapabilityDTO capability(Long uid, String benefitCode, String consumerCode);

    EntitlementReservationDTO reserve(EntitlementReservationCmd command);

    EntitlementUsageDTO confirm(EntitlementConfirmCmd command);

    EntitlementUsageDTO release(EntitlementReleaseCmd command);

    EntitlementUsageDTO findByRequest(Long uid, String consumerCode, String idempotencyKey);
}

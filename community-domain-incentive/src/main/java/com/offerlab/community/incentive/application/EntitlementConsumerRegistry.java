package com.offerlab.community.incentive.application;

import com.offerlab.community.incentive.api.quota.BenefitCodes;
import com.offerlab.community.incentive.api.quota.EntitlementConsumerDescriptor;
import com.offerlab.community.incentive.api.quota.EntitlementConsumerRuntimeAvailability;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EntitlementConsumerRegistry {
    private final List<EntitlementConsumerRuntimeAvailability> runtimeAvailability;

    @Value("${offerlab.incentive.ai-assist-entitlement-enabled:false}")
    private boolean aiAssistEntitlementEnabled;

    @Value("${offerlab.ai.content-assist.enhanced-enabled:false}")
    private boolean enhancedAssistEnabled;

    public Optional<EntitlementConsumerDescriptor> find(String benefitCode, String consumerCode) {
        if (!BenefitCodes.AI_ASSIST_QUOTA.equals(benefitCode)
                || !BenefitCodes.CONTENT_ASSIST_ENHANCED.equals(consumerCode)) {
            return Optional.empty();
        }
        EntitlementConsumerRuntimeAvailability runtime = runtimeAvailability.stream()
                .filter(item -> benefitCode.equals(item.benefitCode()) && consumerCode.equals(item.consumerCode()))
                .findFirst()
                .orElse(null);
        boolean runtimeAvailable = aiAssistEntitlementEnabled && enhancedAssistEnabled
                && runtime != null && runtime.available();
        String unavailableReason = !aiAssistEntitlementEnabled || !enhancedAssistEnabled
                ? "AI_ASSIST_DISABLED"
                : runtime == null ? "ENTITLEMENT_CONSUMER_UNAVAILABLE" : runtime.unavailableReason();
        return Optional.of(new EntitlementConsumerDescriptor(
                benefitCode,
                consumerCode,
                "AI 创作增强",
                "/editor",
                true,
                runtimeAvailable,
                unavailableReason
        ));
    }

    public boolean hasInstalledConsumer(String benefitCode) {
        return BenefitCodes.AI_ASSIST_QUOTA.equals(benefitCode);
    }

    public boolean isRuntimeAvailable(String benefitCode) {
        return find(benefitCode, BenefitCodes.CONTENT_ASSIST_ENHANCED)
                .map(EntitlementConsumerDescriptor::runtimeAvailable)
                .orElse(false);
    }
}

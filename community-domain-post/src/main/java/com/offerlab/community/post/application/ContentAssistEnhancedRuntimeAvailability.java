package com.offerlab.community.post.application;

import com.offerlab.community.incentive.api.quota.BenefitCodes;
import com.offerlab.community.incentive.api.quota.EntitlementConsumerRuntimeAvailability;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ContentAssistEnhancedRuntimeAvailability implements EntitlementConsumerRuntimeAvailability {
    private final ContentAssistAiClient aiClient;

    @Value("${offerlab.ai.content-assist.enhanced-enabled:false}")
    private boolean enhancedEnabled;

    @Override
    public String benefitCode() {
        return BenefitCodes.AI_ASSIST_QUOTA;
    }

    @Override
    public String consumerCode() {
        return BenefitCodes.CONTENT_ASSIST_ENHANCED;
    }

    @Override
    public boolean available() {
        return enhancedEnabled && aiClient.enabled() && aiClient.configured();
    }

    @Override
    public String unavailableReason() {
        return enhancedEnabled && aiClient.enabled() && aiClient.configured()
                ? null
                : "AI_ASSIST_NOT_CONFIGURED";
    }
}

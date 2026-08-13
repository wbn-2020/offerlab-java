package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.incentive.api.quota.EntitlementConfirmCmd;
import com.offerlab.community.incentive.api.quota.EntitlementQuotaFacade;
import com.offerlab.community.incentive.api.quota.EntitlementReleaseCmd;
import com.offerlab.community.incentive.api.quota.EntitlementUsageDTO;
import com.offerlab.community.incentive.api.quota.EntitlementUsageStatus;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedResultDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentAssistEnhancedRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
class ContentAssistEnhancedFinalizer {
    private final EntitlementQuotaFacade entitlementQuotaFacade;
    private final ContentAssistEnhancedRequestMapper requestMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public Finalization finalizeSuccess(Long uid, Long requestId, Long usageId, String consumerCode,
                                       ContentAssistEnhancedResultDTO successResult, String provider,
                                       int promptTokens, int completionTokens, long estimatedCostMicros,
                                       Supplier<ContentAssistEnhancedResultDTO> expiredFallback) {
        requireRunningRequest(uid, requestId);
        EntitlementUsageDTO usage = entitlementQuotaFacade.confirm(
                new EntitlementConfirmCmd(uid, usageId, consumerCode));
        if (usage.status() == EntitlementUsageStatus.CONFIRMED) {
            successResult.setRequestStatus("SUCCEEDED");
            successResult.setUsageStatus(usage.status().name());
            successResult.setQuotaConsumed(true);
            if (requestMapper.complete(requestId, uid, "SUCCEEDED", provider, serialize(successResult),
                    promptTokens, completionTokens, estimatedCostMicros, null) != 1) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_CONFIRM_FAILED");
            }
            return new Finalization(usage, successResult);
        }
        ContentAssistEnhancedResultDTO fallback = expiredFallback.get();
        fallback.setRequestStatus("FALLBACK");
        fallback.setUsageStatus(usage.status().name());
        fallback.setQuotaConsumed(false);
        if (requestMapper.complete(requestId, uid, "FALLBACK", "rules", serialize(fallback),
                0, 0, 0L, "AI_ASSIST_RESERVATION_EXPIRED") != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_RELEASE_FAILED");
        }
        return new Finalization(usage, fallback);
    }

    @Transactional
    public EntitlementUsageDTO releaseFallback(Long uid, Long requestId, Long usageId, String consumerCode,
                                               ContentAssistEnhancedResultDTO result, String errorCode) {
        requireRunningRequest(uid, requestId);
        EntitlementUsageDTO usage = entitlementQuotaFacade.release(
                new EntitlementReleaseCmd(uid, usageId, consumerCode, errorCode));
        if (usage.status() == EntitlementUsageStatus.CONFIRMED) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_RELEASE_FAILED");
        }
        if (requestMapper.complete(requestId, uid, "FALLBACK", "rules", serialize(result),
                0, 0, 0L, errorCode) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_RELEASE_FAILED");
        }
        return usage;
    }

    @Transactional
    public void failWithoutReservation(Long uid, Long requestId, String errorCode) {
        requestMapper.complete(requestId, uid, "FAILED", null, null, 0, 0, 0L, errorCode);
    }

    @Transactional
    public boolean recoverTimedOutRequest(Long uid, Long requestId, Long usageId, String consumerCode,
                                          long timeoutSeconds) {
        if (requestMapper.markFailedIfRunningAndStale(requestId, uid,
                Math.max(30L, timeoutSeconds), "AI_ASSIST_RECOVERY_TIMEOUT") != 1) {
            return false;
        }
        if (usageId != null) {
            entitlementQuotaFacade.release(new EntitlementReleaseCmd(
                    uid, usageId, consumerCode, "AI_ASSIST_RECOVERY_TIMEOUT"));
        }
        return true;
    }

    private void requireRunningRequest(Long uid, Long requestId) {
        if (requestMapper.lockRunningRequest(requestId, uid) == null) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_REQUEST_UNAVAILABLE");
        }
    }

    private String serialize(ContentAssistEnhancedResultDTO result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                    "enhanced content assist result serialization failed");
        }
    }

    record Finalization(EntitlementUsageDTO usage, ContentAssistEnhancedResultDTO result) {
    }
}

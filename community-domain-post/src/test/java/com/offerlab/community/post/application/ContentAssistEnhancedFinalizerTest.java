package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.incentive.api.quota.BenefitCodes;
import com.offerlab.community.incentive.api.quota.EntitlementQuotaFacade;
import com.offerlab.community.incentive.api.quota.EntitlementUsageDTO;
import com.offerlab.community.incentive.api.quota.EntitlementUsageStatus;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedResultDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentAssistEnhancedRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentAssistEnhancedFinalizerTest {

    @Mock
    private EntitlementQuotaFacade entitlementQuotaFacade;
    @Mock
    private ContentAssistEnhancedRequestMapper requestMapper;

    private ContentAssistEnhancedFinalizer finalizer;

    @BeforeEach
    void setUp() {
        finalizer = new ContentAssistEnhancedFinalizer(
                entitlementQuotaFacade,
                requestMapper,
                new ObjectMapper());
    }

    @Test
    void recoveryReleasesQuotaOnlyAfterItAtomicallyClaimsTheStaleRequest() {
        when(requestMapper.markFailedIfRunningAndStale(7L, 5L, 120L, "AI_ASSIST_RECOVERY_TIMEOUT"))
                .thenReturn(1);
        when(entitlementQuotaFacade.release(any())).thenReturn(releasedUsage());

        assertTrue(finalizer.recoverTimedOutRequest(
                5L, 7L, 11L, BenefitCodes.CONTENT_ASSIST_ENHANCED, 120L));

        InOrder order = inOrder(requestMapper, entitlementQuotaFacade);
        order.verify(requestMapper).markFailedIfRunningAndStale(
                7L, 5L, 120L, "AI_ASSIST_RECOVERY_TIMEOUT");
        order.verify(entitlementQuotaFacade).release(any());
    }

    @Test
    void recoveryDoesNotReleaseQuotaWhenTheRequestWasCompletedByAnotherWorker() {
        when(requestMapper.markFailedIfRunningAndStale(7L, 5L, 120L, "AI_ASSIST_RECOVERY_TIMEOUT"))
                .thenReturn(0);

        assertFalse(finalizer.recoverTimedOutRequest(
                5L, 7L, 11L, BenefitCodes.CONTENT_ASSIST_ENHANCED, 120L));

        verify(entitlementQuotaFacade, never()).release(any());
    }

    @Test
    void releasedReservationIsFinalizedAsFallbackWhileTheRequestLockIsHeld() {
        when(requestMapper.lockRunningRequest(7L, 5L)).thenReturn(7L);
        when(requestMapper.complete(
                eq(7L), eq(5L), eq("FALLBACK"), eq("rules"), anyString(),
                eq(0), eq(0), eq(0L), eq("AI_ASSIST_RESERVATION_EXPIRED")))
                .thenReturn(1);
        when(entitlementQuotaFacade.confirm(any())).thenReturn(releasedUsage());

        ContentAssistEnhancedFinalizer.Finalization finalization = finalizer.finalizeSuccess(
                5L,
                7L,
                11L,
                BenefitCodes.CONTENT_ASSIST_ENHANCED,
                ContentAssistEnhancedResultDTO.builder().requestStatus("SUCCEEDED").build(),
                "deepseek",
                10,
                5,
                100L,
                () -> ContentAssistEnhancedResultDTO.builder()
                        .requestStatus("FALLBACK")
                        .usageStatus("RELEASED")
                        .quotaConsumed(false)
                        .fallbackReason("AI_ASSIST_RESERVATION_EXPIRED")
                        .build());

        InOrder order = inOrder(requestMapper, entitlementQuotaFacade);
        order.verify(requestMapper).lockRunningRequest(7L, 5L);
        order.verify(entitlementQuotaFacade).confirm(any());
        order.verify(requestMapper).complete(
                eq(7L), eq(5L), eq("FALLBACK"), eq("rules"), anyString(),
                eq(0), eq(0), eq(0L), eq("AI_ASSIST_RESERVATION_EXPIRED"));
        assertTrue(finalization.usage().status() == EntitlementUsageStatus.RELEASED);
        assertTrue(finalization.result().getRequestStatus().equals("FALLBACK"));
    }

    private static EntitlementUsageDTO releasedUsage() {
        return new EntitlementUsageDTO(
                11L,
                12L,
                5L,
                BenefitCodes.AI_ASSIST_QUOTA,
                BenefitCodes.CONTENT_ASSIST_ENHANCED,
                EntitlementUsageStatus.RELEASED,
                1L,
                "content-assist:test",
                "a".repeat(64),
                "AI_ASSIST_RECOVERY_TIMEOUT",
                null,
                null,
                null);
    }
}

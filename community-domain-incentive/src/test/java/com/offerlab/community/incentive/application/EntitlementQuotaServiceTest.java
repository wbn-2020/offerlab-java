package com.offerlab.community.incentive.application;

import com.offerlab.community.incentive.api.quota.BenefitCodes;
import com.offerlab.community.incentive.api.quota.EntitlementConfirmCmd;
import com.offerlab.community.incentive.api.quota.EntitlementQuotaFacade;
import com.offerlab.community.incentive.api.quota.EntitlementReleaseCmd;
import com.offerlab.community.incentive.api.quota.EntitlementReservationCmd;
import com.offerlab.community.incentive.api.quota.EntitlementReservationDTO;
import com.offerlab.community.incentive.api.quota.EntitlementUsageDTO;
import com.offerlab.community.incentive.api.quota.EntitlementUsageStatus;
import com.offerlab.community.incentive.infrastructure.IncentiveMapper;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BenefitEntitlementPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BenefitEntitlementUsagePO;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementQuotaServiceTest {

    private static final long UID = 7L;
    private static final long ENTITLEMENT_ID = 101L;
    private static final long USAGE_ID = 201L;
    private static final String IDEMPOTENCY_KEY = "content-assist:test-key";
    private static final String FINGERPRINT = "a".repeat(64);

    @Mock
    private IncentiveMapper mapper;
    @Mock
    private EntitlementConsumerRegistry consumerRegistry;

    private EntitlementQuotaService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new EntitlementQuotaService(
                mapper,
                new SnowflakeIdGenerator(1, 1),
                consumerRegistry);
        Field field = EntitlementQuotaService.class.getDeclaredField("reservationTtlSeconds");
        field.setAccessible(true);
        field.setLong(service, 120L);
    }

    @Test
    void duplicateReservationInsertReplaysExistingUsageWithoutConsumingQuotaAgain() {
        BenefitEntitlementPO entitlement = entitlement();
        BenefitEntitlementUsagePO existing = usage("RESERVED");
        existing.setExpiresAt(LocalDateTime.now().plusMinutes(2));

        when(consumerRegistry.isRuntimeAvailable(BenefitCodes.AI_ASSIST_QUOTA)).thenReturn(true);
        when(mapper.selectEntitlementUsageByRequest(UID, BenefitCodes.CONTENT_ASSIST_ENHANCED, IDEMPOTENCY_KEY))
                .thenReturn(null);
        when(mapper.lockFirstActiveEntitlement(UID, BenefitCodes.AI_ASSIST_QUOTA, 1L)).thenReturn(entitlement);
        when(mapper.insertEntitlementReservation(
                anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), any(LocalDateTime.class), anyString())).thenReturn(0);
        when(mapper.lockEntitlementUsageByRequest(UID, BenefitCodes.CONTENT_ASSIST_ENHANCED, IDEMPOTENCY_KEY))
                .thenReturn(existing);

        EntitlementReservationDTO result = service.reserve(reservationCommand());

        assertEquals(USAGE_ID, result.usageId());
        assertEquals(EntitlementUsageStatus.RESERVED, result.status());
        verify(mapper, never()).consumeEntitlement(ENTITLEMENT_ID, UID, 1L);
    }

    @Test
    void expiredReservationCannotBeConfirmedAndRestoresQuotaExactlyOnce() {
        BenefitEntitlementUsagePO reserved = usage("RESERVED");
        reserved.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        BenefitEntitlementUsagePO released = usage("RELEASED");
        released.setReleasedAt(LocalDateTime.now());

        when(mapper.lockEntitlementUsage(USAGE_ID)).thenReturn(reserved, released);
        when(mapper.confirmEntitlementUsage(USAGE_ID, UID, BenefitCodes.CONTENT_ASSIST_ENHANCED)).thenReturn(0);
        when(mapper.releaseEntitlementUsage(
                USAGE_ID, UID, BenefitCodes.CONTENT_ASSIST_ENHANCED, "AI_ASSIST_RESERVATION_EXPIRED")).thenReturn(1);
        when(mapper.restoreEntitlementQuota(ENTITLEMENT_ID, UID, 1L)).thenReturn(1);

        EntitlementUsageDTO result = service.confirm(new EntitlementConfirmCmd(
                UID, USAGE_ID, BenefitCodes.CONTENT_ASSIST_ENHANCED));

        assertEquals(EntitlementUsageStatus.RELEASED, result.status());
        verify(mapper).restoreEntitlementQuota(ENTITLEMENT_ID, UID, 1L);
    }

    @Test
    void releasingAnAlreadyReleasedReservationDoesNotRestoreQuotaTwice() {
        BenefitEntitlementUsagePO released = usage("RELEASED");
        released.setReleasedAt(LocalDateTime.now());
        when(mapper.lockEntitlementUsage(USAGE_ID)).thenReturn(released);

        EntitlementUsageDTO result = service.release(new EntitlementReleaseCmd(
                UID, USAGE_ID, BenefitCodes.CONTENT_ASSIST_ENHANCED, "AI_ASSIST_PROVIDER_FAILED"));

        assertEquals(EntitlementUsageStatus.RELEASED, result.status());
        verify(mapper, never()).releaseEntitlementUsage(
                USAGE_ID, UID, BenefitCodes.CONTENT_ASSIST_ENHANCED, "AI_ASSIST_PROVIDER_FAILED");
        verify(mapper, never()).restoreEntitlementQuota(ENTITLEMENT_ID, UID, 1L);
    }

    private static EntitlementReservationCmd reservationCommand() {
        return new EntitlementReservationCmd(
                UID,
                BenefitCodes.AI_ASSIST_QUOTA,
                BenefitCodes.CONTENT_ASSIST_ENHANCED,
                1L,
                IDEMPOTENCY_KEY,
                FINGERPRINT,
                "CONTENT_ASSIST",
                "1001",
                "CONTENT_ASSIST_ENHANCED",
                120L);
    }

    private static BenefitEntitlementPO entitlement() {
        BenefitEntitlementPO entitlement = new BenefitEntitlementPO();
        entitlement.setId(ENTITLEMENT_ID);
        entitlement.setUserId(UID);
        entitlement.setBenefitCode(BenefitCodes.AI_ASSIST_QUOTA);
        entitlement.setQuantityRemaining(2L);
        entitlement.setEntitlementStatus("ACTIVE");
        return entitlement;
    }

    private static BenefitEntitlementUsagePO usage(String status) {
        BenefitEntitlementUsagePO usage = new BenefitEntitlementUsagePO();
        usage.setId(USAGE_ID);
        usage.setEntitlementId(ENTITLEMENT_ID);
        usage.setUserId(UID);
        usage.setBenefitCode(BenefitCodes.AI_ASSIST_QUOTA);
        usage.setConsumerCode(BenefitCodes.CONTENT_ASSIST_ENHANCED);
        usage.setAmount(1L);
        usage.setIdempotencyKey(IDEMPOTENCY_KEY);
        usage.setRequestFingerprint(FINGERPRINT);
        usage.setUsageStatus(status);
        return usage;
    }
}

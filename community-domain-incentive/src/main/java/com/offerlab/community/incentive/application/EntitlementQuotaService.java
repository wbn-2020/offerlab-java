package com.offerlab.community.incentive.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.incentive.api.quota.BenefitCodes;
import com.offerlab.community.incentive.api.quota.EntitlementCapabilityDTO;
import com.offerlab.community.incentive.api.quota.EntitlementConfirmCmd;
import com.offerlab.community.incentive.api.quota.EntitlementConsumerDescriptor;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EntitlementQuotaService implements EntitlementQuotaFacade {
    private static final long MAX_QUOTA_PER_REQUEST = 1L;
    private static final long MIN_RESERVATION_TTL_SECONDS = 30L;
    private static final long MAX_RESERVATION_TTL_SECONDS = 15 * 60L;

    private final IncentiveMapper mapper;
    private final SnowflakeIdGenerator idGenerator;
    private final EntitlementConsumerRegistry consumerRegistry;

    @Value("${offerlab.incentive.entitlement-reservation.ttl-seconds:120}")
    private long reservationTtlSeconds;

    @Override
    public EntitlementCapabilityDTO capability(Long uid, String benefitCode, String consumerCode) {
        requireUid(uid);
        EntitlementConsumerDescriptor descriptor = consumerRegistry.find(benefitCode, consumerCode)
                .orElseGet(() -> unavailableDescriptor(benefitCode, consumerCode, "ENTITLEMENT_CONSUMER_UNAVAILABLE"));
        if (!descriptor.runtimeAvailable()) {
            return new EntitlementCapabilityDTO(benefitCode, consumerCode, 0, false,
                    descriptor.unavailableReason(), descriptor.targetPath());
        }
        long availableQuantity = mapper.sumUserEntitlementQuantity(uid, benefitCode);
        return new EntitlementCapabilityDTO(benefitCode, consumerCode, availableQuantity,
                availableQuantity > 0, availableQuantity > 0 ? null : "AI_ASSIST_QUOTA_INSUFFICIENT",
                descriptor.targetPath());
    }

    @Override
    @Transactional
    public EntitlementReservationDTO reserve(EntitlementReservationCmd command) {
        validateReservation(command);
        BenefitEntitlementUsagePO existing = mapper.selectEntitlementUsageByRequest(
                command.uid(), command.consumerCode(), command.idempotencyKey());
        if (existing != null) {
            requireEquivalent(existing, command.requestFingerprint());
            return reservationOf(existing);
        }
        if (!consumerRegistry.isRuntimeAvailable(command.benefitCode())) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_DISABLED");
        }
        BenefitEntitlementPO entitlement = mapper.lockFirstActiveEntitlement(
                command.uid(), command.benefitCode(), command.amount());
        if (entitlement == null) {
            BenefitEntitlementUsagePO concurrent = mapper.lockEntitlementUsageByRequest(
                    command.uid(), command.consumerCode(), command.idempotencyKey());
            if (concurrent != null) {
                requireEquivalent(concurrent, command.requestFingerprint());
                return reservationOf(concurrent);
            }
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_QUOTA_INSUFFICIENT");
        }
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(reservationTtlSeconds(command));
        try {
            int inserted = mapper.insertEntitlementReservation(
                    idGenerator.nextId(), entitlement.getId(), command.uid(), command.benefitCode(), command.consumerCode(),
                    command.amount(), command.idempotencyKey(), command.requestFingerprint(), command.sourceType(),
                    command.sourceRef(), expiresAt, command.reasonCode());
            if (inserted != 1) {
                return concurrentReservation(command);
            }
        } catch (DuplicateKeyException ex) {
            return concurrentReservation(command);
        }
        if (mapper.consumeEntitlement(entitlement.getId(), command.uid(), command.amount()) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "AI_ASSIST_QUOTA_INSUFFICIENT");
        }
        BenefitEntitlementUsagePO created = mapper.selectEntitlementUsageByRequest(
                command.uid(), command.consumerCode(), command.idempotencyKey());
        return reservationOf(created);
    }

    @Override
    @Transactional
    public EntitlementUsageDTO confirm(EntitlementConfirmCmd command) {
        requireCommand(command == null ? null : command.uid(), command == null ? null : command.usageId(),
                command == null ? null : command.consumerCode());
        BenefitEntitlementUsagePO usage = mapper.lockEntitlementUsage(command.usageId());
        requireUsageOwner(usage, command.uid(), command.consumerCode());
        if ("RELEASED".equals(usage.getUsageStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "entitlement reservation was released");
        }
        if ("RESERVED".equals(usage.getUsageStatus())
                && mapper.confirmEntitlementUsage(command.usageId(), command.uid(), command.consumerCode()) != 1) {
            return releaseLocked(usage, command.uid(), command.consumerCode(), "AI_ASSIST_RESERVATION_EXPIRED");
        }
        return usageOf(mapper.lockEntitlementUsage(command.usageId()));
    }

    @Override
    @Transactional
    public EntitlementUsageDTO release(EntitlementReleaseCmd command) {
        requireCommand(command == null ? null : command.uid(), command == null ? null : command.usageId(),
                command == null ? null : command.consumerCode());
        BenefitEntitlementUsagePO usage = mapper.lockEntitlementUsage(command.usageId());
        requireUsageOwner(usage, command.uid(), command.consumerCode());
        return releaseLocked(usage, command.uid(), command.consumerCode(), cleanFailureCode(command.failureCode()));
    }

    @Override
    public EntitlementUsageDTO findByRequest(Long uid, String consumerCode, String idempotencyKey) {
        requireUid(uid);
        requireText(consumerCode, 64, "consumerCode");
        requireText(idempotencyKey, 96, "idempotencyKey");
        BenefitEntitlementUsagePO usage = mapper.selectEntitlementUsageByRequest(uid, consumerCode, idempotencyKey);
        return usage == null ? null : usageOf(usage);
    }

    public List<BenefitEntitlementUsagePO> expiredReservations(int limit) {
        return mapper.selectExpiredEntitlementReservations(Math.max(1, Math.min(limit, 1000)));
    }

    private static EntitlementConsumerDescriptor unavailableDescriptor(String benefitCode, String consumerCode,
                                                                       String reason) {
        return new EntitlementConsumerDescriptor(benefitCode, consumerCode, "", "", false, false, reason);
    }

    private static void validateReservation(EntitlementReservationCmd command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        requireUid(command.uid());
        if (!BenefitCodes.AI_ASSIST_QUOTA.equals(command.benefitCode())
                || !BenefitCodes.CONTENT_ASSIST_ENHANCED.equals(command.consumerCode())
                || command.amount() != MAX_QUOTA_PER_REQUEST) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        requireText(command.idempotencyKey(), 96, "idempotencyKey");
        requireText(command.requestFingerprint(), 64, "requestFingerprint");
        requireText(command.sourceType(), 32, "sourceType");
        requireText(command.sourceRef(), 128, "sourceRef");
        requireText(command.reasonCode(), 64, "reasonCode");
        if (command.minimumTtlSeconds() < MIN_RESERVATION_TTL_SECONDS
                || command.minimumTtlSeconds() > MAX_RESERVATION_TTL_SECONDS) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "minimumTtlSeconds is invalid");
        }
    }

    private static void requireCommand(Long uid, Long usageId, String consumerCode) {
        requireUid(uid);
        if (usageId == null || usageId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        requireText(consumerCode, 64, "consumerCode");
    }

    private static void requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), field + " is invalid");
        }
    }

    private static void requireEquivalent(BenefitEntitlementUsagePO usage, String fingerprint) {
        if (usage == null || !Objects.equals(usage.getRequestFingerprint(), fingerprint)) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "AI_ASSIST_IDEMPOTENCY_CONFLICT");
        }
    }

    private static void requireUsageOwner(BenefitEntitlementUsagePO usage, Long uid, String consumerCode) {
        if (usage == null || !Objects.equals(usage.getUserId(), uid)
                || !Objects.equals(usage.getConsumerCode(), consumerCode)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private static String cleanFailureCode(String value) {
        if (value == null || value.isBlank()) {
            return "AI_ASSIST_PROVIDER_FAILED";
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private long reservationTtlSeconds(EntitlementReservationCmd command) {
        return Math.min(MAX_RESERVATION_TTL_SECONDS, Math.max(
                MIN_RESERVATION_TTL_SECONDS,
                Math.max(reservationTtlSeconds, command.minimumTtlSeconds())));
    }

    private EntitlementReservationDTO concurrentReservation(EntitlementReservationCmd command) {
        BenefitEntitlementUsagePO concurrent = mapper.lockEntitlementUsageByRequest(
                command.uid(), command.consumerCode(), command.idempotencyKey());
        requireEquivalent(concurrent, command.requestFingerprint());
        return reservationOf(concurrent);
    }

    private EntitlementUsageDTO releaseLocked(BenefitEntitlementUsagePO usage, Long uid, String consumerCode,
                                              String failureCode) {
        if (!"RESERVED".equals(usage.getUsageStatus())) {
            return usageOf(mapper.lockEntitlementUsage(usage.getId()));
        }
        if (mapper.releaseEntitlementUsage(usage.getId(), uid, consumerCode, failureCode) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "entitlement reservation release failed");
        }
        if (mapper.restoreEntitlementQuota(usage.getEntitlementId(), uid, usage.getAmount()) != 1) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(), "entitlement quota restore failed");
        }
        return usageOf(mapper.lockEntitlementUsage(usage.getId()));
    }

    private static EntitlementReservationDTO reservationOf(BenefitEntitlementUsagePO usage) {
        return new EntitlementReservationDTO(usage.getId(), usage.getEntitlementId(),
                EntitlementUsageStatus.valueOf(usage.getUsageStatus()), usage.getAmount(), usage.getExpiresAt());
    }

    private static EntitlementUsageDTO usageOf(BenefitEntitlementUsagePO usage) {
        return new EntitlementUsageDTO(usage.getId(), usage.getEntitlementId(), usage.getUserId(),
                usage.getBenefitCode(), usage.getConsumerCode(), EntitlementUsageStatus.valueOf(usage.getUsageStatus()),
                usage.getAmount(), usage.getIdempotencyKey(), usage.getRequestFingerprint(), usage.getFailureCode(),
                usage.getExpiresAt(), usage.getConfirmedAt(), usage.getReleasedAt());
    }
}

package com.offerlab.community.user.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.user.api.DeliveryPreferenceCapability;
import com.offerlab.community.user.api.UserRelationshipSourceVerifier;
import com.offerlab.community.user.api.UserSubscriptionPreferenceFacade;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceDTO;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceKeyDTO;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserFollowMapper;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserSubscriptionPreferenceMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserSubscriptionPreferencePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserSubscriptionPreferenceService implements UserSubscriptionPreferenceFacade {

    public static final String DEFAULT_DELIVERY_MODE = "IMMEDIATE";
    private static final int MAX_SOURCE_KEY_BATCH_SIZE = 200;
    private static final List<String> SOURCE_TYPES =
            List.of("USER", "TOPIC", "DISCUSSION", "NEED", "SERIES");
    private static final List<String> DELIVERY_MODES =
            List.of(DEFAULT_DELIVERY_MODE, "DIGEST", "MUTED");

    private final UserSubscriptionPreferenceMapper preferenceMapper;
    private final UserFollowMapper followMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final List<UserRelationshipSourceVerifier> sourceVerifiers;

    @Override
    public UserSubscriptionPreferenceDTO get(Long uid, String sourceType, Long sourceId) {
        Long safeUid = requireUid(uid);
        String safeSourceType = normalizeSourceType(sourceType);
        Long safeSourceId = requireSourceId(sourceId);
        requireActiveRelationship(safeUid, safeSourceType, safeSourceId);

        UserSubscriptionPreferencePO preference = preferenceMapper.findEffective(
                safeUid, safeSourceType, safeSourceId, LocalDateTime.now());
        return preference == null
                ? defaultPreference(safeSourceType, safeSourceId)
                : toDto(preference);
    }

    @Override
    public Map<String, UserSubscriptionPreferenceDTO> findEffective(
            Long uid, Collection<UserSubscriptionPreferenceKeyDTO> sourceKeys) {
        Long safeUid = requireUid(uid);
        if (sourceKeys == null || sourceKeys.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, UserSubscriptionPreferenceKeyDTO> normalized = new LinkedHashMap<>();
        for (UserSubscriptionPreferenceKeyDTO sourceKey : sourceKeys) {
            if (sourceKey == null) {
                continue;
            }
            String sourceType = normalizeSourceType(sourceKey.getSourceType());
            Long sourceId = requireSourceId(sourceKey.getSourceId());
            normalized.putIfAbsent(
                    resourceKey(sourceType, sourceId),
                    UserSubscriptionPreferenceKeyDTO.builder()
                            .sourceType(sourceType)
                            .sourceId(sourceId)
                            .build());
            if (normalized.size() >= MAX_SOURCE_KEY_BATCH_SIZE) {
                break;
            }
        }
        if (normalized.isEmpty()) {
            return Map.of();
        }

        List<UserSubscriptionPreferencePO> rows = preferenceMapper.findEffectiveBatch(
                safeUid, normalized.values(), LocalDateTime.now());
        Set<String> requestedKeys = new LinkedHashSet<>(normalized.keySet());
        Map<String, UserSubscriptionPreferenceDTO> result = new LinkedHashMap<>();
        for (UserSubscriptionPreferencePO row
                : rows == null ? List.<UserSubscriptionPreferencePO>of() : rows) {
            if (row == null || !SOURCE_TYPES.contains(row.getSourceType())
                    || row.getSourceId() == null || !DELIVERY_MODES.contains(row.getDeliveryMode())) {
                continue;
            }
            String key = resourceKey(row.getSourceType(), row.getSourceId());
            if (requestedKeys.contains(key)) {
                result.putIfAbsent(key, toDto(row));
            }
        }
        return Map.copyOf(result);
    }

    @Override
    public Map<Long, UserSubscriptionPreferenceDTO> findEffectiveForRecipients(
            Collection<Long> receiverUids, String sourceType, Long sourceId) {
        String safeSourceType = normalizeSourceType(sourceType);
        Long safeSourceId = requireSourceId(sourceId);
        List<Long> normalizedRecipients = normalizeReceiverUids(receiverUids);
        if (normalizedRecipients.isEmpty()) {
            return Map.of();
        }

        List<UserSubscriptionPreferencePO> rows = preferenceMapper.findEffectiveForRecipients(
                normalizedRecipients, safeSourceType, safeSourceId, LocalDateTime.now());
        Set<Long> requestedUids = new LinkedHashSet<>(normalizedRecipients);
        Map<Long, UserSubscriptionPreferenceDTO> result = new LinkedHashMap<>();
        for (UserSubscriptionPreferencePO row
                : rows == null ? List.<UserSubscriptionPreferencePO>of() : rows) {
            if (row == null || row.getUid() == null || !requestedUids.contains(row.getUid())
                    || !safeSourceType.equals(row.getSourceType())
                    || !safeSourceId.equals(row.getSourceId())
                    || !DELIVERY_MODES.contains(row.getDeliveryMode())) {
                continue;
            }
            result.putIfAbsent(row.getUid(), toDto(row));
        }
        return Map.copyOf(result);
    }

    @Override
    @Transactional
    public UserSubscriptionPreferenceDTO upsert(Long uid,
                                                String sourceType,
                                                Long sourceId,
                                                String deliveryMode,
                                                LocalDateTime expiresAt) {
        Long safeUid = requireUid(uid);
        String safeSourceType = normalizeSourceType(sourceType);
        Long safeSourceId = requireSourceId(sourceId);
        requireDeliveryPreferenceSupported(safeSourceType);
        String safeDeliveryMode = normalizeDeliveryMode(deliveryMode);
        LocalDateTime now = LocalDateTime.now();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "expiresAt must be in the future");
        }
        requireActiveRelationship(safeUid, safeSourceType, safeSourceId);

        UserSubscriptionPreferencePO preference = new UserSubscriptionPreferencePO();
        preference.setId(idGenerator.nextId());
        preference.setUid(safeUid);
        preference.setSourceType(safeSourceType);
        preference.setSourceId(safeSourceId);
        preference.setDeliveryMode(safeDeliveryMode);
        preference.setExpiresAt(expiresAt);
        preference.setCreateTime(now);
        preference.setUpdateTime(now);
        preference.setIsDeleted(0);
        preferenceMapper.upsert(preference);
        return toDto(preference);
    }

    @Override
    @Transactional
    public void delete(Long uid, String sourceType, Long sourceId) {
        preferenceMapper.softDelete(
                requireUid(uid),
                normalizeSourceType(sourceType),
                requireSourceId(sourceId),
                LocalDateTime.now());
    }

    private void requireActiveRelationship(Long uid, String sourceType, Long sourceId) {
        boolean exists = "USER".equals(sourceType)
                ? followMapper.existsActiveFollowing(uid, sourceId) > 0
                : sourceVerifiers.stream()
                        .filter(verifier -> verifier.supports(sourceType))
                        .anyMatch(verifier -> verifier.exists(uid, sourceType, sourceId));
        if (!exists) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), "relationship does not exist");
        }
    }

    private static UserSubscriptionPreferenceDTO defaultPreference(String sourceType, Long sourceId) {
        DeliveryPreferenceCapability capability =
                DeliveryPreferenceCapability.forSourceType(sourceType);
        return UserSubscriptionPreferenceDTO.builder()
                .sourceType(sourceType)
                .sourceId(sourceId)
                .deliveryMode(DEFAULT_DELIVERY_MODE)
                .deliveryPreferenceSupported(capability.deliveryPreferenceSupported())
                .deliveryPreferenceUnsupportedReason(
                        capability.deliveryPreferenceUnsupportedReason())
                .build();
    }

    private static UserSubscriptionPreferenceDTO toDto(UserSubscriptionPreferencePO preference) {
        DeliveryPreferenceCapability capability =
                DeliveryPreferenceCapability.forSourceType(preference.getSourceType());
        return UserSubscriptionPreferenceDTO.builder()
                .sourceType(preference.getSourceType())
                .sourceId(preference.getSourceId())
                .deliveryMode(preference.getDeliveryMode())
                .expiresAt(preference.getExpiresAt())
                .deliveryPreferenceSupported(capability.deliveryPreferenceSupported())
                .deliveryPreferenceUnsupportedReason(
                        capability.deliveryPreferenceUnsupportedReason())
                .build();
    }

    private static Long requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return uid;
    }

    private static Long requireSourceId(Long sourceId) {
        if (sourceId == null || sourceId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "sourceId is invalid");
        }
        return sourceId;
    }

    private static List<Long> normalizeReceiverUids(Collection<Long> receiverUids) {
        if (receiverUids == null || receiverUids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long receiverUid : receiverUids) {
            if (receiverUid == null) {
                continue;
            }
            normalized.add(requireUid(receiverUid));
            if (normalized.size() > UserSubscriptionPreferenceFacade.MAX_RECIPIENT_BATCH_SIZE) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                        "receiverUids exceeds max batch size");
            }
        }
        return List.copyOf(normalized);
    }

    private static void requireDeliveryPreferenceSupported(String sourceType) {
        if (!DeliveryPreferenceCapability.isSupported(sourceType)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "delivery preference is not supported for sourceType: " + sourceType);
        }
    }

    private static String normalizeSourceType(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "sourceType is invalid");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SOURCE_TYPES.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "sourceType is invalid");
        }
        return normalized;
    }

    private static String normalizeDeliveryMode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "deliveryMode is invalid");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!DELIVERY_MODES.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "deliveryMode is invalid");
        }
        return normalized;
    }

    private static String resourceKey(String sourceType, Long sourceId) {
        return sourceType + ":" + sourceId;
    }
}

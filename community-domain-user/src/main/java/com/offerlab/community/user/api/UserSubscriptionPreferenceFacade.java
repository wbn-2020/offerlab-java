package com.offerlab.community.user.api;

import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceDTO;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceKeyDTO;

import java.util.Collection;
import java.util.Map;

public interface UserSubscriptionPreferenceFacade {

    UserSubscriptionPreferenceDTO get(Long uid, String sourceType, Long sourceId);

    Map<String, UserSubscriptionPreferenceDTO> findEffective(
            Long uid, Collection<UserSubscriptionPreferenceKeyDTO> sourceKeys);

    UserSubscriptionPreferenceDTO upsert(Long uid,
                                         String sourceType,
                                         Long sourceId,
                                         String deliveryMode,
                                         java.time.LocalDateTime expiresAt);

    void delete(Long uid, String sourceType, Long sourceId);
}

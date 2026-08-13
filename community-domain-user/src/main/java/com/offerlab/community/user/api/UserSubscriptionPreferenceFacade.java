package com.offerlab.community.user.api;

import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceDTO;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceKeyDTO;

import java.util.Collection;
import java.util.Map;

public interface UserSubscriptionPreferenceFacade {

    int MAX_RECIPIENT_BATCH_SIZE = 1000;

    UserSubscriptionPreferenceDTO get(Long uid, String sourceType, Long sourceId);

    Map<String, UserSubscriptionPreferenceDTO> findEffective(
            Long uid, Collection<UserSubscriptionPreferenceKeyDTO> sourceKeys);

    /**
     * Returns explicit, effective preferences for a bounded receiver batch and one source.
     *
     * <p>Missing receivers are intentionally omitted so the caller can apply the
     * default {@code IMMEDIATE} mode. This read does not revalidate relationships;
     * the source domain owns candidate-recipient validity. Callers must partition
     * candidate lists larger than {@value #MAX_RECIPIENT_BATCH_SIZE}.</p>
     */
    Map<Long, UserSubscriptionPreferenceDTO> findEffectiveForRecipients(
            Collection<Long> receiverUids, String sourceType, Long sourceId);

    UserSubscriptionPreferenceDTO upsert(Long uid,
                                          String sourceType,
                                          Long sourceId,
                                         String deliveryMode,
                                         java.time.LocalDateTime expiresAt);

    void delete(Long uid, String sourceType, Long sourceId);
}

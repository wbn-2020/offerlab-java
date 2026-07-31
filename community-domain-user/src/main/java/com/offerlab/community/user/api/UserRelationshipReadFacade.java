package com.offerlab.community.user.api;

import com.offerlab.community.user.api.dto.UserRelationshipItemDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Read-only user relationship port for cross-domain relationship projections.
 */
public interface UserRelationshipReadFacade {

    default List<UserRelationshipItemDTO> listFollowing(Long uid,
                                                        LocalDateTime cursorTime,
                                                        Long cursorId,
                                                        String cursorSourceType,
                                                        int limit) {
        return listFollowing(uid, cursorTime, cursorId, cursorSourceType, "ALL", limit);
    }

    List<UserRelationshipItemDTO> listFollowing(Long uid,
                                                LocalDateTime cursorTime,
                                                Long cursorId,
                                                String cursorSourceType,
                                                String mode,
                                                int limit);

    long countFollowing(Long uid);

    default Map<String, Long> countFollowingByDeliveryMode(Long uid) {
        return Map.of("IMMEDIATE", countFollowing(uid), "DIGEST", 0L, "MUTED", 0L);
    }
}

package com.offerlab.community.user.application;

import com.offerlab.community.user.api.UserRelationshipReadFacade;
import com.offerlab.community.user.api.dto.UserRelationshipItemDTO;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserFollowMapper;
import com.offerlab.community.user.infrastructure.persistence.projection.UserRelationshipView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserRelationshipReadService implements UserRelationshipReadFacade {

    private static final int MAX_LIMIT = 101;

    private final UserFollowMapper followMapper;

    @Override
    public List<UserRelationshipItemDTO> listFollowing(Long uid,
                                                        LocalDateTime cursorTime,
                                                        Long cursorId,
                                                        String cursorSourceType,
                                                        String mode,
                                                        int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<UserRelationshipView> rows = followMapper.selectFollowingRelationshipRows(
                uid, cursorTime, cursorId, cursorSourceType, mode, safeLimit);
        return (rows == null ? List.<UserRelationshipView>of() : rows).stream()
                .map(row -> UserRelationshipItemDTO.builder()
                        .relationId(row.getRelationId())
                        .uid(row.getUid())
                        .nickname(row.getNickname())
                        .bio(row.getBio())
                        .relationTime(row.getRelationTime())
                        .lastPublicUpdateAt(row.getLastPublicUpdateAt())
                        .deliveryMode(row.getDeliveryMode())
                        .expiresAt(row.getExpiresAt())
                        .build())
                .toList();
    }

    @Override
    public long countFollowing(Long uid) {
        return followMapper.countVisibleFollowingRelationships(uid);
    }

    @Override
    public Map<String, Long> countFollowingByDeliveryMode(Long uid) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("IMMEDIATE", 0L);
        counts.put("DIGEST", 0L);
        counts.put("MUTED", 0L);
        var rows = followMapper.countVisibleFollowingRelationshipsByDeliveryMode(uid);
        if (rows != null) {
            rows.forEach(row -> {
                if (row.getDeliveryMode() != null && counts.containsKey(row.getDeliveryMode())) {
                    counts.put(row.getDeliveryMode(), Math.max(0L, row.getCount() == null ? 0L : row.getCount()));
                }
            });
        }
        return counts;
    }
}

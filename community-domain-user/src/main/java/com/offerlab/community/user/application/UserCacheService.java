package com.offerlab.community.user.application;

import com.offerlab.community.infra.redis.cache.CacheKeyBuilder;
import com.offerlab.community.infra.redis.cache.MultiLevelCache;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserCacheService {

    private final MultiLevelCache<UserBriefDTO> userBriefCache;

    public void evictBrief(Long... uids) {
        if (uids == null) {
            return;
        }
        for (Long uid : uids) {
            if (uid != null) {
                userBriefCache.evict(CacheKeyBuilder.userProfile(uid));
            }
        }
    }
}

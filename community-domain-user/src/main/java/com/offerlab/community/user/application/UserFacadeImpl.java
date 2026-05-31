package com.offerlab.community.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.redis.cache.CacheKeyBuilder;
import com.offerlab.community.infra.redis.cache.MultiLevelCache;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.FollowCursorDTO;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import com.offerlab.community.user.api.dto.UserIntentDTO;
import com.offerlab.community.user.domain.model.User;
import com.offerlab.community.user.domain.repository.FollowRepository;
import com.offerlab.community.user.domain.repository.UserRepository;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserCounterMapper;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserPrivacySettingMapper;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserProfileMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserCounterPO;
import com.offerlab.community.user.infrastructure.persistence.po.UserPrivacySettingPO;
import com.offerlab.community.user.infrastructure.persistence.po.UserProfilePO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserFacadeImpl implements UserFacade {

    private final UserRepository userRepo;
    private final FollowRepository followRepo;
    private final UserCounterMapper counterMapper;
    private final UserProfileMapper profileMapper;
    private final UserPrivacySettingMapper privacySettingMapper;
    private final ObjectMapper objectMapper;
    private final MultiLevelCache<UserBriefDTO> multiLevelCache;

    @Value("${offerlab.feed.bigv-threshold:1000}")
    private long bigvThreshold;

    @Override
    public UserBriefDTO getUserBrief(Long uid) {
        String cacheKey = CacheKeyBuilder.userProfile(uid);
        return multiLevelCache.get(cacheKey, key -> {
            User user = userRepo.findById(uid).orElse(null);
            return user != null ? toBrief(user) : null;
        }, UserBriefDTO.class);
    }

    @Override
    public Map<Long, UserBriefDTO> batchGetUserBriefs(Collection<Long> uids) {
        if (uids == null || uids.isEmpty()) return Map.of();
        Map<Long, UserBriefDTO> result = new HashMap<>(uids.size());
        for (Long uid : uids) {
            UserBriefDTO dto = getUserBrief(uid);
            if (dto != null) result.put(uid, dto);
        }
        return result;
    }

    @Override
    public Map<String, Long> findUserIdsByNicknames(Collection<String> nicknames) {
        if (nicknames == null || nicknames.isEmpty()) return Map.of();
        List<String> names = nicknames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .limit(20)
                .toList();
        if (names.isEmpty()) return Map.of();
        return profileMapper.selectList(new LambdaQueryWrapper<UserProfilePO>()
                        .in(UserProfilePO::getNickname, names)
                        .eq(UserProfilePO::getIsDeleted, 0))
                .stream()
                .collect(Collectors.toMap(UserProfilePO::getNickname, UserProfilePO::getId, (a, b) -> a));
    }

    @Override
    public boolean isFollowing(Long fromUid, Long toUid) {
        return followRepo.isFollowing(fromUid, toUid);
    }

    @Override
    public Map<Long, Boolean> batchIsFollowing(Long fromUid, Collection<Long> toUids) {
        if (toUids == null || toUids.isEmpty()) return Map.of();
        return toUids.stream().distinct()
                .collect(Collectors.toMap(t -> t, t -> followRepo.isFollowing(fromUid, t)));
    }

    @Override
    public List<Long> getFollowerIds(Long uid, long cursor, int size) {
        return followRepo.followerIds(uid, cursor, size);
    }

    @Override
    public List<FollowCursorDTO> getFollowerPage(Long uid, long cursor, int size) {
        return followRepo.followerPage(uid, cursor, size);
    }

    @Override
    public List<Long> getFollowingIds(Long uid, long cursor, int size) {
        return followRepo.followingIds(uid, cursor, size);
    }

    @Override
    public List<FollowCursorDTO> getFollowingPage(Long uid, long cursor, int size) {
        return followRepo.followingPage(uid, cursor, size);
    }

    @Override
    public long getFollowerCount(Long uid) {
        UserCounterPO c = counterMapper.selectById(uid);
        return c == null ? 0 : c.getFollowerCount();
    }

    @Override
    public boolean isBigV(Long uid) {
        return getFollowerCount(uid) >= bigvThreshold;
    }

    @Override
    public UserIntentDTO getUserIntent(Long uid) {
        return userRepo.findById(uid)
                .map(User::getIntentJson)
                .filter(s -> s != null && !s.isBlank())
                .map(this::parseIntent)
                .orElse(null);
    }

    @Override
    public boolean isProfileVisible(Long viewerUid, Long targetUid) {
        return visible(viewerUid, targetUid, setting(targetUid).getProfileVisibility());
    }

    @Override
    public boolean isIntentVisible(Long viewerUid, Long targetUid) {
        return visible(viewerUid, targetUid, setting(targetUid).getIntentVisibility());
    }

    @Override
    public boolean isSearchable(Long uid) {
        return enabled(setting(uid).getSearchable());
    }

    @Override
    public boolean allowsInteractionNotification(Long uid) {
        return enabled(setting(uid).getInteractionNotification());
    }

    @Override
    public boolean allowsLikeNotification(Long uid) {
        UserPrivacySettingPO setting = setting(uid);
        return allowsInteraction(setting) && enabled(setting.getLikeNotification());
    }

    @Override
    public boolean allowsCommentNotification(Long uid) {
        UserPrivacySettingPO setting = setting(uid);
        return allowsInteraction(setting) && enabled(setting.getCommentNotification());
    }

    @Override
    public boolean allowsFollowNotification(Long uid) {
        UserPrivacySettingPO setting = setting(uid);
        return allowsInteraction(setting) && enabled(setting.getFollowNotification());
    }

    @Override
    public boolean allowsFavoriteNotification(Long uid) {
        UserPrivacySettingPO setting = setting(uid);
        return allowsInteraction(setting) && enabled(setting.getFavoriteNotification());
    }

    @Override
    public boolean allowsMentionNotification(Long uid) {
        UserPrivacySettingPO setting = setting(uid);
        return allowsInteraction(setting) && enabled(setting.getMentionNotification());
    }

    @Override
    public boolean allowsSystemNotification(Long uid) {
        return enabled(setting(uid).getSystemNotification());
    }

    private UserIntentDTO parseIntent(String json) {
        try {
            return objectMapper.readValue(json, UserIntentDTO.class);
        } catch (Exception e) {
            log.warn("parse intent failed: {}", e.getMessage());
            return null;
        }
    }

    private UserBriefDTO toBrief(User u) {
        UserCounterPO c = counterMapper.selectById(u.getId());
        UserBriefDTO.UserBriefDTOBuilder b = UserBriefDTO.builder()
                .uid(u.getId())
                .nickname(u.getNickname())
                .avatarUrl(u.getAvatarUrl())
                .bio(u.getBio());
        if (c != null) {
            b.followerCount(c.getFollowerCount())
             .followingCount(c.getFollowingCount())
             .postCount(c.getPostCount());
        } else {
            b.followerCount(0L).followingCount(0L).postCount(0L);
        }
        return b.build();
    }

    private UserPrivacySettingPO setting(Long uid) {
        UserPrivacySettingPO setting = privacySettingMapper.selectById(uid);
        if (setting != null) {
            return setting;
        }
        UserPrivacySettingPO defaults = new UserPrivacySettingPO();
        defaults.setProfileVisibility("PUBLIC");
        defaults.setIntentVisibility("PUBLIC");
        defaults.setSearchable(1);
        defaults.setInteractionNotification(1);
        defaults.setSystemNotification(1);
        defaults.setLikeNotification(1);
        defaults.setCommentNotification(1);
        defaults.setFollowNotification(1);
        defaults.setFavoriteNotification(1);
        defaults.setMentionNotification(1);
        return defaults;
    }

    private boolean allowsInteraction(UserPrivacySettingPO setting) {
        return enabled(setting.getInteractionNotification());
    }

    private boolean visible(Long viewerUid, Long targetUid, String visibility) {
        if (targetUid == null) {
            return false;
        }
        if (targetUid.equals(viewerUid)) {
            return true;
        }
        if ("PRIVATE".equalsIgnoreCase(visibility)) {
            return false;
        }
        return !"FOLLOWERS".equalsIgnoreCase(visibility)
                || viewerUid != null && followRepo.isFollowing(viewerUid, targetUid);
    }

    private boolean enabled(Integer value) {
        return value == null || value == 1;
    }
}

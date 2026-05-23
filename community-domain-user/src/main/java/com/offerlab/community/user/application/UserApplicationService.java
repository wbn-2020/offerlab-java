package com.offerlab.community.user.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.infra.security.PasswordEncoder;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import com.offerlab.community.user.api.dto.UserIntentDTO;
import com.offerlab.community.user.api.dto.UserPrivacySettingDTO;
import com.offerlab.community.user.api.event.UserFollowedEvent;
import com.offerlab.community.user.domain.model.User;
import com.offerlab.community.user.domain.repository.FollowRepository;
import com.offerlab.community.user.domain.repository.UserRepository;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserPrivacySettingMapper;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserProfileMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserPrivacySettingPO;
import com.offerlab.community.user.infrastructure.persistence.po.UserProfilePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * 用户应用服务：编排领域逻辑，事务边界
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserApplicationService {

    private final UserRepository userRepo;
    private final FollowRepository followRepo;
    private final SnowflakeIdGenerator idGen;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final UserPrivacySettingMapper privacySettingMapper;
    private final UserProfileMapper profileMapper;

    @Transactional
    public Long register(String email, String password, String nickname) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password) || !StringUtils.hasText(nickname)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (password.length() < 6 || password.length() > 64) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        userRepo.findByEmail(email).ifPresent(u -> {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS);
        });

        long uid = idGen.nextId();
        User user = User.builder()
                .id(uid)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .nickname(nickname)
                .accountStatus(User.STATUS_NORMAL)
                .build();
        userRepo.register(user);
        log.info("user registered: uid={} email={}", uid, email);
        return uid;
    }

    public String login(String email, String password, String ip) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }
        userRepo.updateLastLogin(user.getId(), ip);
        return jwtService.issue(user.getId());
    }

    public void logout(String token) {
        jwtService.invalidate(token);
    }

    @Transactional
    public void changePassword(Long uid, String oldPassword, String newPassword) {
        User user = getUser(uid);
        if (!StringUtils.hasText(oldPassword) || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6 || newPassword.length() > 64
                || oldPassword.equals(newPassword)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        userRepo.updatePassword(uid, passwordEncoder.encode(newPassword));
        jwtService.invalidateAll(uid);
    }

    public void logoutAll(Long uid) {
        getUser(uid);
        jwtService.invalidateAll(uid);
    }

    @Transactional
    public void follow(Long fromUid, Long toUid) {
        if (fromUid.equals(toUid)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        userRepo.findById(toUid).orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        boolean ok = followRepo.follow(fromUid, toUid);
        if (!ok) {
            throw new BizException(ErrorCode.FOLLOW_ALREADY_EXISTS);
        }
        eventPublisher.publish(UserFollowedEvent.builder()
                .followerId(fromUid)
                .followeeId(toUid)
                .timestamp(Instant.now().toEpochMilli())
                .build());
    }

    @Transactional
    public void unfollow(Long fromUid, Long toUid) {
        boolean ok = followRepo.unfollow(fromUid, toUid);
        if (!ok) {
            throw new BizException(ErrorCode.FOLLOW_NOT_EXISTS);
        }
    }

    public User getUser(Long uid) {
        return userRepo.findById(uid).orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public void updateProfile(Long uid, String nickname, String avatarUrl, String bio) {
        User u = getUser(uid);
        if (StringUtils.hasText(nickname)) u.setNickname(nickname);
        if (avatarUrl != null) u.setAvatarUrl(avatarUrl);
        if (bio != null) u.setBio(bio);
        userRepo.updateProfile(u);
    }

    @Transactional
    public void updateIntent(Long uid, UserIntentDTO intent) {
        User u = getUser(uid);
        try {
            // 求职意向当前以 JSON 存在用户资料表，DTO 需保持字段兼容后再序列化。
            u.setIntentJson(objectMapper.writeValueAsString(intent));
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        userRepo.updateProfile(u);
    }

    @Transactional
    public UserPrivacySettingDTO getPrivacySetting(Long uid) {
        getUser(uid);
        UserPrivacySettingPO po = privacySettingMapper.selectById(uid);
        if (po == null) {
            // 首次访问即落默认配置，后续隐私判断可只面对显式记录或同一套默认值。
            po = defaultPrivacySetting(uid);
            privacySettingMapper.insert(po);
        }
        return toPrivacyDTO(po);
    }

    @Transactional
    public UserPrivacySettingDTO updatePrivacySetting(Long uid, UserPrivacySettingDTO setting) {
        getUser(uid);
        UserPrivacySettingPO po = privacySettingMapper.selectById(uid);
        boolean exists = po != null;
        if (po == null) {
            // 允许用户在没有历史配置时直接保存，避免前端必须先调用 GET 初始化。
            po = defaultPrivacySetting(uid);
        }
        po.setProfileVisibility(normalizeVisibility(setting.getProfileVisibility()));
        po.setIntentVisibility(normalizeVisibility(setting.getIntentVisibility()));
        po.setSearchable(toFlag(setting.getSearchable()));
        po.setInteractionNotification(toFlag(setting.getInteractionNotification()));
        po.setSystemNotification(toFlag(setting.getSystemNotification()));
        if (exists) {
            privacySettingMapper.updateById(po);
        } else {
            privacySettingMapper.insert(po);
        }
        return toPrivacyDTO(po);
    }

    public List<UserBriefDTO> searchUsers(String keyword, Long viewerUid, int size, UserFacade userFacade) {
        int limit = Math.max(1, Math.min(size, 20));
        // 先放宽查询数量，再按隐私过滤并截断，避免少量受限用户占满结果页。
        LambdaQueryWrapper<UserProfilePO> query = new LambdaQueryWrapper<UserProfilePO>()
                .eq(UserProfilePO::getIsDeleted, 0)
                .orderByDesc(UserProfilePO::getUpdateTime)
                .last("LIMIT " + Math.min(limit * 3, 60));
        if (StringUtils.hasText(keyword)) {
            query.like(UserProfilePO::getNickname, keyword.trim());
        }
        return profileMapper.selectList(query)
                .stream()
                .map(UserProfilePO::getId)
                .filter(uid -> userFacade.isSearchable(uid) && userFacade.isProfileVisible(viewerUid, uid))
                .map(userFacade::getUserBrief)
                .filter(java.util.Objects::nonNull)
                .limit(limit)
                .toList();
    }

    private static UserPrivacySettingPO defaultPrivacySetting(Long uid) {
        UserPrivacySettingPO po = new UserPrivacySettingPO();
        po.setUserId(uid);
        po.setProfileVisibility("PUBLIC");
        po.setIntentVisibility("PUBLIC");
        po.setSearchable(1);
        po.setInteractionNotification(1);
        po.setSystemNotification(1);
        return po;
    }

    private static UserPrivacySettingDTO toPrivacyDTO(UserPrivacySettingPO po) {
        return UserPrivacySettingDTO.builder()
                .profileVisibility(po.getProfileVisibility())
                .intentVisibility(po.getIntentVisibility())
                .searchable(isEnabled(po.getSearchable()))
                .interactionNotification(isEnabled(po.getInteractionNotification()))
                .systemNotification(isEnabled(po.getSystemNotification()))
                .build();
    }

    private static boolean isEnabled(Integer value) {
        return value == null || value == 1;
    }

    private static int toFlag(Boolean value) {
        return Boolean.FALSE.equals(value) ? 0 : 1;
    }

    private static String normalizeVisibility(String value) {
        if ("PRIVATE".equalsIgnoreCase(value)) {
            return "PRIVATE";
        }
        if ("FOLLOWERS".equalsIgnoreCase(value)) {
            return "FOLLOWERS";
        }
        return "PUBLIC";
    }
}

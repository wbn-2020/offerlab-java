package com.offerlab.community.user.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.user.api.dto.ContactRequestPolicyCheckDTO;
import com.offerlab.community.user.api.dto.ContactRequestSettingsDTO;
import com.offerlab.community.user.domain.model.User;
import com.offerlab.community.user.domain.repository.FollowRepository;
import com.offerlab.community.user.domain.repository.UserRepository;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserPrivacySettingMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserPrivacySettingPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContactRequestSettingsService {
    public static final String POLICY_ALL = "all";
    public static final String POLICY_FOLLOWING = "following";
    public static final String POLICY_MUTUAL = "mutual";
    public static final String POLICY_OFF = "off";
    public static final String DEFAULT_POLICY = POLICY_FOLLOWING;

    private static final Set<String> POLICIES = Set.of(POLICY_ALL, POLICY_FOLLOWING, POLICY_MUTUAL, POLICY_OFF);
    private static final int MAX_DAILY_LIMIT = 1000;

    private final UserRepository userRepo;
    private final FollowRepository followRepo;
    private final UserPrivacySettingMapper privacySettingMapper;
    private final ContentModerationService contentModerationService;

    @Transactional
    public ContactRequestSettingsDTO getSettings(Long uid) {
        requireUser(uid);
        return toDTO(loadOrCreate(uid));
    }

    @Transactional
    public ContactRequestSettingsDTO updateSettings(Long uid, ContactRequestSettingsDTO setting) {
        requireUser(uid);
        UserPrivacySettingPO po = loadOrDefault(uid);
        boolean exists = privacySettingMapper.selectById(uid) != null;
        ContactRequestSettingsDTO normalized = normalizeForUpdate(setting, po);
        po.setAcceptContactRequest(toFlag(normalized.getAcceptContactRequest()));
        po.setContactRequestPolicy(normalized.getContactRequestPolicy());
        po.setContactRequestDailyLimit(normalized.getContactRequestDailyLimit());
        if (exists) {
            privacySettingMapper.updateById(po);
        } else {
            privacySettingMapper.insert(po);
        }
        return toDTO(po);
    }

    public ContactRequestPolicyCheckDTO checkPolicy(Long requesterUid, Long receiverUid) {
        if (!validUid(requesterUid) || !validUid(receiverUid)) {
            return denied("PARAM_ERROR", "用户参数不合法", null);
        }
        if (requesterUid.equals(receiverUid)) {
            return denied("SELF_CONTACT", "不能联系自己", null);
        }

        User requester = userRepo.findById(requesterUid).orElse(null);
        if (requester == null) {
            return denied("REQUESTER_NOT_FOUND", "发起用户不存在", null);
        }
        if (!requester.isActive()) {
            return denied("REQUESTER_FORBIDDEN", "发起用户当前不可发起联系请求", null);
        }
        User receiver = userRepo.findById(receiverUid).orElse(null);
        if (receiver == null) {
            return denied("RECEIVER_NOT_FOUND", "接收用户不存在", null);
        }
        if (!receiver.isActive()) {
            return denied("RECEIVER_UNAVAILABLE", "接收用户当前不可接收联系请求", null);
        }
        try {
            contentModerationService.requireUserCanPublish(requesterUid);
        } catch (BizException e) {
            return denied("REQUESTER_FORBIDDEN", "发起用户当前不可发起联系请求", null);
        }

        UserPrivacySettingPO setting = loadOrDefault(receiverUid);
        String policy = normalizePolicyOrDefault(setting.getContactRequestPolicy());
        ContactRequestPolicyCheckDTO.ContactRequestPolicyCheckDTOBuilder result = ContactRequestPolicyCheckDTO.builder()
                .contactRequestPolicy(policy)
                .contactRequestDailyLimit(setting.getContactRequestDailyLimit());

        if (!isEnabled(setting.getAcceptContactRequest()) || POLICY_OFF.equals(policy)) {
            return result.allowed(false)
                    .reasonCode("CONTACT_REQUEST_CLOSED")
                    .reasonMessage("对方暂未开放联系请求")
                    .build();
        }
        if (POLICY_ALL.equals(policy)) {
            return result.allowed(true).reasonCode("ALLOWED").reasonMessage("允许发起联系请求").build();
        }

        boolean receiverFollowsRequester = followRepo.isFollowing(receiverUid, requesterUid);
        if (POLICY_FOLLOWING.equals(policy)) {
            return receiverFollowsRequester
                    ? result.allowed(true).reasonCode("ALLOWED").reasonMessage("允许发起联系请求").build()
                    : result.allowed(false).reasonCode("POLICY_FOLLOWING_REQUIRED").reasonMessage("仅接收对方已关注用户的联系请求").build();
        }

        boolean requesterFollowsReceiver = followRepo.isFollowing(requesterUid, receiverUid);
        return receiverFollowsRequester && requesterFollowsReceiver
                ? result.allowed(true).reasonCode("ALLOWED").reasonMessage("允许发起联系请求").build()
                : result.allowed(false).reasonCode("POLICY_MUTUAL_REQUIRED").reasonMessage("仅接收互相关注用户的联系请求").build();
    }

    private User requireUser(Long uid) {
        return userRepo.findById(uid).orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    }

    private UserPrivacySettingPO loadOrCreate(Long uid) {
        UserPrivacySettingPO po = privacySettingMapper.selectById(uid);
        if (po == null) {
            po = defaultPrivacySetting(uid);
            privacySettingMapper.insert(po);
        }
        return withContactDefaults(po);
    }

    private UserPrivacySettingPO loadOrDefault(Long uid) {
        UserPrivacySettingPO po = privacySettingMapper.selectById(uid);
        return withContactDefaults(po == null ? defaultPrivacySetting(uid) : po);
    }

    private ContactRequestSettingsDTO normalizeForUpdate(ContactRequestSettingsDTO setting, UserPrivacySettingPO existing) {
        if (setting == null) {
            setting = new ContactRequestSettingsDTO();
        }
        boolean accept = setting.getAcceptContactRequest() == null
                ? isEnabled(existing.getAcceptContactRequest())
                : Boolean.TRUE.equals(setting.getAcceptContactRequest());
        String policy = setting.getContactRequestPolicy() == null
                ? normalizePolicyOrDefault(existing.getContactRequestPolicy())
                : normalizePolicy(setting.getContactRequestPolicy());
        if (Boolean.TRUE.equals(setting.getAcceptContactRequest())
                && setting.getContactRequestPolicy() == null
                && POLICY_OFF.equals(policy)) {
            policy = DEFAULT_POLICY;
        }
        if (!accept || POLICY_OFF.equals(policy)) {
            accept = false;
            policy = POLICY_OFF;
        }
        Integer dailyLimit = normalizeDailyLimit(setting.getContactRequestDailyLimit());
        return ContactRequestSettingsDTO.builder()
                .acceptContactRequest(accept)
                .contactRequestPolicy(policy)
                .contactRequestDailyLimit(dailyLimit)
                .build();
    }

    private static UserPrivacySettingPO withContactDefaults(UserPrivacySettingPO po) {
        if (po.getProfileVisibility() == null) po.setProfileVisibility("PUBLIC");
        if (po.getIntentVisibility() == null) po.setIntentVisibility("PUBLIC");
        if (po.getSearchable() == null) po.setSearchable(1);
        if (po.getInteractionNotification() == null) po.setInteractionNotification(1);
        if (po.getSystemNotification() == null) po.setSystemNotification(1);
        if (po.getLikeNotification() == null) po.setLikeNotification(1);
        if (po.getCommentNotification() == null) po.setCommentNotification(1);
        if (po.getFollowNotification() == null) po.setFollowNotification(1);
        if (po.getFavoriteNotification() == null) po.setFavoriteNotification(1);
        if (po.getMentionNotification() == null) po.setMentionNotification(1);
        if (po.getAcceptContactRequest() == null) po.setAcceptContactRequest(1);
        if (!StringUtils.hasText(po.getContactRequestPolicy())) po.setContactRequestPolicy(DEFAULT_POLICY);
        return po;
    }

    private static UserPrivacySettingPO defaultPrivacySetting(Long uid) {
        UserPrivacySettingPO po = new UserPrivacySettingPO();
        po.setUserId(uid);
        po.setProfileVisibility("PUBLIC");
        po.setIntentVisibility("PUBLIC");
        po.setSearchable(1);
        po.setInteractionNotification(1);
        po.setSystemNotification(1);
        po.setLikeNotification(1);
        po.setCommentNotification(1);
        po.setFollowNotification(1);
        po.setFavoriteNotification(1);
        po.setMentionNotification(1);
        po.setAcceptContactRequest(1);
        po.setContactRequestPolicy(DEFAULT_POLICY);
        po.setContactRequestDailyLimit(null);
        return po;
    }

    private static ContactRequestSettingsDTO toDTO(UserPrivacySettingPO po) {
        String policy = normalizePolicyOrDefault(po.getContactRequestPolicy());
        boolean accept = isEnabled(po.getAcceptContactRequest()) && !POLICY_OFF.equals(policy);
        return ContactRequestSettingsDTO.builder()
                .acceptContactRequest(accept)
                .contactRequestPolicy(accept ? policy : POLICY_OFF)
                .contactRequestDailyLimit(po.getContactRequestDailyLimit())
                .build();
    }

    private static String normalizePolicy(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_POLICY;
        }
        String policy = value.trim().toLowerCase(Locale.ROOT);
        if (!POLICIES.contains(policy)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return policy;
    }

    private static String normalizePolicyOrDefault(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_POLICY;
        }
        String policy = value.trim().toLowerCase(Locale.ROOT);
        return POLICIES.contains(policy) ? policy : DEFAULT_POLICY;
    }

    private static Integer normalizeDailyLimit(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < 1 || value > MAX_DAILY_LIMIT) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static ContactRequestPolicyCheckDTO denied(String code, String message, UserPrivacySettingPO setting) {
        String policy = setting == null ? null : normalizePolicyOrDefault(setting.getContactRequestPolicy());
        return ContactRequestPolicyCheckDTO.builder()
                .allowed(false)
                .reasonCode(code)
                .reasonMessage(message)
                .contactRequestPolicy(policy)
                .contactRequestDailyLimit(setting == null ? null : setting.getContactRequestDailyLimit())
                .build();
    }

    private static boolean validUid(Long uid) {
        return uid != null && uid > 0;
    }

    private static boolean isEnabled(Integer value) {
        return value == null || value == 1;
    }

    private static int toFlag(Boolean value) {
        return Boolean.FALSE.equals(value) ? 0 : 1;
    }
}

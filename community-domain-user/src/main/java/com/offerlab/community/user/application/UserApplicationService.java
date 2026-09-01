package com.offerlab.community.user.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.ExternalUrlSafety;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.infra.security.PasswordEncoder;
import com.offerlab.community.infra.tx.AfterCommitExecutor;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.NotificationPreferenceDTO;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import com.offerlab.community.user.api.dto.UserIntentDTO;
import com.offerlab.community.user.api.dto.UserPrivacySettingDTO;
import com.offerlab.community.user.api.event.UserFollowedEvent;
import com.offerlab.community.user.api.event.UserRegisteredEvent;
import com.offerlab.community.user.domain.model.User;
import com.offerlab.community.user.domain.repository.FollowRepository;
import com.offerlab.community.user.domain.repository.UserRepository;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserPrivacySettingMapper;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserProfileMapper;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserPublicContentMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserPrivacySettingPO;
import com.offerlab.community.user.infrastructure.persistence.po.UserProfilePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerlab.community.common.utils.SqlLimits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户应用服务：编排领域逻辑，事务边界
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserApplicationService {
    private static final Set<String> SYNTHETIC_MARKERS = Set.of(
            "E2E", "SMOKE", "CODEX", "TESTDATA", "TEST", "DEMO", "ACCEPTANCE",
            "ADMIN", "验收", "测试", "演示", "管理员"
    );
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final Duration LOGIN_FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOGIN_LOCK_TTL = Duration.ofMinutes(15);
    private static final String LOGIN_FAILURE_PREFIX = "auth:login:fail:";
    private static final String LOGIN_LOCK_PREFIX = "auth:login:lock:";
    public static final String CURRENT_TERMS_VERSION = "2026-09-01";
    public static final String CURRENT_PRIVACY_VERSION = "2026-09-01";

    private final UserRepository userRepo;
    private final FollowRepository followRepo;
    private final SnowflakeIdGenerator idGen;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final StringRedisTemplate redis;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final UserPrivacySettingMapper privacySettingMapper;
    private final UserProfileMapper profileMapper;
    private final UserCacheService userCacheService;
    private final ContentModerationService contentModerationService;
    private final AfterCommitExecutor afterCommit;
    private final AdminPermissionService adminPermissionService;
    private final UserPublicContentMapper publicContentMapper;

    @Transactional
    public Long register(String email, String password, String nickname) {
        return register(
                email,
                password,
                nickname,
                true,
                true,
                CURRENT_TERMS_VERSION,
                CURRENT_PRIVACY_VERSION);
    }

    @Transactional
    public Long register(String email,
                         String password,
                         String nickname,
                         boolean termsAccepted,
                         boolean privacyAccepted,
                         String termsVersion,
                         String privacyVersion) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password) || !StringUtils.hasText(nickname)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (!termsAccepted || !privacyAccepted) {
            throw new BizException(
                    ErrorCode.PARAM_ERROR.getCode(),
                    "请阅读并同意服务条款与隐私政策",
                    Map.of("fieldErrors", Map.of("agreements", "请阅读并同意服务条款与隐私政策")));
        }
        if (!CURRENT_TERMS_VERSION.equals(termsVersion)
                || !CURRENT_PRIVACY_VERSION.equals(privacyVersion)) {
            throw new BizException(
                    ErrorCode.PARAM_ERROR.getCode(),
                    "协议版本已更新，请重新确认",
                    Map.of("fieldErrors", Map.of("agreements", "协议版本已更新，请重新确认")));
        }
        if (!isValidPassword(password)) {
            throw new BizException(
                    ErrorCode.PARAM_ERROR.getCode(),
                    "密码至少 8 位，且需同时包含字母和数字",
                    Map.of("fieldErrors", Map.of("password", "密码至少 8 位，且需同时包含字母和数字")));
        }
        String normalizedEmail = normalizeEmail(email);
        String normalizedNickname = normalizeNickname(nickname);
        contentModerationService.requireContentAllowed(ContentModerationService.SCOPE_PROFILE, normalizedNickname);
        userRepo.findByEmail(normalizedEmail).ifPresent(u -> {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS);
        });

        long uid = idGen.nextId();
        User user = User.builder()
                .id(uid)
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(password))
                .nickname(normalizedNickname)
                .accountStatus(User.STATUS_NORMAL)
                .termsAcceptedAt(LocalDateTime.now())
                .termsVersion(CURRENT_TERMS_VERSION)
                .privacyVersion(CURRENT_PRIVACY_VERSION)
                .build();
        userRepo.register(user);
        eventPublisher.publish(UserRegisteredEvent.builder()
                .uid(uid)
                .timestamp(Instant.now().toEpochMilli())
                .build());
        log.info("user registered: uid={} email={}", uid, maskEmail(normalizedEmail));
        return uid;
    }

    public String login(String email, String password, String ip) {
        String normalizedEmail = normalizeEmail(email);
        requireLoginNotLocked(normalizedEmail);
        User user = userRepo.findByEmail(normalizedEmail)
                .or(() -> normalizedEmail.equals(email) ? java.util.Optional.empty() : userRepo.findByEmail(email))
                .orElse(null);
        if (user == null) {
            recordLoginFailure(normalizedEmail);
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }
        if (!user.isActive()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            recordLoginFailure(normalizedEmail);
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }
        clearLoginFailures(normalizedEmail);
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
        if (!isValidPassword(newPassword)
                || oldPassword.equals(newPassword)) {
            throw new BizException(
                    ErrorCode.PARAM_ERROR.getCode(),
                    "密码至少 8 位，且需同时包含字母和数字",
                    Map.of("fieldErrors", Map.of("newPassword", "密码至少 8 位，且需同时包含字母和数字")));
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
        afterCommit.execute(() -> userCacheService.evictBrief(fromUid, toUid),
                "follow cache eviction:" + fromUid + ":" + toUid);
    }

    @Transactional
    public void unfollow(Long fromUid, Long toUid) {
        boolean ok = followRepo.unfollow(fromUid, toUid);
        if (!ok) {
            throw new BizException(ErrorCode.FOLLOW_NOT_EXISTS);
        }
        afterCommit.execute(() -> userCacheService.evictBrief(fromUid, toUid),
                "unfollow cache eviction:" + fromUid + ":" + toUid);
    }

    public User getUser(Long uid) {
        return userRepo.findById(uid).orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    @Transactional
    public void updateProfile(Long uid, String nickname, String avatarUrl, String bio) {
        User u = getUser(uid);
        contentModerationService.requireUserCanPublish(uid);
        if (nickname != null) {
            u.setNickname(normalizeNickname(nickname));
        }
        if (avatarUrl != null) {
            u.setAvatarUrl(normalizeAvatarUrl(avatarUrl));
        }
        if (bio != null) {
            u.setBio(limitText(bio, 500, "bio"));
        }
        contentModerationService.requireContentAllowed(uid, ContentModerationService.SCOPE_PROFILE,
                u.getNickname(), u.getBio());
        userRepo.updateProfile(u);
        afterCommit.execute(() -> userCacheService.evictBrief(uid), "user profile cache eviction:" + uid);
    }

    private static boolean isValidPassword(String password) {
        return StringUtils.hasText(password)
                && password.length() >= 8
                && password.length() <= 64
                && password.chars().anyMatch(Character::isLetter)
                && password.chars().anyMatch(Character::isDigit);
    }

    private String normalizeEmail(String email) {
        String value = limitText(email, 254, "email");
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String normalizeNickname(String nickname) {
        String value = limitText(nickname, 32, "nickname");
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "nickname cannot be blank");
        }
        if (value.indexOf('<') >= 0 || value.indexOf('>') >= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "nickname contains invalid characters");
        }
        return value;
    }

    private String normalizeAvatarUrl(String avatarUrl) {
        String value = limitText(avatarUrl, 512, "avatarUrl");
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return ExternalUrlSafety.requireSafeHttpUrl(value, "avatarUrl", 512);
    }

    private String limitText(String value, int maxLength, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized != null && normalized.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), field + " is too long");
        }
        return normalized;
    }

    @Transactional
    public void updateIntent(Long uid, UserIntentDTO intent) {
        User u = getUser(uid);
        try {
            // 求职意向当前以 JSON 存在用户资料表，DTO 需保持字段兼容后再序列化。
            UserIntentDTO normalizedIntent = normalizeIntent(intent);
            u.setIntentJson(objectMapper.writeValueAsString(normalizedIntent));
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        userRepo.updateProfile(u);
        afterCommit.execute(() -> userCacheService.evictBrief(uid), "user intent cache eviction:" + uid);
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
    public NotificationPreferenceDTO getNotificationPreference(Long uid) {
        return toNotificationPreferenceDTO(loadOrCreatePrivacySetting(uid));
    }

    @Transactional
    public UserPrivacySettingDTO updatePrivacySetting(Long uid, UserPrivacySettingDTO setting) {
        getUser(uid);
        if (setting == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        UserPrivacySettingPO po = privacySettingMapper.selectById(uid);
        boolean exists = po != null;
        if (po == null) {
            // 允许用户在没有历史配置时直接保存，避免前端必须先调用 GET 初始化。
            po = defaultPrivacySetting(uid);
        }
        po.setProfileVisibility(normalizeVisibility(setting.getProfileVisibility(), po.getProfileVisibility()));
        po.setIntentVisibility(normalizeVisibility(setting.getIntentVisibility(), po.getIntentVisibility()));
        po.setSearchable(toFlag(setting.getSearchable()));
        po.setInteractionNotification(toFlag(setting.getInteractionNotification()));
        po.setSystemNotification(toFlag(setting.getSystemNotification()));
        po.setLikeNotification(toFlag(setting.getLikeNotification(), po.getLikeNotification()));
        po.setCommentNotification(toFlag(setting.getCommentNotification(), po.getCommentNotification()));
        po.setFollowNotification(toFlag(setting.getFollowNotification(), po.getFollowNotification()));
        po.setFavoriteNotification(toFlag(setting.getFavoriteNotification(), po.getFavoriteNotification()));
        po.setMentionNotification(toFlag(setting.getMentionNotification(), po.getMentionNotification()));
        if (exists) {
            privacySettingMapper.updateById(po);
        } else {
            privacySettingMapper.insert(po);
        }
        return toPrivacyDTO(po);
    }

    @Transactional
    public NotificationPreferenceDTO updateNotificationPreference(Long uid, NotificationPreferenceDTO setting) {
        getUser(uid);
        UserPrivacySettingPO po = privacySettingMapper.selectById(uid);
        boolean exists = po != null;
        if (po == null) {
            po = defaultPrivacySetting(uid);
        }
        po.setInteractionNotification(toFlag(setting == null ? null : setting.getInteractionNotification(), po.getInteractionNotification()));
        po.setSystemNotification(toFlag(setting == null ? null : setting.getSystemNotification(), po.getSystemNotification()));
        po.setLikeNotification(toFlag(setting == null ? null : setting.getLikeNotification(), po.getLikeNotification()));
        po.setCommentNotification(toFlag(setting == null ? null : setting.getCommentNotification(), po.getCommentNotification()));
        po.setFollowNotification(toFlag(setting == null ? null : setting.getFollowNotification(), po.getFollowNotification()));
        po.setFavoriteNotification(toFlag(setting == null ? null : setting.getFavoriteNotification(), po.getFavoriteNotification()));
        po.setMentionNotification(toFlag(setting == null ? null : setting.getMentionNotification(), po.getMentionNotification()));
        po.setGovernanceReminderNotification(toFlag(
                setting == null ? null : setting.getGovernanceReminderNotification(),
                po.getGovernanceReminderNotification()));
        po.setGovernanceReminderQuietStartMinute(keepExisting(
                setting == null ? null : setting.getGovernanceReminderQuietStartMinute(),
                po.getGovernanceReminderQuietStartMinute()));
        po.setGovernanceReminderQuietEndMinute(keepExisting(
                setting == null ? null : setting.getGovernanceReminderQuietEndMinute(),
                po.getGovernanceReminderQuietEndMinute()));
        po.setGovernanceReminderTimeZone(keepExisting(
                setting == null ? null : setting.getGovernanceReminderTimeZone(),
                po.getGovernanceReminderTimeZone()));
        if (po.getGovernanceReminderTimeZone() != null) {
            po.setGovernanceReminderTimeZone(po.getGovernanceReminderTimeZone().trim());
        }
        validateGovernanceReminderQuietWindow(po);
        if (exists) {
            privacySettingMapper.updateById(po);
        } else {
            privacySettingMapper.insert(po);
        }
        return toNotificationPreferenceDTO(po);
    }

    public List<UserBriefDTO> searchUsers(String keyword, Long viewerUid, int size, UserFacade userFacade) {
        int limit = Math.max(1, Math.min(size, 20));
        // 先放宽查询数量，再按隐私过滤并截断，避免少量受限用户占满结果页。
        LambdaQueryWrapper<UserProfilePO> query = new LambdaQueryWrapper<UserProfilePO>()
                .eq(UserProfilePO::getIsDeleted, 0)
                .orderByDesc(UserProfilePO::getUpdateTime)
                .last(SqlLimits.limit(limit * 3, 1, 60));
        if (StringUtils.hasText(keyword)) {
            query.like(UserProfilePO::getNickname, keyword.trim());
        }
        List<Long> candidateIds = profileMapper.selectList(query).stream()
                .map(UserProfilePO::getId)
                .toList();
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        Map<Long, UserPrivacySettingPO> settings = privacySettingMapper.selectBatchIds(candidateIds).stream()
                .collect(Collectors.toMap(UserPrivacySettingPO::getUserId, setting -> setting, (left, right) -> left));
        Map<Long, Boolean> following = viewerUid == null
                ? Map.of()
                : userFacade.batchIsFollowing(viewerUid, candidateIds);
        List<Long> discoverableIds = candidateIds.stream()
                .filter(uid -> isDiscoverableUser(viewerUid, uid, settings.get(uid), following.get(uid)))
                .filter(uid -> adminPermissionService == null || !adminPermissionService.isAdmin(uid))
                .toList();
        Map<Long, UserBriefDTO> users = userFacade.batchGetUserBriefs(discoverableIds);
        Map<Long, Long> publicPostCounts = publicPostCounts(discoverableIds);
        return discoverableIds.stream()
                .map(users::get)
                .filter(java.util.Objects::nonNull)
                .filter(user -> !isSyntheticUser(user))
                .peek(user -> user.setPostCount(publicPostCounts.getOrDefault(user.getUid(), 0L)))
                .limit(limit)
                .toList();
    }

    public void applyPublicPostCount(UserBriefDTO user) {
        if (user == null || user.getUid() == null) {
            return;
        }
        user.setPostCount(publicPostCounts(List.of(user.getUid()))
                .getOrDefault(user.getUid(), 0L));
    }

    private Map<Long, Long> publicPostCounts(List<Long> authorIds) {
        if (publicContentMapper == null || authorIds == null || authorIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = publicContentMapper.countPublicPostsByAuthors(authorIds);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream()
                .map(row -> new PublicPostCount(
                        asLong(firstPresent(row, "authorId", "author_id", "AUTHORID", "AUTHOR_ID")),
                        asLong(firstPresent(row, "postCount", "post_count", "POSTCOUNT", "POST_COUNT"))))
                .filter(count -> count.authorId() != null && count.postCount() != null)
                .collect(Collectors.toMap(PublicPostCount::authorId, PublicPostCount::postCount, Math::max));
    }

    private static Object firstPresent(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (row.containsKey(key) && row.get(key) != null) {
                return row.get(key);
            }
        }
        return null;
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private record PublicPostCount(Long authorId, Long postCount) {
    }

    private static boolean isDiscoverableUser(Long viewerUid,
                                              Long targetUid,
                                              UserPrivacySettingPO setting,
                                              Boolean viewerFollowsTarget) {
        if (targetUid == null || setting != null && Integer.valueOf(0).equals(setting.getSearchable())) {
            return false;
        }
        if (targetUid.equals(viewerUid)) {
            return true;
        }
        String visibility = setting == null || !StringUtils.hasText(setting.getProfileVisibility())
                ? "PUBLIC"
                : setting.getProfileVisibility().trim();
        if ("PRIVATE".equalsIgnoreCase(visibility)) {
            return false;
        }
        return !"FOLLOWERS".equalsIgnoreCase(visibility) || Boolean.TRUE.equals(viewerFollowsTarget);
    }

    private static boolean isSyntheticUser(UserBriefDTO user) {
        return containsSyntheticMarker(user.getNickname()) || containsSyntheticMarker(user.getBio());
    }

    private static boolean containsSyntheticMarker(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return SYNTHETIC_MARKERS.stream().anyMatch(upper::contains);
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
        po.setGovernanceReminderNotification(1);
        po.setAcceptContactRequest(1);
        po.setContactRequestPolicy(ContactRequestSettingsService.DEFAULT_POLICY);
        return po;
    }

    private UserPrivacySettingPO loadOrCreatePrivacySetting(Long uid) {
        getUser(uid);
        UserPrivacySettingPO po = privacySettingMapper.selectById(uid);
        if (po == null) {
            po = defaultPrivacySetting(uid);
            privacySettingMapper.insert(po);
        }
        return po;
    }

    private static UserPrivacySettingDTO toPrivacyDTO(UserPrivacySettingPO po) {
        return UserPrivacySettingDTO.builder()
                .profileVisibility(po.getProfileVisibility())
                .intentVisibility(po.getIntentVisibility())
                .searchable(isEnabled(po.getSearchable()))
                .interactionNotification(isEnabled(po.getInteractionNotification()))
                .systemNotification(isEnabled(po.getSystemNotification()))
                .likeNotification(isEnabled(po.getLikeNotification()))
                .commentNotification(isEnabled(po.getCommentNotification()))
                .followNotification(isEnabled(po.getFollowNotification()))
                .favoriteNotification(isEnabled(po.getFavoriteNotification()))
                .mentionNotification(isEnabled(po.getMentionNotification()))
                .build();
    }

    private static NotificationPreferenceDTO toNotificationPreferenceDTO(UserPrivacySettingPO po) {
        return NotificationPreferenceDTO.builder()
                .interactionNotification(isEnabled(po.getInteractionNotification()))
                .systemNotification(isEnabled(po.getSystemNotification()))
                .likeNotification(isEnabled(po.getLikeNotification()))
                .commentNotification(isEnabled(po.getCommentNotification()))
                .followNotification(isEnabled(po.getFollowNotification()))
                .favoriteNotification(isEnabled(po.getFavoriteNotification()))
                .mentionNotification(isEnabled(po.getMentionNotification()))
                .governanceReminderNotification(isGovernanceReminderEnabled(po))
                .governanceReminderQuietStartMinute(po.getGovernanceReminderQuietStartMinute())
                .governanceReminderQuietEndMinute(po.getGovernanceReminderQuietEndMinute())
                .governanceReminderTimeZone(po.getGovernanceReminderTimeZone())
                .build();
    }

    private static boolean isGovernanceReminderEnabled(UserPrivacySettingPO po) {
        return isEnabled(po.getSystemNotification()) && isEnabled(po.getGovernanceReminderNotification());
    }

    private static boolean isEnabled(Integer value) {
        return value == null || value == 1;
    }

    private static int toFlag(Boolean value) {
        return Boolean.FALSE.equals(value) ? 0 : 1;
    }

    private static int toFlag(Boolean value, Integer fallback) {
        return value == null ? (fallback == null ? 1 : fallback) : toFlag(value);
    }

    private static <T> T keepExisting(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private static void validateGovernanceReminderQuietWindow(UserPrivacySettingPO po) {
        Integer startMinute = po.getGovernanceReminderQuietStartMinute();
        Integer endMinute = po.getGovernanceReminderQuietEndMinute();
        String timeZone = po.getGovernanceReminderTimeZone();
        boolean empty = startMinute == null && endMinute == null && timeZone == null;
        if (empty) {
            return;
        }
        if (startMinute == null || endMinute == null || !StringUtils.hasText(timeZone)
                || startMinute < 0 || startMinute > 1439 || endMinute < 0 || endMinute > 1439) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        try {
            ZoneId.of(timeZone.trim());
        } catch (DateTimeException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static String normalizeVisibility(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return normalizeVisibility(fallback, "PUBLIC");
        }
        if ("PRIVATE".equalsIgnoreCase(value)) {
            return "PRIVATE";
        }
        if ("FOLLOWERS".equalsIgnoreCase(value)) {
            return "FOLLOWERS";
        }
        if ("PUBLIC".equalsIgnoreCase(value)) {
            return "PUBLIC";
        }
        throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid visibility");
    }

    private static UserIntentDTO normalizeIntent(UserIntentDTO intent) {
        if (intent == null) {
            return null;
        }
        return UserIntentDTO.builder()
                .targetCompanies(normalizeList(intent.getTargetCompanies()))
                .targetPositions(normalizeList(intent.getTargetPositions()))
                .yearsOfExp(intent.getYearsOfExp())
                .expectedCity(normalizeText(intent.getExpectedCity()))
                .techStack(normalizeList(intent.getTechStack()))
                .interestTopics(normalizeList(intent.getInterestTopics()))
                .interestTags(normalizeList(intent.getInterestTags()))
                .contentPreferences(normalizeList(intent.getContentPreferences()))
                .expectedSalaryRange(normalizeSalaryRange(intent.getExpectedSalaryRange()))
                .build();
    }

    private static UserIntentDTO.SalaryRange normalizeSalaryRange(UserIntentDTO.SalaryRange salaryRange) {
        if (salaryRange == null) {
            return null;
        }
        String unit = normalizeText(salaryRange.getUnit());
        if (salaryRange.getMin() == null && salaryRange.getMax() == null && unit == null) {
            return null;
        }
        return UserIntentDTO.SalaryRange.builder()
                .min(salaryRange.getMin())
                .max(salaryRange.getMax())
                .unit(unit)
                .build();
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> normalized = values.stream()
                .map(UserApplicationService::normalizeText)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(20)
                .toList();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void requireLoginNotLocked(String account) {
        String key = loginLockKey(account);
        try {
            if (Boolean.TRUE.equals(redis.hasKey(key))) {
                throw new BizException(ErrorCode.RATE_LIMIT_EXCEEDED);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("login guard unavailable while checking lock: accountRef={} reason={}",
                    LogMask.key(accountRef(account)), LogMask.message(e));
            throw new BizException(ErrorCode.CACHE_ERROR.getCode(), "auth guard unavailable");
        }
    }

    private void recordLoginFailure(String account) {
        String failureKey = loginFailureKey(account);
        try {
            Long count = redis.opsForValue().increment(failureKey);
            if (count != null && count == 1L) {
                redis.expire(failureKey, LOGIN_FAILURE_WINDOW);
            }
            if (count != null && count >= MAX_LOGIN_FAILURES) {
                redis.opsForValue().set(loginLockKey(account), "1", LOGIN_LOCK_TTL);
                redis.expire(failureKey, LOGIN_FAILURE_WINDOW);
                log.warn("login account temporarily locked after repeated failures: accountRef={} failures={}",
                        LogMask.key(accountRef(account)), count);
            }
        } catch (Exception e) {
            log.error("login guard unavailable while recording failure: accountRef={} reason={}",
                    LogMask.key(accountRef(account)), LogMask.message(e));
            throw new BizException(ErrorCode.CACHE_ERROR.getCode(), "auth guard unavailable");
        }
    }

    private void clearLoginFailures(String account) {
        try {
            redis.delete(List.of(loginFailureKey(account), loginLockKey(account)));
        } catch (Exception e) {
            log.warn("login guard cleanup failed after successful login: accountRef={} reason={}",
                    LogMask.key(accountRef(account)), LogMask.message(e));
        }
    }

    private static String loginFailureKey(String account) {
        return LOGIN_FAILURE_PREFIX + accountRef(account);
    }

    private static String loginLockKey(String account) {
        return LOGIN_LOCK_PREFIX + accountRef(account);
    }

    private static String accountRef(String account) {
        String normalized = account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", bytes[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(normalized.hashCode());
        }
    }
}

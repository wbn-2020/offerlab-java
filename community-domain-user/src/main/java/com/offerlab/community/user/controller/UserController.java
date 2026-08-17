package com.offerlab.community.user.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.FollowCursorDTO;
import com.offerlab.community.user.api.dto.ContactRequestPolicyCheckDTO;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import com.offerlab.community.user.api.dto.UserIntentDTO;
import com.offerlab.community.user.api.dto.UserPrivacySettingDTO;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.user.api.dto.ContactRequestSettingsDTO;
import com.offerlab.community.user.application.UserApplicationService;
import com.offerlab.community.user.application.ContactRequestSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserFacade userFacade;
    private final UserApplicationService userService;
    private final ContactRequestSettingsService contactRequestSettingsService;

    @GetMapping("/{uid}")
    @PublicApi
    @RateLimit(key = "'public:user:detail:' + #uid + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<UserBriefDTO> getUser(@PathVariable Long uid,
                                        HttpServletRequest request) {
        UserBriefDTO dto = copyBrief(userFacade.getUserBrief(uid));
        if (dto != null) {
            userService.applyPublicPostCount(dto);
            Long viewer = UserContext.get();
            if (viewer != null && !viewer.equals(uid)) {
                dto.setIsFollowing(userFacade.isFollowing(viewer, uid));
            }
            // 公开接口允许匿名访问，但返回前必须按访问者身份裁剪隐私字段。
            boolean profileVisible = userFacade.isProfileVisible(viewer, uid);
            dto.setProfileVisible(profileVisible);
            dto.setIntentVisible(userFacade.isIntentVisible(viewer, uid));
            if (!profileVisible) {
                dto.setNickname("");
                dto.setAvatarUrl("");
                dto.setBio("");
                dto.setFollowerCount(0L);
                dto.setFollowingCount(0L);
                dto.setPostCount(0L);
                dto.setPrivacyReason("PROFILE_RESTRICTED");
                dto.setAcceptContactRequest(false);
                dto.setContactRequestPolicy("off");
                dto.setCanStartContactRequest(false);
                dto.setContactRequestReasonCode("PROFILE_RESTRICTED");
                dto.setContactRequestReasonMessage("PROFILE_RESTRICTED");
            } else {
                applyContactRequestPolicy(dto, viewer, uid);
            }
        }
        return Result.ok(dto);
    }

    @GetMapping("/me")
    public Result<UserBriefDTO> getMe() {
        Long uid = UserContext.require();
        UserBriefDTO dto = copyBrief(userFacade.getUserBrief(uid));
        userService.applyPublicPostCount(dto);
        return Result.ok(dto);
    }

    @PatchMapping("/me")
    @RateLimit(key = "'user:profile:update:' + #uid", rate = 30, per = 60)
    public Result<Void> updateMe(@Valid @RequestBody UpdateProfileReq req) {
        Long uid = UserContext.require();
        userService.updateProfile(uid, req.getNickname(), req.getAvatarUrl(), req.effectiveBio());
        return Result.ok();
    }

    @PutMapping("/me/password")
    @RateLimit(key = "'user:password:' + #uid", rate = 5, per = 3600)
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        userService.changePassword(UserContext.require(), req.getOldPassword(), req.getNewPassword());
        return Result.ok();
    }

    @PostMapping("/me/logout-all")
    @RateLimit(key = "'user:logout-all:' + #uid", rate = 5, per = 300)
    public Result<Void> logoutAll() {
        userService.logoutAll(UserContext.require());
        return Result.ok();
    }

    @PutMapping("/me/intent")
    @RateLimit(key = "'user:intent:update:' + #uid", rate = 30, per = 60)
    public Result<Void> updateIntent(@Valid @RequestBody UserIntentDTO intent) {
        Long uid = UserContext.require();
        // UserIntentDTO 同时兼容 targetCity/expectedCity，避免旧前端保存后丢城市字段。
        userService.updateIntent(uid, intent);
        return Result.ok();
    }

    @PublicApi
    @GetMapping("/{uid}/intent")
    @RateLimit(key = "'public:user:intent:' + #uid + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<UserIntentDTO> getIntent(@PathVariable Long uid,
                                           HttpServletRequest request) {
        return Result.ok(userFacade.isIntentVisible(UserContext.get(), uid) ? userFacade.getUserIntent(uid) : null);
    }

    @PublicApi
    @GetMapping("/search")
    @RateLimit(key = "'public:user:search:' + #request.remoteAddr", rate = 60, per = 60, failOpen = false)
    public Result<List<UserBriefDTO>> searchUsers(@RequestParam(name = "q", required = false) @Size(max = 80) String keyword,
                                                  @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size,
                                                  HttpServletRequest request) {
        return Result.ok(userService.searchUsers(keyword, UserContext.get(), size, userFacade));
    }

    @GetMapping("/me/privacy-settings")
    public Result<UserPrivacySettingDTO> getPrivacySettings() {
        Long uid = UserContext.require();
        return Result.ok(userService.getPrivacySetting(uid));
    }

    @PutMapping("/me/privacy-settings")
    @RateLimit(key = "'user:privacy:update:' + #uid", rate = 30, per = 60)
    public Result<UserPrivacySettingDTO> updatePrivacySettings(@Valid @RequestBody UserPrivacySettingDTO setting) {
        Long uid = UserContext.require();
        return Result.ok(userService.updatePrivacySetting(uid, setting));
    }

    @GetMapping("/me/contact-request-settings")
    public Result<ContactRequestSettingsDTO> getContactRequestSettings() {
        Long uid = UserContext.require();
        return Result.ok(contactRequestSettingsService.getSettings(uid));
    }

    @PutMapping("/me/contact-request-settings")
    @RateLimit(key = "'user:contact-request-settings:update:' + #uid", rate = 30, per = 60)
    public Result<ContactRequestSettingsDTO> updateContactRequestSettings(@RequestBody ContactRequestSettingsDTO setting) {
        Long uid = UserContext.require();
        return Result.ok(contactRequestSettingsService.updateSettings(uid, setting));
    }

    @PostMapping("/{uid}/follow")
    @RateLimit(key = "'user:follow:' + #uid", rate = 60, per = 60)
    public Result<Void> follow(@PathVariable Long uid) {
        userService.follow(UserContext.require(), uid);
        return Result.ok();
    }

    @DeleteMapping("/{uid}/follow")
    @RateLimit(key = "'user:unfollow:' + #uid", rate = 60, per = 60)
    public Result<Void> unfollow(@PathVariable Long uid) {
        userService.unfollow(UserContext.require(), uid);
        return Result.ok();
    }

    @PublicApi
    @GetMapping("/{uid}/followers")
    @RateLimit(key = "'public:user:followers:' + #uid + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<UserBriefDTO>> followers(@PathVariable Long uid,
                                                      @RequestParam(defaultValue = "0") long cursor,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      HttpServletRequest request) {
        Long viewer = UserContext.get();
        if (!userFacade.isProfileVisible(viewer, uid)) {
            return Result.ok(PageResult.empty());
        }
        int limit = pageSize(size);
        return Result.ok(toFollowPage(userFacade.getFollowerPage(uid, cursor, limit + 1), limit, viewer));
    }

    @PublicApi
    @GetMapping("/{uid}/following")
    @RateLimit(key = "'public:user:following:' + #uid + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<UserBriefDTO>> following(@PathVariable Long uid,
                                                      @RequestParam(defaultValue = "0") long cursor,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      HttpServletRequest request) {
        Long viewer = UserContext.get();
        if (!userFacade.isProfileVisible(viewer, uid)) {
            return Result.ok(PageResult.empty());
        }
        int limit = pageSize(size);
        return Result.ok(toFollowPage(userFacade.getFollowingPage(uid, cursor, limit + 1), limit, viewer));
    }

    private PageResult<UserBriefDTO> toFollowPage(List<FollowCursorDTO> rows, int limit, Long viewer) {
        if (rows.isEmpty()) return PageResult.empty();
        boolean hasMore = rows.size() > limit;
        List<FollowCursorDTO> pageRows = hasMore ? rows.subList(0, limit) : rows;
        List<Long> uids = pageRows.stream().map(FollowCursorDTO::getUid).toList();
        Map<Long, UserBriefDTO> briefs = userFacade.batchGetUserBriefs(uids);
        Map<Long, Boolean> following = viewer == null
                ? Map.of()
                : userFacade.batchIsFollowing(viewer, uids);
        List<UserBriefDTO> items = pageRows.stream()
                .map(FollowCursorDTO::getUid)
                .map(briefs::get)
                .map(dto -> sanitizeFollowBrief(dto, viewer, following))
                .filter(java.util.Objects::nonNull)
                .toList();
        String next = hasMore && !pageRows.isEmpty()
                ? String.valueOf(pageRows.get(pageRows.size() - 1).getRelationId())
                : null;
        return PageResult.of(items, next, hasMore);
    }

    private int pageSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private UserBriefDTO sanitizeFollowBrief(UserBriefDTO dto, Long viewer) {
        return sanitizeFollowBrief(dto, viewer, Map.of());
    }

    private UserBriefDTO sanitizeFollowBrief(UserBriefDTO dto, Long viewer, Map<Long, Boolean> followingByUid) {
        UserBriefDTO copy = copyBrief(dto);
        if (copy == null) {
            return null;
        }
        Long targetUid = copy.getUid();
        if (viewer != null && targetUid != null && !viewer.equals(targetUid)) {
            Boolean isFollowing = followingByUid == null ? null : followingByUid.get(targetUid);
            copy.setIsFollowing(isFollowing != null ? isFollowing : userFacade.isFollowing(viewer, targetUid));
        }
        boolean profileVisible = userFacade.isProfileVisible(viewer, targetUid);
        copy.setProfileVisible(profileVisible);
        copy.setIntentVisible(userFacade.isIntentVisible(viewer, targetUid));
        if (!profileVisible) {
            copy.setNickname("");
            copy.setAvatarUrl("");
            copy.setBio("");
            copy.setFollowerCount(0L);
            copy.setFollowingCount(0L);
            copy.setPostCount(0L);
            copy.setPrivacyReason("PROFILE_RESTRICTED");
            copy.setAcceptContactRequest(false);
            copy.setContactRequestPolicy("off");
            copy.setCanStartContactRequest(false);
            copy.setContactRequestReasonCode("PROFILE_RESTRICTED");
            copy.setContactRequestReasonMessage("PROFILE_RESTRICTED");
        } else {
            applyContactRequestPolicy(copy, viewer, targetUid);
        }
        return copy;
    }

    private void applyContactRequestPolicy(UserBriefDTO dto, Long viewer, Long targetUid) {
        if (dto == null || targetUid == null) {
            return;
        }
        if (viewer == null) {
            dto.setCanStartContactRequest(false);
            dto.setContactRequestReasonCode("LOGIN_REQUIRED");
            dto.setContactRequestReasonMessage("LOGIN_REQUIRED");
            return;
        }
        if (viewer.equals(targetUid)) {
            dto.setCanStartContactRequest(false);
            dto.setContactRequestReasonCode("SELF_CONTACT");
            dto.setContactRequestReasonMessage("SELF_CONTACT");
            return;
        }
        ContactRequestPolicyCheckDTO policy = contactRequestSettingsService.checkPolicy(viewer, targetUid);
        dto.setCanStartContactRequest(Boolean.TRUE.equals(policy.getAllowed()));
        dto.setContactRequestReasonCode(policy.getReasonCode());
        dto.setContactRequestReasonMessage(policy.getReasonMessage());
        dto.setContactRequestPolicy(policy.getContactRequestPolicy());
        dto.setAcceptContactRequest(Boolean.TRUE.equals(policy.getAllowed())
                || !"CONTACT_REQUEST_CLOSED".equals(policy.getReasonCode()));
    }

    private UserBriefDTO copyBrief(UserBriefDTO dto) {
        if (dto == null) {
            return null;
        }
        return UserBriefDTO.builder()
                .uid(dto.getUid())
                .nickname(dto.getNickname())
                .avatarUrl(dto.getAvatarUrl())
                .bio(dto.getBio())
                .followerCount(dto.getFollowerCount())
                .followingCount(dto.getFollowingCount())
                .postCount(dto.getPostCount())
                .isFollowing(dto.getIsFollowing())
                .profileVisible(dto.getProfileVisible())
                .intentVisible(dto.getIntentVisible())
                .privacyReason(dto.getPrivacyReason())
                .acceptContactRequest(dto.getAcceptContactRequest())
                .contactRequestPolicy(dto.getContactRequestPolicy())
                .canStartContactRequest(dto.getCanStartContactRequest())
                .contactRequestReasonCode(dto.getContactRequestReasonCode())
                .contactRequestReasonMessage(dto.getContactRequestReasonMessage())
                .build();
    }

    @Data
    public static class UpdateProfileReq {
        @Size(min = 1, max = 32)
        private String nickname;

        @Size(max = 512)
        private String avatarUrl;

        @Size(max = 500)
        private String bio;

        @Size(max = 500)
        private String signature;

        private String effectiveBio() {
            // signature 是早期前端字段名，新旧字段同时出现时以 bio 为准。
            return bio != null ? bio : signature;
        }
    }

    @Data
    public static class ChangePasswordReq {
        @NotBlank
        private String oldPassword;
        @NotBlank
        @Size(min = 6, max = 64)
        private String newPassword;
    }
}

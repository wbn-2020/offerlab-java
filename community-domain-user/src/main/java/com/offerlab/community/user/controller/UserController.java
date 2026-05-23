package com.offerlab.community.user.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import com.offerlab.community.user.api.dto.UserIntentDTO;
import com.offerlab.community.user.api.dto.UserPrivacySettingDTO;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.user.application.UserApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserFacade userFacade;
    private final UserApplicationService userService;

    @GetMapping("/{uid}")
    @PublicApi
    public Result<UserBriefDTO> getUser(@PathVariable Long uid) {
        UserBriefDTO dto = copyBrief(userFacade.getUserBrief(uid));
        if (dto != null) {
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
            }
        }
        return Result.ok(dto);
    }

    @GetMapping("/me")
    public Result<UserBriefDTO> getMe() {
        Long uid = UserContext.require();
        return Result.ok(userFacade.getUserBrief(uid));
    }

    @PatchMapping("/me")
    public Result<Void> updateMe(@Valid @RequestBody UpdateProfileReq req) {
        Long uid = UserContext.require();
        userService.updateProfile(uid, req.getNickname(), req.getAvatarUrl(), req.effectiveBio());
        return Result.ok();
    }

    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        userService.changePassword(UserContext.require(), req.getOldPassword(), req.getNewPassword());
        return Result.ok();
    }

    @PostMapping("/me/logout-all")
    public Result<Void> logoutAll() {
        userService.logoutAll(UserContext.require());
        return Result.ok();
    }

    @PutMapping("/me/intent")
    public Result<Void> updateIntent(@RequestBody UserIntentDTO intent) {
        Long uid = UserContext.require();
        // UserIntentDTO 同时兼容 targetCity/expectedCity，避免旧前端保存后丢城市字段。
        userService.updateIntent(uid, intent);
        return Result.ok();
    }

    @PublicApi
    @GetMapping("/{uid}/intent")
    public Result<UserIntentDTO> getIntent(@PathVariable Long uid) {
        return Result.ok(userFacade.isIntentVisible(UserContext.get(), uid) ? userFacade.getUserIntent(uid) : null);
    }

    @PublicApi
    @GetMapping("/search")
    public Result<List<UserBriefDTO>> searchUsers(@RequestParam(name = "q", required = false) String keyword,
                                                  @RequestParam(defaultValue = "10") int size) {
        return Result.ok(userService.searchUsers(keyword, UserContext.get(), size, userFacade));
    }

    @GetMapping("/me/privacy-settings")
    public Result<UserPrivacySettingDTO> getPrivacySettings() {
        Long uid = UserContext.require();
        return Result.ok(userService.getPrivacySetting(uid));
    }

    @PutMapping("/me/privacy-settings")
    public Result<UserPrivacySettingDTO> updatePrivacySettings(@RequestBody UserPrivacySettingDTO setting) {
        Long uid = UserContext.require();
        return Result.ok(userService.updatePrivacySetting(uid, setting));
    }

    @PostMapping("/{uid}/follow")
    public Result<Void> follow(@PathVariable Long uid) {
        userService.follow(UserContext.require(), uid);
        return Result.ok();
    }

    @DeleteMapping("/{uid}/follow")
    public Result<Void> unfollow(@PathVariable Long uid) {
        userService.unfollow(UserContext.require(), uid);
        return Result.ok();
    }

    @GetMapping("/{uid}/followers")
    public Result<PageResult<UserBriefDTO>> followers(@PathVariable Long uid,
                                                     @RequestParam(defaultValue = "0") long cursor,
                                                     @RequestParam(defaultValue = "20") int size) {
        return Result.ok(toPage(userFacade.getFollowerIds(uid, cursor, size), size));
    }

    @GetMapping("/{uid}/following")
    public Result<PageResult<UserBriefDTO>> following(@PathVariable Long uid,
                                                     @RequestParam(defaultValue = "0") long cursor,
                                                     @RequestParam(defaultValue = "20") int size) {
        return Result.ok(toPage(userFacade.getFollowingIds(uid, cursor, size), size));
    }

    private PageResult<UserBriefDTO> toPage(List<Long> ids, int size) {
        if (ids.isEmpty()) return PageResult.empty();
        List<UserBriefDTO> items = ids.stream()
                .map(userFacade::getUserBrief)
                .filter(java.util.Objects::nonNull)
                .toList();
        // 关注列表游标使用最后一个 uid，前端需把 nextCursor 原样带回。
        boolean hasMore = ids.size() == size;
        String next = hasMore ? String.valueOf(ids.get(ids.size() - 1)) : null;
        return PageResult.of(items, next, hasMore);
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
                .build();
    }

    @Data
    public static class UpdateProfileReq {
        private String nickname;
        private String avatarUrl;
        private String bio;
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

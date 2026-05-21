package com.offerlab.community.user.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import com.offerlab.community.user.api.dto.UserIntentDTO;
import com.offerlab.community.user.application.UserApplicationService;
import jakarta.validation.Valid;
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
    public Result<UserBriefDTO> getUser(@PathVariable Long uid) {
        UserBriefDTO dto = userFacade.getUserBrief(uid);
        if (dto != null) {
            Long viewer = UserContext.get();
            if (viewer != null && !viewer.equals(uid)) {
                dto.setIsFollowing(userFacade.isFollowing(viewer, uid));
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

    @PutMapping("/me/intent")
    public Result<Void> updateIntent(@RequestBody UserIntentDTO intent) {
        Long uid = UserContext.require();
        userService.updateIntent(uid, intent);
        return Result.ok();
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
        boolean hasMore = ids.size() == size;
        String next = hasMore ? String.valueOf(ids.get(ids.size() - 1)) : null;
        return PageResult.of(items, next, hasMore);
    }

    @Data
    public static class UpdateProfileReq {
        private String nickname;
        private String avatarUrl;
        private String bio;
        private String signature;

        private String effectiveBio() {
            return bio != null ? bio : signature;
        }
    }
}

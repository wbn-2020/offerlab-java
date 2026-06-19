package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.dto.CommunityTopicCmd;
import com.offerlab.community.post.api.dto.CommunityTopicDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.application.CommunityTopicService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class CommunityTopicController {

    private final CommunityTopicService topicService;
    private final AdminPermissionService adminPermissionService;

    @PublicApi
    @GetMapping
    public Result<List<CommunityTopicDTO>> list(@RequestParam(required = false) Boolean featured,
                                                @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(topicService.listPublic(featured, limit, UserContext.get()));
    }

    @GetMapping("/me/following")
    public Result<PageResult<CommunityTopicDTO>> followingTopics(@RequestParam(defaultValue = "0") long cursor,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return Result.ok(topicService.listFollowingTopics(UserContext.require(), cursor, size));
    }

    @PublicApi
    @GetMapping("/{slug}")
    public Result<CommunityTopicDTO> detail(@PathVariable String slug) {
        return Result.ok(topicService.getPublic(slug, UserContext.get()));
    }

    @GetMapping("/{slug}/follow-status")
    public Result<CommunityTopicDTO> followStatus(@PathVariable String slug) {
        return Result.ok(topicService.followStatus(slug, UserContext.require()));
    }

    @PostMapping("/{slug}/follow")
    @RateLimit(key = "'topic:follow:' + #slug", rate = 60, per = 60)
    public Result<CommunityTopicDTO> follow(@PathVariable String slug) {
        return Result.ok(topicService.follow(slug, UserContext.require()));
    }

    @DeleteMapping("/{slug}/follow")
    @RateLimit(key = "'topic:unfollow:' + #slug", rate = 60, per = 60)
    public Result<CommunityTopicDTO> unfollow(@PathVariable String slug) {
        return Result.ok(topicService.unfollow(slug, UserContext.require()));
    }

    @PublicApi
    @GetMapping("/{slug}/posts")
    public Result<PageResult<PostBriefDTO>> posts(@PathVariable String slug,
                                                  @RequestParam(required = false, name = "type") Integer type,
                                                  @RequestParam(required = false) Boolean featured,
                                                  @RequestParam(defaultValue = "0") long cursor,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.ok(topicService.listPosts(slug, type, featured, cursor, size, UserContext.get()));
    }

    @GetMapping("/admin")
    public Result<List<CommunityTopicDTO>> adminList(@RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "50") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(topicService.listAdmin(status, keyword, limit));
    }

    @GetMapping("/admin/{topicId}")
    public Result<CommunityTopicDTO> adminDetail(@PathVariable Long topicId) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(topicService.getAdmin(topicId));
    }

    @PostMapping("/admin")
    public Result<CommunityTopicDTO> create(@Valid @RequestBody CommunityTopicCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(topicService.create(cmd, uid));
    }

    @PutMapping("/admin/{topicId}")
    public Result<CommunityTopicDTO> update(@PathVariable Long topicId,
                                            @Valid @RequestBody CommunityTopicCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(topicService.update(topicId, cmd, uid));
    }

    @PostMapping("/admin/{topicId}/status")
    public Result<CommunityTopicDTO> updateStatus(@PathVariable Long topicId,
                                                  @Valid @RequestBody StatusReq req) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(topicService.updateStatus(topicId, req.getStatus(), uid, req.getNote()));
    }

    @Data
    public static class StatusReq {
        private Integer status;
        @Size(max = 500)
        private String note;
    }
}

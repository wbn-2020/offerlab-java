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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
@Validated
public class CommunityTopicController {

    private final CommunityTopicService topicService;
    private final AdminPermissionService adminPermissionService;

    @PublicApi
    @GetMapping
    @RateLimit(key = "'public:topics:list:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<List<CommunityTopicDTO>> list(@RequestParam(required = false) Boolean featured,
                                                @RequestParam(required = false) @Size(max = 80) String keyword,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
                                                HttpServletRequest request) {
        return Result.ok(topicService.listPublic(featured, keyword, limit, UserContext.get()));
    }

    @GetMapping("/me/following")
    public Result<PageResult<CommunityTopicDTO>> followingTopics(@RequestParam(defaultValue = "0") long cursor,
                                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(topicService.listFollowingTopics(UserContext.require(), cursor, size));
    }

    @PublicApi
    @GetMapping("/{slug}")
    @RateLimit(key = "'public:topics:detail:' + #slug + ':' + #request.remoteAddr", rate = 180, per = 60, failOpen = false)
    public Result<CommunityTopicDTO> detail(@PathVariable String slug,
                                             HttpServletRequest request) {
        return Result.ok(topicService.getPublic(slug, UserContext.get()));
    }

    @GetMapping("/{slug}/follow-status")
    public Result<CommunityTopicDTO> followStatus(@PathVariable String slug) {
        return Result.ok(topicService.followStatus(slug, UserContext.require()));
    }

    @PostMapping("/{slug}/follow")
    @RateLimit(key = "'topic:follow:' + #uid + ':' + #slug", rate = 60, per = 60)
    public Result<CommunityTopicDTO> follow(@PathVariable String slug) {
        Long uid = UserContext.require();
        return Result.ok(topicService.follow(slug, uid));
    }

    @DeleteMapping("/{slug}/follow")
    @RateLimit(key = "'topic:unfollow:' + #uid + ':' + #slug", rate = 60, per = 60)
    public Result<CommunityTopicDTO> unfollow(@PathVariable String slug) {
        Long uid = UserContext.require();
        return Result.ok(topicService.unfollow(slug, uid));
    }

    @PublicApi
    @GetMapping("/{slug}/posts")
    @RateLimit(key = "'public:topics:posts:' + #slug + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<PostBriefDTO>> posts(@PathVariable String slug,
                                                   @RequestParam(required = false, name = "type") Integer type,
                                                   @RequestParam(required = false) Boolean featured,
                                                   @RequestParam(defaultValue = "0") long cursor,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
                                                   HttpServletRequest request) {
        return Result.ok(topicService.listPosts(slug, type, featured, cursor, size, UserContext.get()));
    }

    @GetMapping("/admin")
    public Result<List<CommunityTopicDTO>> adminList(@RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) @Size(max = 80) String keyword,
                                                    @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
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

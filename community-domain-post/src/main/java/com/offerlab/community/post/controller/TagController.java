package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.api.dto.TagGovernanceCmd;
import com.offerlab.community.post.application.TagGovernanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Validated
public class TagController {

    private final PostFacade postFacade;
    private final TagGovernanceService tagGovernanceService;
    private final AdminPermissionService adminPermissionService;

    @PublicApi
    @GetMapping
    @RateLimit(key = "'public:tags:list:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<List<TagDTO>> list(HttpServletRequest request) {
        return Result.ok(postFacade.listTags());
    }

    @GetMapping("/admin")
    public Result<List<TagDTO>> adminList(@RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) Boolean recommended,
                                           @RequestParam(required = false) @Size(max = 80) String keyword,
                                           @RequestParam(defaultValue = "80") @Min(1) @Max(100) int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(tagGovernanceService.list(status, recommended, keyword, limit));
    }

    @PutMapping("/admin/{tagId}")
    public Result<TagDTO> adminUpdate(@PathVariable Long tagId,
                                      @Valid @RequestBody TagGovernanceCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(tagGovernanceService.update(tagId, cmd, uid));
    }

    @PostMapping("/admin/{tagId}/status")
    public Result<TagDTO> adminUpdateStatus(@PathVariable Long tagId,
                                            @Valid @RequestBody TagGovernanceCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(tagGovernanceService.updateStatus(tagId, cmd.getStatus(), uid, cmd.getNote()));
    }

    @PostMapping("/admin/{tagId}/recommend")
    public Result<TagDTO> adminUpdateRecommended(@PathVariable Long tagId,
                                                 @Valid @RequestBody TagGovernanceCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(tagGovernanceService.updateRecommended(tagId, cmd.getRecommended(), uid, cmd.getNote()));
    }

    @PostMapping("/admin/{tagId}/synonyms")
    public Result<TagDTO> adminUpdateSynonyms(@PathVariable Long tagId,
                                              @Valid @RequestBody TagGovernanceCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(tagGovernanceService.updateSynonyms(tagId, cmd.getSynonyms(), uid, cmd.getNote()));
    }

    @PostMapping("/admin/{tagId}/merge")
    public Result<TagDTO> adminMerge(@PathVariable Long tagId,
                                     @Valid @RequestBody TagGovernanceCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(tagGovernanceService.merge(tagId, cmd.getMergeTargetId(), uid,
                cmd.getNote(), cmd.getConfirmationPhrase()));
    }

    @PublicApi
    @GetMapping("/{tagId}/posts")
    @RateLimit(key = "'public:tag:posts:' + #tagId + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<PostBriefDTO>> posts(@PathVariable Long tagId,
                                                   @RequestParam(required = false, name = "type") Integer type,
                                                   @RequestParam(required = false) Boolean featured,
                                                   @RequestParam(defaultValue = "0") long cursor,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
                                                   HttpServletRequest request) {
        return Result.ok(postFacade.getPostsByTag(tagId, type, featured, cursor, size));
    }
}

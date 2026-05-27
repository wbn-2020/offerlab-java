package com.offerlab.community.post.controller;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostReportDTO;
import com.offerlab.community.post.api.dto.PostUpdateCmd;
import com.offerlab.community.post.application.PostApplicationService;
import com.offerlab.community.post.application.PostReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostFacade postFacade;
    private final PostApplicationService postService;
    private final PostReportService reportService;
    private final AdminPermissionService adminPermissionService;
    private final ContentModerationService contentModerationService;

    @PostMapping
    @RateLimit(key = "'post:create:' + #uid", rate = 20, per = 86400)
    public Result<Map<String, Long>> publish(@Valid @RequestBody PublishReq req) {
        Long uid = UserContext.require();
        contentModerationService.requireUserCanPublish(uid);
        contentModerationService.requireContentAllowed(ContentModerationService.SCOPE_POST, req.getTitle(), req.getContent());
        Long id = postFacade.publishPost(PostCreateCmd.builder()
                .authorId(uid)
                .postType(req.getPostType())
                .title(req.getTitle())
                .content(req.getContent())
                .coverUrl(req.getCoverUrl())
                .visibility(req.getVisibility())
                .extJson(req.getExtJson())
                .tagIds(req.effectiveTagIds())
                .tagNames(req.getTagNames())
                .build());
        return Result.ok(Map.of("postId", id));
    }

    @PutMapping("/{postId}")
    @RateLimit(key = "'post:update:' + #uid", rate = 30, per = 300)
    public Result<Void> update(@PathVariable Long postId, @Valid @RequestBody UpdateReq req) {
        if (req == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Long uid = UserContext.require();
        contentModerationService.requireUserCanPublish(uid);
        contentModerationService.requireContentAllowed(ContentModerationService.SCOPE_POST, req.getTitle(), req.getContent());
        // 更新后只返回成功状态；详情接口会按可见性重新拉取，避免私密帖被匿名视角误判为空。
        postFacade.updatePost(PostUpdateCmd.builder()
                .postId(postId)
                .operatorUid(uid)
                .title(req.getTitle())
                .content(req.getContent())
                .coverUrl(req.getCoverUrl())
                .visibility(req.getVisibility())
                .extJson(req.getExtJson())
                .tagIds(req.effectiveTagIds())
                .tagNames(req.getTagNames())
                .build());
        return Result.ok();
    }

    @DeleteMapping("/{postId}")
    @RateLimit(key = "'post:delete:' + #uid", rate = 20, per = 300)
    public Result<Void> delete(@PathVariable Long postId) {
        postFacade.deletePost(postId, UserContext.require());
        return Result.ok();
    }

    @PublicApi
    @GetMapping("/{postId}")
    public Result<PostDTO> get(@PathVariable Long postId) {
        PostDTO p = postFacade.getPost(postId);
        // 只有实际可见的帖子才计浏览，避免不存在或不可见内容污染计数。
        if (p != null) {
            postService.incrView(postId);
        }
        return Result.ok(p);
    }

    @PublicApi
    @GetMapping
    public Result<PageResult<PostBriefDTO>> list(@RequestParam(required = false) Long authorId,
                                                 @RequestParam(required = false) Long tagId,
                                                 @RequestParam(required = false, name = "tag") Long tag,
                                                 @RequestParam(required = false, name = "type") Integer type,
                                                 @RequestParam(defaultValue = "0") long cursor,
                                                 @RequestParam(defaultValue = "20") int size) {
        Long effectiveTagId = tagId != null ? tagId : tag;
        return Result.ok(postFacade.listPosts(authorId, effectiveTagId, type, cursor, size));
    }

    @PostMapping("/{postId}/reports")
    @RateLimit(key = "'post:report:' + #postId + ':' + #uid", rate = 10, per = 86400)
    public Result<Map<String, Long>> report(@PathVariable Long postId, @Valid @RequestBody ReportReq req) {
        // 举报写入后进入管理员审核流；这里仅返回 reportId，审核动作由 admin 接口处理。
        Long reportId = reportService.reportPost(postId, UserContext.require(), req.getReason(), req.getDetail());
        return Result.ok(Map.of("reportId", reportId));
    }

    @GetMapping("/admin/reports")
    public Result<List<PostReportDTO>> listReports(@RequestParam(required = false) Integer status,
                                                   @RequestParam(defaultValue = "20") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(reportService.listRecent(status, limit));
    }

    @PostMapping("/admin/reports/{reportId}/review")
    public Result<PostReportDTO> reviewReport(@PathVariable Long reportId, @Valid @RequestBody ReviewReq req) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        // 前端可能传 approved/status/action 任一形式，resolveApproved 统一成审核布尔值。
        return Result.ok(reportService.reviewReport(reportId, uid, req.resolveApproved(), req.getNote()));
    }

    @Data
    public static class PublishReq {
        @NotNull
        private Integer postType;
        @NotBlank
        @Size(max = 255)
        private String title;
        @NotBlank
        private String content;
        private String coverUrl;
        private Integer visibility;
        private String extJson;
        private List<Long> tags;
        private List<Long> tagIds;
        private List<String> tagNames;

        private List<Long> effectiveTagIds() {
            // tags 是早期请求字段，tagIds 是当前字段；保留兼容避免旧草稿发布失败。
            return tagIds != null ? tagIds : tags;
        }
    }

    @Data
    public static class UpdateReq {
        @Size(max = 255)
        private String title;
        @Size(max = 20000)
        private String content;
        @Size(max = 512)
        private String coverUrl;
        private Integer visibility;
        @Size(max = 20000)
        private String extJson;
        private List<Long> tags;
        private List<Long> tagIds;
        private List<String> tagNames;

        private List<Long> effectiveTagIds() {
            // tags 是早期请求字段，tagIds 是当前字段；保留兼容避免旧编辑页提交失败。
            return tagIds != null ? tagIds : tags;
        }
    }

    @Data
    public static class ReportReq {
        @NotBlank
        @Size(max = 64)
        private String reason;
        @Size(max = 1000)
        private String detail;
    }

    @Data
    public static class ReviewReq {
        private Boolean approved;
        private Integer status;
        private String action;
        @Size(max = 1000)
        private String note;

        private Boolean resolveApproved() {
            if (approved != null) {
                return approved;
            }
            if (status != null) {
                if (status == PostReportService.STATUS_APPROVED) {
                    return true;
                }
                if (status == PostReportService.STATUS_REJECTED) {
                    return false;
                }
            }
            if (action == null) {
                return null;
            }
            String normalized = action.trim().toUpperCase();
            if ("APPROVE".equals(normalized) || "APPROVED".equals(normalized) || "PASS".equals(normalized)) {
                return true;
            }
            if ("REJECT".equals(normalized) || "REJECTED".equals(normalized) || "DISMISS".equals(normalized)) {
                return false;
            }
            return null;
        }
    }
}

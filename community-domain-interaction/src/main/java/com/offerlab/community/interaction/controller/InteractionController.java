package com.offerlab.community.interaction.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.interaction.api.InteractionFacade;
import com.offerlab.community.interaction.api.dto.CommentCreateCmd;
import com.offerlab.community.interaction.api.dto.CommentDTO;
import com.offerlab.community.interaction.api.dto.CommentReportDTO;
import com.offerlab.community.interaction.application.CommentReportService;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class InteractionController {

    private final InteractionFacade facade;
    private final CommentReportService reportService;
    private final AdminPermissionService adminPermissionService;
    private final ContentModerationService contentModerationService;

    @PostMapping("/posts/{postId}/like")
    @RateLimit(key = "'like:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> like(@PathVariable Long postId) {
        Long uid = UserContext.require();
        facade.like(uid, postId);
        return Result.ok(Map.of("liked", true));
    }

    @DeleteMapping("/posts/{postId}/like")
    public Result<Map<String, Object>> unlike(@PathVariable Long postId) {
        Long uid = UserContext.require();
        facade.unlike(uid, postId);
        return Result.ok(Map.of("liked", false));
    }

    @PublicApi
    @GetMapping("/posts/{postId}/interaction")
    public Result<Map<String, Object>> postInteraction(@PathVariable Long postId) {
        Long uid = UserContext.get();
        // 匿名访问时固定返回 false，前端可直接渲染未互动状态而不必额外判断登录态。
        return Result.ok(Map.of(
                "liked", uid != null && facade.hasLiked(uid, postId),
                "favorited", uid != null && facade.hasFavorited(uid, postId)
        ));
    }

    @GetMapping("/users/me/liked-posts")
    public Result<PageResult<PostBriefDTO>> likedPosts(@RequestParam(defaultValue = "0") long cursor,
                                                       @RequestParam(defaultValue = "20") int size) {
        return Result.ok(facade.listLikedPosts(UserContext.require(), cursor, size));
    }

    @PostMapping("/posts/{postId}/favorite")
    public Result<Map<String, Object>> favorite(@PathVariable Long postId) {
        // 返回 favorited 与点赞接口保持一致，便于前端乐观更新后校正状态。
        facade.favorite(UserContext.require(), postId);
        return Result.ok(Map.of("favorited", true));
    }

    @DeleteMapping("/posts/{postId}/favorite")
    public Result<Map<String, Object>> unfavorite(@PathVariable Long postId) {
        // 返回 favorited 与点赞接口保持一致，便于前端乐观更新后校正状态。
        facade.unfavorite(UserContext.require(), postId);
        return Result.ok(Map.of("favorited", false));
    }

    @GetMapping("/users/me/favorite-posts")
    public Result<PageResult<PostBriefDTO>> favoritePosts(@RequestParam(defaultValue = "0") long cursor,
                                                         @RequestParam(defaultValue = "20") int size) {
        return Result.ok(facade.listFavoritePosts(UserContext.require(), cursor, size));
    }

    @PostMapping("/posts/{postId}/comments")
    @RateLimit(key = "'comment:' + #uid", rate = 30, per = 60)
    public Result<Map<String, Long>> comment(@PathVariable Long postId, @Valid @RequestBody CommentReq req) {
        Long uid = UserContext.require();
        contentModerationService.requireUserCanPublish(uid);
        contentModerationService.requireContentAllowed(ContentModerationService.SCOPE_COMMENT, req.getContent());
        // parentId/replyToUid 同时传入时表示楼中楼回复，领域层负责归并根评论关系。
        Long id = facade.addComment(CommentCreateCmd.builder()
                .postId(postId)
                .authorUid(uid)
                .parentId(req.getParentId())
                .replyToUid(req.getReplyToUid())
                .content(req.getContent())
                .build());
        return Result.ok(Map.of("commentId", id));
    }

    @PublicApi
    @GetMapping("/posts/{postId}/comments")
    public Result<PageResult<CommentDTO>> comments(@PathVariable Long postId,
                                                   @RequestParam(defaultValue = "0") long cursor,
                                                   @RequestParam(defaultValue = "20") int size) {
        return Result.ok(facade.listComments(postId, UserContext.get(), cursor, size));
    }

    @DeleteMapping("/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        facade.deleteComment(commentId, UserContext.require());
        return Result.ok();
    }

    @PostMapping("/comments/{commentId}/reports")
    @RateLimit(key = "'comment:report:' + #commentId + ':' + #uid", rate = 10, per = 86400)
    public Result<Map<String, Long>> reportComment(@PathVariable Long commentId, @Valid @RequestBody ReportReq req) {
        Long reportId = reportService.reportComment(commentId, UserContext.require(), req.getReason(), req.getDetail());
        return Result.ok(Map.of("reportId", reportId));
    }

    @GetMapping("/comments/admin/reports")
    public Result<List<CommentReportDTO>> listCommentReports(@RequestParam(required = false) Integer status,
                                                             @RequestParam(defaultValue = "20") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(reportService.listRecent(status, limit));
    }

    @PostMapping("/comments/admin/reports/{reportId}/review")
    public Result<CommentReportDTO> reviewCommentReport(@PathVariable Long reportId, @Valid @RequestBody ReviewReq req) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        // 前端可能传 approved/status/action 任一形式，resolveApproved 统一成审核布尔值。
        return Result.ok(reportService.reviewReport(reportId, uid, req.resolveApproved(), req.getNote()));
    }

    @PostMapping("/comments/{commentId}/like")
    public Result<Map<String, Object>> likeComment(@PathVariable Long commentId) {
        facade.likeComment(UserContext.require(), commentId);
        return Result.ok(Map.of("liked", true));
    }

    @DeleteMapping("/comments/{commentId}/like")
    public Result<Map<String, Object>> unlikeComment(@PathVariable Long commentId) {
        facade.unlikeComment(UserContext.require(), commentId);
        return Result.ok(Map.of("liked", false));
    }

    @Data
    public static class CommentReq {
        private Long parentId;
        private Long replyToUid;
        @NotBlank
        @Size(max = 2000)
        private String content;
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
                if (status == CommentReportService.STATUS_APPROVED) {
                    return true;
                }
                if (status == CommentReportService.STATUS_REJECTED) {
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

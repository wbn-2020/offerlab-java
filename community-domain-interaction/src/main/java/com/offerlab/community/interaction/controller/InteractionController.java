package com.offerlab.community.interaction.controller;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.interaction.api.DiscussionFollowFacade;
import com.offerlab.community.interaction.api.InteractionFacade;
import com.offerlab.community.interaction.api.dto.CommentCreateCmd;
import com.offerlab.community.interaction.api.dto.CommentDTO;
import com.offerlab.community.interaction.api.dto.CommentReportDTO;
import com.offerlab.community.interaction.api.dto.DiscussionFollowStatusDTO;
import com.offerlab.community.interaction.api.dto.FavoriteFolderCreateCmd;
import com.offerlab.community.interaction.api.dto.FavoriteFolderDTO;
import com.offerlab.community.interaction.api.dto.FavoriteFolderSortCmd;
import com.offerlab.community.interaction.api.dto.FavoriteFolderUpdateCmd;
import com.offerlab.community.interaction.api.dto.FavoriteBatchMoveCmd;
import com.offerlab.community.interaction.api.dto.FavoriteMoveCmd;
import com.offerlab.community.interaction.application.CommentReportService;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.application.DomainModeratorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
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
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Validated
public class InteractionController {

    private final InteractionFacade facade;
    private final PostFacade postFacade;
    private final DiscussionFollowFacade discussionFollowFacade;
    private final CommentReportService reportService;
    private final DomainModeratorService domainModeratorService;
    private final ContentModerationService contentModerationService;
    private final SnowflakeIdGenerator idGen;

    @PostMapping("/posts/{postId}/like")
    @RateLimit(key = "'like:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> like(@PathVariable Long postId) {
        Long uid = UserContext.require();
        facade.like(uid, postId);
        return Result.ok(Map.of("liked", true));
    }

    @DeleteMapping("/posts/{postId}/like")
    @RateLimit(key = "'unlike:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> unlike(@PathVariable Long postId) {
        Long uid = UserContext.require();
        facade.unlike(uid, postId);
        return Result.ok(Map.of("liked", false));
    }

    @PublicApi
    @GetMapping("/posts/{postId}/interaction")
    @RateLimit(key = "'public:post:interaction:' + #postId + ':' + #request.remoteAddr", rate = 300, per = 60, failOpen = false)
    public Result<Map<String, Object>> postInteraction(@PathVariable @Positive Long postId,
                                                       HttpServletRequest request) {
        Long uid = UserContext.get();
        PostDTO post = postFacade.getPost(postId, uid);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        // 匿名访问时固定返回 false，前端可直接渲染未互动状态而不必额外判断登录态。
        return Result.ok(Map.of(
                "liked", uid != null && facade.hasLiked(uid, postId),
                "favorited", uid != null && facade.hasFavorited(uid, postId)
        ));
    }

    @GetMapping("/users/me/liked-posts")
    public Result<PageResult<PostBriefDTO>> likedPosts(@RequestParam(defaultValue = "0") String cursor,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(facade.listLikedPosts(UserContext.require(), cursor, size));
    }

    @PostMapping("/posts/{postId}/favorite")
    @RateLimit(key = "'favorite:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> favorite(@PathVariable Long postId,
                                                @RequestBody(required = false) FavoriteMoveCmd req) {
        // 返回 favorited 与点赞接口保持一致，便于前端乐观更新后校正状态。
        facade.favorite(UserContext.require(), postId, req == null ? null : req.getFolderId());
        return Result.ok(Map.of("favorited", true));
    }

    @DeleteMapping("/posts/{postId}/favorite")
    @RateLimit(key = "'unfavorite:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> unfavorite(@PathVariable Long postId) {
        // 返回 favorited 与点赞接口保持一致，便于前端乐观更新后校正状态。
        facade.unfavorite(UserContext.require(), postId);
        return Result.ok(Map.of("favorited", false));
    }

    @GetMapping("/users/me/favorite-posts")
    public Result<PageResult<PostBriefDTO>> favoritePosts(@RequestParam(defaultValue = "0") String cursor,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(facade.listFavoritePosts(UserContext.require(), cursor, size));
    }

    @GetMapping("/users/me/favorite-folders")
    @RateLimit(key = "'favorite-folder:list:' + #uid", rate = 120, per = 60)
    public Result<List<FavoriteFolderDTO>> favoriteFolders() {
        return Result.ok(facade.listFavoriteFolders(UserContext.require()));
    }

    @PostMapping("/users/me/favorite-folders")
    @RateLimit(key = "'favorite-folder:create:' + #uid", rate = 30, per = 60)
    public Result<FavoriteFolderDTO> createFavoriteFolder(@Valid @RequestBody FavoriteFolderCreateCmd cmd) {
        return Result.ok(facade.createFavoriteFolder(UserContext.require(), cmd));
    }

    @PutMapping("/users/me/favorite-folders/{folderId}")
    @RateLimit(key = "'favorite-folder:update:' + #uid", rate = 60, per = 60)
    public Result<FavoriteFolderDTO> updateFavoriteFolder(@PathVariable Long folderId,
                                                          @Valid @RequestBody FavoriteFolderUpdateCmd cmd) {
        return Result.ok(facade.updateFavoriteFolder(UserContext.require(), folderId, cmd));
    }

    @PostMapping("/users/me/favorite-folders/{folderId}/sort")
    @RateLimit(key = "'favorite-folder:sort:' + #uid", rate = 60, per = 60)
    public Result<FavoriteFolderDTO> sortFavoriteFolder(@PathVariable @Positive Long folderId,
                                                        @Valid @RequestBody FavoriteFolderSortCmd cmd) {
        return Result.ok(facade.sortFavoriteFolder(UserContext.require(), folderId, cmd));
    }

    @DeleteMapping("/users/me/favorite-folders/{folderId}")
    @RateLimit(key = "'favorite-folder:delete:' + #uid", rate = 30, per = 60)
    public Result<Void> deleteFavoriteFolder(@PathVariable Long folderId,
                                             @RequestParam(required = false) Long targetFolderId) {
        facade.deleteFavoriteFolder(UserContext.require(), folderId, targetFolderId);
        return Result.ok();
    }

    @GetMapping("/users/me/favorite-folders/{folderId}/posts")
    public Result<PageResult<PostBriefDTO>> favoriteFolderPosts(@PathVariable @Positive Long folderId,
                                                               @RequestParam(defaultValue = "0") String cursor,
                                                               @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(facade.listFavoritePostsInFolder(UserContext.require(), folderId, cursor, size));
    }

    @PutMapping("/users/me/favorites/{postId}/folder")
    @RateLimit(key = "'favorite:move:' + #uid", rate = 60, per = 60)
    public Result<FavoriteFolderDTO> moveFavorite(@PathVariable Long postId,
                                                  @RequestBody(required = false) FavoriteMoveCmd cmd) {
        return Result.ok(facade.moveFavorite(UserContext.require(), postId, cmd));
    }

    @PutMapping("/users/me/favorites/batch-folder")
    @RateLimit(key = "'favorite:batch-move:' + #uid", rate = 20, per = 60)
    public Result<FavoriteFolderDTO> batchMoveFavorites(@Valid @RequestBody FavoriteBatchMoveCmd cmd) {
        return Result.ok(facade.batchMoveFavorites(UserContext.require(), cmd));
    }

    @PostMapping("/users/me/favorites/batch-move")
    @RateLimit(key = "'favorite:batch-move:' + #uid", rate = 20, per = 60)
    public Result<FavoriteFolderDTO> batchMoveFavoritesByDocumentedPath(@Valid @RequestBody FavoriteBatchMoveCmd cmd) {
        return Result.ok(facade.batchMoveFavorites(UserContext.require(), cmd));
    }

    @PublicApi
    @GetMapping("/favorite-folders/{folderId}")
    @RateLimit(key = "'public:favorite-folder:detail:' + #folderId + ':' + #request.remoteAddr", rate = 180, per = 60, failOpen = false)
    public Result<FavoriteFolderDTO> publicFavoriteFolder(@PathVariable @Positive Long folderId,
                                                          HttpServletRequest request) {
        return Result.ok(facade.getPublicFavoriteFolder(folderId));
    }

    @PublicApi
    @GetMapping("/users/{uid}/favorite-folders")
    @RateLimit(key = "'public:favorite-folder:user:' + #uid + ':' + #request.remoteAddr", rate = 180, per = 60, failOpen = false)
    public Result<List<FavoriteFolderDTO>> publicFavoriteFoldersByUser(@PathVariable @Positive Long uid,
                                                                       @RequestParam(defaultValue = "6") @Min(1) @Max(20) int limit,
                                                                       HttpServletRequest request) {
        return Result.ok(facade.listPublicFavoriteFoldersByUser(uid, limit));
    }

    @PublicApi
    @GetMapping("/favorite-folders/{folderId}/posts")
    @RateLimit(key = "'public:favorite-folder:posts:' + #folderId + ':' + #request.remoteAddr", rate = 180, per = 60, failOpen = false)
    public Result<PageResult<PostBriefDTO>> publicFavoriteFolderPosts(@PathVariable @Positive Long folderId,
                                                                      @RequestParam(defaultValue = "0") String cursor,
                                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
                                                                      HttpServletRequest request) {
        return Result.ok(facade.listPublicFavoritePostsInFolder(folderId, cursor, size));
    }

    @PublicApi
    @GetMapping("/posts/{postId}/discussion-follow")
    @RateLimit(key = "'public:discussion-follow:status:' + #postId + ':' + #request.remoteAddr", rate = 300, per = 60, failOpen = false)
    public Result<DiscussionFollowStatusDTO> discussionFollowStatus(@PathVariable @Positive Long postId,
                                                                    HttpServletRequest request) {
        return Result.ok(discussionFollowFacade.status(UserContext.get(), postId));
    }

    @PostMapping("/posts/{postId}/discussion-follow")
    @RateLimit(key = "'discussion-follow:' + #uid", rate = 60, per = 60)
    public Result<DiscussionFollowStatusDTO> followDiscussion(@PathVariable Long postId) {
        return Result.ok(discussionFollowFacade.follow(UserContext.require(), postId));
    }

    @DeleteMapping("/posts/{postId}/discussion-follow")
    @RateLimit(key = "'discussion-unfollow:' + #uid", rate = 60, per = 60)
    public Result<DiscussionFollowStatusDTO> unfollowDiscussion(@PathVariable Long postId) {
        return Result.ok(discussionFollowFacade.unfollow(UserContext.require(), postId));
    }

    @GetMapping("/users/me/discussion-follows")
    public Result<PageResult<PostBriefDTO>> discussionFollows(@RequestParam(defaultValue = "0") @Min(0) long cursor,
                                                              @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(discussionFollowFacade.listFollowedPosts(UserContext.require(), cursor, size));
    }

    @PostMapping("/posts/{postId}/comments")
    @RateLimit(key = "'comment:' + #uid", rate = 30, per = 60)
    public Result<Map<String, Object>> comment(@PathVariable Long postId, @Valid @RequestBody CommentReq req) {
        Long uid = UserContext.require();
        contentModerationService.requireUserCanPublish(uid);
        Long id = idGen.nextId();
        ContentModerationService.ModerationDecision moderationDecision = contentModerationService.checkContent(
                uid, ContentModerationService.SCOPE_COMMENT, ContentModerationService.SOURCE_COMMENT, id,
                req.getContent());
        // parentId/replyToUid 同时传入时表示楼中楼回复，领域层负责归并根评论关系。
        facade.addComment(CommentCreateCmd.builder()
                .commentId(id)
                .postId(postId)
                .authorUid(uid)
                .parentId(req.getParentId())
                .replyToUid(req.getReplyToUid())
                .content(req.getContent())
                .reviewRequired(moderationDecision.reviewRequired())
                .build());
        return Result.ok(Map.of("commentId", id, "reviewRequired", moderationDecision.reviewRequired()));
    }

    @PublicApi
    @GetMapping("/posts/{postId}/comments")
    @RateLimit(key = "'public:comments:list:' + #postId + ':' + #request.remoteAddr", rate = 240, per = 60, failOpen = false)
    public Result<PageResult<CommentDTO>> comments(@PathVariable @Positive Long postId,
                                                   @RequestParam(defaultValue = "0") String cursor,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
                                                   @RequestParam(defaultValue = "latest") @Size(max = 16) String sort,
                                                   HttpServletRequest request) {
        return Result.ok(facade.listComments(postId, UserContext.get(), cursor, size, sort));
    }

    @PublicApi
    @GetMapping("/posts/{postId}/comments/{rootId}/replies")
    @RateLimit(key = "'public:comments:replies:' + #postId + ':' + #rootId + ':' + #request.remoteAddr", rate = 240, per = 60, failOpen = false)
    public Result<PageResult<CommentDTO>> commentReplies(@PathVariable @Positive Long postId,
                                                        @PathVariable @Positive Long rootId,
                                                        @RequestParam(defaultValue = "0") String cursor,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
                                                        HttpServletRequest request) {
        return Result.ok(facade.listCommentReplies(postId, rootId, UserContext.get(), cursor, size));
    }

    @DeleteMapping("/comments/{commentId}")
    @RateLimit(key = "'comment:delete:' + #uid", rate = 30, per = 60)
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

    @GetMapping("/comments/reports/me")
    @RateLimit(key = "'comment-report:list:me:' + #uid", rate = 120, per = 60)
    public Result<List<CommentReportDTO>> listMyCommentReports(@RequestParam(required = false) Integer status,
                                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return Result.ok(reportService.listUserReports(UserContext.require(), status, limit));
    }

    @GetMapping("/comments/reports/{reportId}")
    public Result<CommentReportDTO> getMyCommentReport(@PathVariable Long reportId) {
        return Result.ok(reportService.getUserReport(reportId, UserContext.require()));
    }

    @GetMapping("/comments/admin/reports")
    @RateLimit(key = "'comment-report:list:admin:' + #uid", rate = 120, per = 60)
    public Result<List<CommentReportDTO>> listCommentReports(@RequestParam(required = false) Integer status,
                                                             @RequestParam(required = false) Integer domain,
                                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                                                             @RequestParam(defaultValue = "false") boolean includeTestData) {
        domainModeratorService.requireModerateDomain(UserContext.require(), domain);
        return Result.ok(reportService.listRecent(status, domain, limit, includeTestData));
    }

    @PostMapping("/comments/admin/reports/{reportId}/review")
    @RateLimit(key = "'comment-report:review:' + #uid", rate = 60, per = 60)
    public Result<CommentReportDTO> reviewCommentReport(@PathVariable Long reportId, @Valid @RequestBody ReviewReq req) {
        Long uid = UserContext.require();
        // 前端可能传 approved/status/action 任一形式，resolveApproved 统一成审核布尔值。
        return Result.ok(reportService.reviewReport(reportId, uid, req.resolveApproved(), req.getNote()));
    }

    @PostMapping("/comments/{commentId}/like")
    @RateLimit(key = "'comment:like:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> likeComment(@PathVariable Long commentId) {
        facade.likeComment(UserContext.require(), commentId);
        return Result.ok(Map.of("liked", true));
    }

    @DeleteMapping("/comments/{commentId}/like")
    @RateLimit(key = "'comment:unlike:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> unlikeComment(@PathVariable Long commentId) {
        facade.unlikeComment(UserContext.require(), commentId);
        return Result.ok(Map.of("liked", false));
    }

    @PostMapping("/comments/{commentId}/helpful")
    @RateLimit(key = "'comment:helpful:' + #commentId + ':' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> markCommentHelpful(@PathVariable Long commentId) {
        facade.markCommentHelpful(UserContext.require(), commentId);
        return Result.ok(Map.of("helpful", true));
    }

    @DeleteMapping("/comments/{commentId}/helpful")
    @RateLimit(key = "'comment:unhelpful:' + #commentId + ':' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> unmarkCommentHelpful(@PathVariable Long commentId) {
        facade.unmarkCommentHelpful(UserContext.require(), commentId);
        return Result.ok(Map.of("helpful", false));
    }

    @PostMapping("/posts/{postId}/comments/{commentId}/pin")
    @RateLimit(key = "'comment:pin:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> pinComment(@PathVariable Long postId, @PathVariable Long commentId) {
        facade.pinComment(UserContext.require(), postId, commentId);
        return Result.ok(Map.of("pinned", true));
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}/pin")
    @RateLimit(key = "'comment:unpin:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> unpinComment(@PathVariable Long postId, @PathVariable Long commentId) {
        facade.unpinComment(UserContext.require(), postId, commentId);
        return Result.ok(Map.of("pinned", false));
    }

    @PostMapping("/comments/{commentId}/featured")
    @RateLimit(key = "'comment:feature:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> featureComment(@PathVariable Long commentId) {
        facade.featureComment(UserContext.require(), commentId);
        return Result.ok(Map.of("featured", true));
    }

    @DeleteMapping("/comments/{commentId}/featured")
    @RateLimit(key = "'comment:unfeature:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> unfeatureComment(@PathVariable Long commentId) {
        facade.unfeatureComment(UserContext.require(), commentId);
        return Result.ok(Map.of("featured", false));
    }

    @PostMapping("/comments/{commentId}/fold")
    @RateLimit(key = "'comment:fold:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> foldComment(@PathVariable Long commentId,
                                                   @RequestBody(required = false) FoldReq req) {
        facade.foldComment(UserContext.require(), commentId, req == null ? null : req.getReason());
        return Result.ok(Map.of("folded", true));
    }

    @DeleteMapping("/comments/{commentId}/fold")
    @RateLimit(key = "'comment:unfold:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> unfoldComment(@PathVariable Long commentId) {
        facade.unfoldComment(UserContext.require(), commentId);
        return Result.ok(Map.of("folded", false));
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
    public static class FoldReq {
        @Size(max = 255)
        private String reason;
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

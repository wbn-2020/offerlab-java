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
import com.offerlab.community.post.api.dto.DomainModeratorDTO;
import com.offerlab.community.post.api.dto.PostContentLimits;
import com.offerlab.community.post.api.dto.PostContentTypeDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostReportDTO;
import com.offerlab.community.post.api.dto.PostUpdateCmd;
import com.offerlab.community.post.api.dto.PostVersionHistoryDTO;
import com.offerlab.community.post.api.event.PublicPostViewedEvent;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.application.PostApplicationService;
import com.offerlab.community.post.application.PostDraftService;
import com.offerlab.community.post.application.PostFeaturedService;
import com.offerlab.community.post.application.PostKnowledgeReviewService;
import com.offerlab.community.post.application.PostReportService;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.model.PostDomain;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final PostFeaturedService featuredService;
    private final PostKnowledgeReviewService knowledgeReviewService;
    private final PostDraftService draftService;
    private final DomainModeratorService domainModeratorService;
    private final AdminPermissionService adminPermissionService;
    private final ContentModerationService contentModerationService;
    private final ApplicationEventPublisher applicationEventPublisher;

    private static final List<PostContentTypeDTO> CONTENT_TYPES = List.of(
            new PostContentTypeDTO(Post.TYPE_TECH_ARTICLE, "TECH_ARTICLE", "技术文章", "文章",
                    "沉淀架构设计、技术方案、源码阅读和工程实践。", "例如：Spring Cloud Gateway 鉴权链路实践", 40, false),
            new PostContentTypeDTO(Post.TYPE_NOTE, "NOTE", "经验分享", "经验",
                    "分享亲身经历、过程、踩坑、结果和可复用的做法。", "例如：我如何用两周时间调整作息并稳定完成学习计划", 30, false),
            new PostContentTypeDTO(Post.TYPE_COMMUNITY_QUESTION, "QUESTION", "问题求助", "求助",
                    "提出具体问题，补充背景、已尝试方法和期待获得的建议。", "例如：第一次租房看房时，哪些细节最容易被忽略？", 30, false),
            new PostContentTypeDTO(Post.TYPE_RESOURCE, "RESOURCE", "资源推荐", "资源",
                    "推荐工具、网站、书单、课程、模板、资料和使用建议。", "例如：我常用的 8 个免费效率工具和适合场景", 30, false),
            new PostContentTypeDTO(Post.TYPE_SYSTEM_DESIGN, "SYSTEM_DESIGN", "观点讨论", "讨论",
                    "表达观点、提出判断、分享观察，并邀请大家一起讨论。", "例如：远程办公真正考验的是自我管理还是团队协作？", 40, false),
            new PostContentTypeDTO(Post.TYPE_PROJECT_REVIEW, "PROJECT_REVIEW", "复盘记录", "复盘",
                    "复盘一次项目、活动、经历或决策，记录背景、过程、结果和下一步。", "例如：第一次组织线下读书会后的完整复盘", 60, false),
            new PostContentTypeDTO(Post.TYPE_PITFALL, "PITFALL", "图文笔记", "笔记",
                    "轻量记录灵感、日常观察、实用片段和图文式分享。", "例如：这周让我效率变高的 5 个小习惯", 30, false),
            new PostContentTypeDTO(Post.TYPE_INTERVIEW_RECAP, "INTERVIEW_RECAP", "面试复盘", "面试",
                    "职场经验频道保留的面试复盘类型，用于兼容历史内容和直达链路。", "例如：某次产品运营岗位面试后的表达复盘", 80, true),
            new PostContentTypeDTO(Post.TYPE_INTERVIEW, "LEGACY_INTERVIEW", "历史经验", "旧经验",
                    "旧版经验类型，保留给历史数据和知识卡链路。", "例如：某主题 Java 后端复盘", 120, true),
            new PostContentTypeDTO(Post.TYPE_BLOG, "LEGACY_BLOG", "技术博客", "博客",
                    "旧版技术博客类型。", "例如：Spring 事务传播机制总结", 40, true),
            new PostContentTypeDTO(Post.TYPE_SOLUTION, "LEGACY_SOLUTION", "题解", "题解",
                    "旧版题解类型。", "例如：一道并发题的解法整理", 40, true),
            new PostContentTypeDTO(Post.TYPE_QA, "LEGACY_QA", "历史问答", "问答",
                    "旧版问答类型。", "例如：如何梳理一个技术问题的上下文？", 40, true)
    );

    @PostMapping
    @RateLimit(key = "'post:create:' + #uid", rate = 20, per = 86400)
    public Result<Map<String, Object>> publish(@Valid @RequestBody PublishReq req) {
        Long uid = UserContext.require();
        Integer domain = requireOptionalDomain(req.getDomain());
        contentModerationService.requireUserCanPublish(uid);
        ContentModerationService.ModerationDecision moderationDecision = contentModerationService.checkContent(
                uid, ContentModerationService.SCOPE_POST, req.getTitle(), req.getContent());
        Long id = postFacade.publishPost(PostCreateCmd.builder()
                .authorId(uid)
                .postType(req.getPostType())
                .domain(domain)
                .title(req.getTitle())
                .content(req.getContent())
                .coverUrl(req.getCoverUrl())
                .visibility(req.getVisibility())
                .extJson(req.getExtJson())
                .tagIds(req.effectiveTagIds())
                .tagNames(req.getTagNames())
                .anonymous(req.getAnonymous())
                .reviewRequired(moderationDecision.reviewRequired())
                .build());
        draftService.deleteIfOwned(uid, req.getDraftId());
        return Result.ok(Map.of("postId", id, "reviewRequired", moderationDecision.reviewRequired()));
    }

    @PutMapping("/{postId}")
    @RateLimit(key = "'post:update:' + #uid", rate = 30, per = 300)
    public Result<Map<String, Object>> update(@PathVariable Long postId, @Valid @RequestBody UpdateReq req) {
        if (req == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Long uid = UserContext.require();
        Integer domain = requireOptionalDomain(req.getDomain());
        contentModerationService.requireUserCanPublish(uid);
        ContentModerationService.ModerationDecision moderationDecision = contentModerationService.checkContent(
                uid, ContentModerationService.SCOPE_POST, req.getTitle(), req.getContent());
        // 更新后只返回成功状态；详情接口会按可见性重新拉取，避免私密帖被匿名视角误判为空。
        postFacade.updatePost(PostUpdateCmd.builder()
                .postId(postId)
                .operatorUid(uid)
                .title(req.getTitle())
                .content(req.getContent())
                .domain(domain)
                .coverUrl(req.getCoverUrl())
                .visibility(req.getVisibility())
                .extJson(req.getExtJson())
                .tagIds(req.effectiveTagIds())
                .tagNames(req.getTagNames())
                .anonymous(req.getAnonymous())
                .reviewRequired(moderationDecision.reviewRequired())
                .build());
        draftService.deleteIfOwned(uid, req.getDraftId());
        return Result.ok(Map.of("postId", postId, "reviewRequired", moderationDecision.reviewRequired()));
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
        Long viewerUid = UserContext.get();
        PostDTO p = postFacade.getPost(postId, viewerUid);
        if (p == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        // 只有实际可见的帖子才计浏览，避免不存在或不可见内容污染计数。
        postService.incrView(postId);
        if (isPublicPost(p)) {
            applicationEventPublisher.publishEvent(PublicPostViewedEvent.builder()
                    .postId(postId)
                    .viewerUid(viewerUid)
                    .domain(p.getDomain())
                    .build());
        }
        return Result.ok(p);
    }

    @PublicApi
    @GetMapping
    public Result<PageResult<PostBriefDTO>> list(@RequestParam(required = false) Long authorId,
                                                 @RequestParam(required = false) Long tagId,

                                                 @RequestParam(required = false, name = "tag") Long tag,
                                                 @RequestParam(required = false, name = "type") Integer type,
                                                 @RequestParam(required = false) Boolean featured,
                                                 @RequestParam(required = false) Integer domain,
                                                 @RequestParam(defaultValue = "false") boolean includeTestData,
                                                 @RequestParam(defaultValue = "0") long cursor,
                                                 @RequestParam(defaultValue = "20") int size) {
        Long effectiveTagId = tagId != null ? tagId : tag;
        return Result.ok(postFacade.listPosts(authorId, effectiveTagId, type, featured,
                requireOptionalDomain(domain), cursor, size, includeTestData));
    }

    @PublicApi
    @GetMapping("/content-types")
    public Result<List<PostContentTypeDTO>> contentTypes() {
        return Result.ok(CONTENT_TYPES);
    }

    @GetMapping("/{postId}/versions")
    public Result<List<PostVersionHistoryDTO>> listVersions(@PathVariable Long postId,
                                                           @RequestParam(defaultValue = "10") int limit) {
        Long uid = UserContext.require();
        boolean moderator = adminPermissionService.isAdmin(uid) || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        return Result.ok(postFacade.listPostVersions(postId, uid, moderator, limit));
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
                                                   @RequestParam(required = false) Integer domain,
                                                   @RequestParam(defaultValue = "20") int limit,
                                                   @RequestParam(defaultValue = "false") boolean includeTestData) {
        domainModeratorService.requireModerateDomain(UserContext.require(), domain);
        return Result.ok(reportService.listRecent(status, domain, limit, includeTestData));
    }

    @PostMapping("/admin/reports/{reportId}/review")
    public Result<PostReportDTO> reviewReport(@PathVariable Long reportId, @Valid @RequestBody ReviewReq req) {
        Long uid = UserContext.require();
        // 前端可能传 approved/status/action 任一形式，resolveApproved 统一成审核布尔值。
        return Result.ok(reportService.reviewReport(reportId, uid, req.resolveApproved(), req.getNote()));
    }

    @PostMapping("/admin/featured/{postId}")
    public Result<Map<String, Object>> updateFeatured(@PathVariable Long postId,
                                                      @Valid @RequestBody FeaturedReq req) {
        Long uid = UserContext.require();
        return Result.ok(featuredService.updateFeatured(postId, Boolean.TRUE.equals(req.getFeatured()), uid, req.getNote()));
    }

    @PostMapping("/admin/knowledge/{postId}/review")
    public Result<Map<String, Object>> reviewKnowledge(@PathVariable Long postId,
                                                       @Valid @RequestBody KnowledgeReviewReq req) {
        Long uid = UserContext.require();
        return Result.ok(knowledgeReviewService.applyReview(postId, uid,
                new PostKnowledgeReviewService.KnowledgeReviewCmd(
                        req.getSummary(),
                        req.getFaqJson(),
                        req.getKnowledgeCardJson(),
                        req.getTechStacks(),
                        req.getSuggestedTags(),
                        req.getNote()
                )));
    }

    @GetMapping("/admin/domain-moderators")
    public Result<List<DomainModeratorDTO>> listDomainModerators(@RequestParam(required = false) Integer domain,
                                                                 @RequestParam(required = false) Boolean enabled,
                                                                 @RequestParam(defaultValue = "100") int limit) {
        domainModeratorService.requireModerateDomain(UserContext.require(), domain);
        return Result.ok(domainModeratorService.listModerators(domain, enabled, limit));
    }

    @PostMapping("/admin/domain-moderators")
    public Result<DomainModeratorDTO> addDomainModerator(@Valid @RequestBody DomainModeratorReq req) {
        Long uid = UserContext.require();
        adminPermissionService.requireAdmin(uid);
        return Result.ok(domainModeratorService.upsertModerator(req.getUid(), req.getDomain(), uid, req.getNote()));
    }

    @PostMapping("/admin/domain-moderators/{uid}/status")
    public Result<DomainModeratorDTO> updateDomainModeratorStatus(@PathVariable Long uid,
                                                                  @Valid @RequestBody DomainModeratorStatusReq req) {
        Long operatorUid = UserContext.require();
        adminPermissionService.requireAdmin(operatorUid);
        return Result.ok(domainModeratorService.updateModeratorStatus(uid, req.getDomain(), req.getEnabled(), operatorUid, req.getNote()));
    }

    @Data
    public static class PublishReq {
        @NotNull
        private Integer postType;
        /** 领域编码，1-技术 2-职场 3-阅读 4-生活 5-投资理财。为空时服务端默认 TECH */
        private Integer domain;
        @NotBlank
        @Size(max = 255)
        private String title;
        @NotBlank
        @Size(max = PostContentLimits.MAX_CONTENT_LEN)
        private String content;
        @Size(max = 512)
        private String coverUrl;
        private Integer visibility;
        @Size(max = PostContentLimits.MAX_EXT_JSON_LEN)
        private String extJson;
        private Boolean anonymous;
        @Size(max = 20)
        private List<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long> tags;
        @Size(max = 20)
        private List<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long> tagIds;
        @Size(max = 20)
        // Legacy request contract: private List<String> tagNames
        private List<@Size(max = 32) String> tagNames;
        private Long draftId;

        private List<Long> effectiveTagIds() {
            // tags 是早期请求字段，tagIds 是当前字段；保留兼容避免旧草稿发布失败。
            return tagIds != null ? tagIds : tags;
        }
    }

    @Data
    public static class UpdateReq {
        @Size(max = 255)
        private String title;
        @Size(max = PostContentLimits.MAX_CONTENT_LEN)
        private String content;
        @Size(max = 512)
        private String coverUrl;
        private Integer visibility;
        private Integer domain;
        @Size(max = PostContentLimits.MAX_EXT_JSON_LEN)
        private String extJson;
        private Boolean anonymous;
        @Size(max = 20)
        private List<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long> tags;
        @Size(max = 20)
        private List<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long> tagIds;
        @Size(max = 20)
        private List<@Size(max = 32) String> tagNames;
        private Long draftId;

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

    @Data
    public static class FeaturedReq {
        @NotNull
        private Boolean featured;
        @Size(max = 500)
        private String note;
    }

    @Data
    public static class KnowledgeReviewReq {
        @Size(max = 500)
        private String summary;
        @Size(max = PostContentLimits.MAX_EXT_JSON_LEN / 2)
        private String faqJson;
        @Size(max = PostContentLimits.MAX_EXT_JSON_LEN / 2)
        private String knowledgeCardJson;
        @Size(max = 20)
        private List<@Size(max = 64) String> techStacks;
        @Size(max = 20)
        private List<@Size(max = 64) String> suggestedTags;
        @Size(max = 500)
        private String note;
    }

    @Data
    public static class DomainModeratorReq {
        @NotNull
        private Long uid;
        @NotNull
        private Integer domain;
        @Size(max = 500)
        private String note;
    }

    @Data
    public static class DomainModeratorStatusReq {
        @NotNull
        private Integer domain;
        @NotNull
        private Boolean enabled;
        @Size(max = 500)
        private String note;
    }

    private static Integer requireOptionalDomain(Integer domain) {
        if (domain == null) {
            return null;
        }
        if (PostDomain.isValid(domain)) {
            return domain;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static boolean isPublicPost(PostDTO post) {
        return post != null && (post.getVisibility() == null || post.getVisibility() == Post.VIS_PUBLIC);
    }
}

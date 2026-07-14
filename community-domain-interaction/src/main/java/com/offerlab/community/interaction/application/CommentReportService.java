package com.offerlab.community.interaction.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.review.ReviewQueueItemCommand;
import com.offerlab.community.infra.review.ReviewQueuePublisher;
import com.offerlab.community.infra.tx.AfterCommitExecutor;
import com.offerlab.community.interaction.api.event.CommentReportReviewedEvent;
import com.offerlab.community.interaction.api.event.CommentUnavailableEvent;
import com.offerlab.community.interaction.api.dto.CommentReportDTO;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentReportMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentPO;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentReportPO;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentReportService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_CLOSED = 3;
    public static final String USER_STATUS_PROCESSING = "PROCESSING";
    public static final String USER_STATUS_ACTION_TAKEN = "ACTION_TAKEN";
    public static final String USER_STATUS_NOT_ACCEPTED = "NOT_ACCEPTED";
    public static final String USER_STATUS_CLOSED = "CLOSED";

    private static final int COMMENT_STATUS_NORMAL = 1;
    private static final int COMMENT_STATUS_HIDDEN = 3;
    private static final String CONTENT_STATUS_AVAILABLE = "AVAILABLE";
    private static final String CONTENT_STATUS_POST_UNAVAILABLE = "POST_UNAVAILABLE";
    private static final String CONTENT_STATUS_COMMENT_UNAVAILABLE = "COMMENT_UNAVAILABLE";
    private static final int MAX_REASON_LEN = 64;
    private static final int MAX_DETAIL_LEN = 1000;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_REPORTS_PER_DAY = 50;

    private final CommentMapper commentMapper;
    private final CommentReportMapper reportMapper;
    private final PostCounterMapper postCounterMapper;
    private final PostCounterRedis postCounterRedis;
    private final PostRepository postRepo;
    private final PostFacade postFacade;
    private final SnowflakeIdGenerator idGen;
    private final ContentModerationService contentModerationService;
    private final AdminAuditService adminAuditService;
    private final AfterCommitExecutor afterCommit;
    private final ReviewQueuePublisher reviewQueuePublisher;
    private final DomainModeratorService domainModeratorService;
    private final TrustedContentService trustedContentService;
    private final EventPublisher events;

    @Transactional
    public Long reportComment(Long commentId, Long reporterUid, String reason, String detail) {
        if (reporterUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        CommentPO comment = requireVisibleCommentForUpdate(commentId);
        if (reporterUid.equals(comment.getAuthorId())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "comment authors cannot report their own comment");
        }
        if (postFacade.getPost(comment.getPostId(), reporterUid) == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        if (reportMapper.findPendingByReporter(commentId, reporterUid) != null) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        if (reportMapper.countRecentByReporter(reporterUid, LocalDateTime.now().minusDays(1)) >= MAX_REPORTS_PER_DAY) {
            throw new BizException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        long reportId = idGen.nextId();
        contentModerationService.requireUserCanPublish(reporterUid);
        contentModerationService.requireContentAllowed(reporterUid, ContentModerationService.SCOPE_REPORT,
                ContentModerationService.SOURCE_REPORT, reportId, reason, detail);
        CommentReportPO po = new CommentReportPO();
        po.setId(reportId);
        po.setCommentId(commentId);
        po.setPostId(comment.getPostId());
        po.setReporterUid(reporterUid);
        po.setReason(clean(reason, MAX_REASON_LEN, "OTHER"));
        po.setDetail(clean(detail, MAX_DETAIL_LEN, null));
        po.setReportStatus(STATUS_PENDING);
        reportMapper.insert(po);
        publishReportQueueItem(reportId, comment, po);
        return reportId;
    }

    public List<CommentReportDTO> listRecent(Integer status, int limit) {
        return listRecent(status, limit, false);
    }

    public List<CommentReportDTO> listRecent(Integer status, int limit, boolean includeTestData) {
        return listRecent(status, null, limit, includeTestData);
    }

    public List<CommentReportDTO> listRecent(Integer status, Integer domain, int limit, boolean includeTestData) {
        Integer effectiveStatus = status == null ? null : requireKnownStatus(status);
        int safeLimit = clampLimit(limit);
        int queryLimit = includeTestData ? safeLimit : clampLimit(safeLimit * 5);
        List<CommentReportPO> reports = reportMapper.selectRecent(effectiveStatus, domain, queryLimit);
        Map<Long, CommentPO> commentsById = loadCommentsByIds(reports.stream()
                .map(CommentReportPO::getCommentId)
                .collect(Collectors.toSet()));
        Map<Long, Post> postsById = postRepo.batchFindByIds(reports.stream()
                .map(CommentReportPO::getPostId)
                .collect(Collectors.toSet()));
        return reports.stream()
                .map(po -> toDto(po, commentsById.get(po.getCommentId()), postsById.get(po.getPostId())))
                .filter(dto -> includeTestData || !isSyntheticReport(dto))
                .limit(safeLimit)
                .toList();
    }

    public List<CommentReportDTO> listUserReports(Long reporterUid, Integer status, int limit) {
        return listUserReports(reporterUid, status, null, limit);
    }

    public List<CommentReportDTO> listUserReports(Long reporterUid, Integer status, Long cursor, int limit) {
        requireReporter(reporterUid);
        Integer effectiveStatus = status == null ? null : requireKnownStatus(status);
        return reportMapper.selectByReporter(reporterUid, effectiveStatus, safeCursor(cursor), clampLimit(limit)).stream()
                .map(po -> toUserDto(po, reporterUid))
                .toList();
    }

    public CommentReportDTO getUserReport(Long reportId, Long reporterUid) {
        requireReporter(reporterUid);
        CommentReportPO report = reportMapper.selectByIdAndReporter(reportId, reporterUid);
        if (report == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toUserDto(report, reporterUid);
    }

    @Transactional
    public CommentReportDTO reviewReport(Long reportId, Long reviewerUid, Boolean approved, String note) {
        if (reviewerUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (approved == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String reviewNote = clean(note, MAX_DETAIL_LEN, null);
        if (!StringUtils.hasText(reviewNote)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "审核备注不能为空");
        }
        CommentReportPO report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Post post = postRepo.findById(report.getPostId())
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        domainModeratorService.requireModerateDomain(reviewerUid, post.getDomain());
        CommentPO targetComment = commentMapper.selectById(report.getCommentId());
        if (reviewerUid.equals(report.getReporterUid())
                || (targetComment != null && reviewerUid.equals(targetComment.getAuthorId()))) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "reporters and comment authors cannot review this report");
        }
        if (report.getReportStatus() == null || report.getReportStatus() != STATUS_PENDING) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }

        int nextStatus = approved ? STATUS_APPROVED : STATUS_REJECTED;
        int updated = reportMapper.reviewPending(reportId, nextStatus, reviewerUid, reviewNote);
        if (updated <= 0) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (approved) {
            hideCommentBranch(
                    report.getCommentId(), report.getPostId(), reviewerUid, post);
        }
        publishReportReviewedEvent(report, approved ? USER_STATUS_ACTION_TAKEN : USER_STATUS_NOT_ACCEPTED);
        CommentReportDTO dto = toDto(reportMapper.selectById(reportId));
        adminAuditService.recordRequired(reviewerUid, approved ? "COMMENT_REPORT_APPROVE" : "COMMENT_REPORT_REJECT",
                "COMMENT_REPORT", reportId, report,
                Map.of("approved", approved, "commentId", report.getCommentId(), "postId", report.getPostId()), reviewNote);
        reviewQueuePublisher.resolve("COMMENT_REPORT", reportId,
                approved ? "approved" : "rejected",
                approved ? "comment hidden" : "report rejected",
                reviewNote,
                reviewerUid);
        return dto;
    }

    @Transactional
    public CommentReportDTO closeReportFromQueue(Long reportId, Long reviewerUid, String note) {
        if (reviewerUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String reviewNote = clean(note, MAX_DETAIL_LEN, "Review queue closed");
        CommentReportPO report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Post post = postRepo.findById(report.getPostId())
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        domainModeratorService.requireModerateDomain(reviewerUid, post.getDomain());
        CommentPO targetComment = commentMapper.selectById(report.getCommentId());
        if (reviewerUid.equals(report.getReporterUid())
                || (targetComment != null && reviewerUid.equals(targetComment.getAuthorId()))) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "reporters and comment authors cannot close this report");
        }
        if (report.getReportStatus() == null || report.getReportStatus() != STATUS_PENDING) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        int updated = reportMapper.reviewPending(reportId, STATUS_CLOSED, reviewerUid, reviewNote);
        if (updated <= 0) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        publishReportReviewedEvent(report, USER_STATUS_CLOSED);
        adminAuditService.recordRequired(reviewerUid, "COMMENT_REPORT_CLOSE",
                "COMMENT_REPORT", reportId, report,
                Map.of("closed", true, "commentId", report.getCommentId(), "postId", report.getPostId()), reviewNote);
        return toDto(reportMapper.selectById(reportId));
    }

    private void publishReportQueueItem(Long reportId, CommentPO comment, CommentReportPO report) {
        String title = "评论举报 " + report.getCommentId();
        String summary = String.join(" / ", List.of(
                "帖子：" + report.getPostId(),
                "原因：" + clean(report.getReason(), MAX_REASON_LEN, "OTHER"),
                "说明：" + clean(report.getDetail(), 180, ""),
                "评论：" + summary(comment == null ? null : comment.getContent())
        )).trim();
        reviewQueuePublisher.upsert(new ReviewQueueItemCommand(
                "COMMENT_REPORT",
                reportId,
                title,
                summary,
                "high",
                report.getReporterUid(),
                80,
                "{\"commentId\":" + report.getCommentId() + ",\"postId\":" + report.getPostId() + "}",
                "comment report created"
        ));
    }

    private void publishReportReviewedEvent(CommentReportPO report, String userStatus) {
        if (report == null || report.getReporterUid() == null || report.getId() == null) {
            return;
        }
        events.publish(CommentReportReviewedEvent.builder()
                .reporterUid(report.getReporterUid())
                .reportId(report.getId())
                .postId(report.getPostId())
                .commentId(report.getCommentId())
                .userStatus(userStatus)
                .targetPath(report.getPostId() == null ? null : "/post/" + report.getPostId() + "#comments")
                .build());
    }

    private CommentPO requireVisibleComment(Long commentId) {
        CommentPO comment = commentMapper.selectById(commentId);
        return requireVisibleComment(comment);
    }

    private CommentPO requireVisibleCommentForUpdate(Long commentId) {
        CommentPO comment = commentMapper.selectByIdForUpdate(commentId);
        return requireVisibleComment(comment);
    }

    private CommentPO requireVisibleComment(CommentPO comment) {
        if (comment == null
                || comment.getCommentStatus() == null
                || comment.getCommentStatus() != COMMENT_STATUS_NORMAL
                || (comment.getIsDeleted() != null && comment.getIsDeleted() != 0)) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return comment;
    }

    private void hideCommentBranch(Long commentId, Long postId, Long actorUid, Post post) {
        CommentPO comment = requireVisibleComment(commentId);
        LambdaQueryWrapper<CommentPO> visibleQuery = new LambdaQueryWrapper<CommentPO>()
                .eq(CommentPO::getPostId, postId)
                .eq(CommentPO::getCommentStatus, COMMENT_STATUS_NORMAL)
                .eq(CommentPO::getIsDeleted, 0);
        if (comment.getRootId() == null || comment.getRootId() == 0L) {
            visibleQuery.and(q -> q.eq(CommentPO::getId, commentId).or().eq(CommentPO::getRootId, commentId));
        } else {
            visibleQuery.eq(CommentPO::getId, commentId);
        }
        Long visibleCount = commentMapper.selectCount(visibleQuery);
        if (visibleCount == null || visibleCount <= 0) {
            return;
        }

        CommentPO update = new CommentPO();
        update.setCommentStatus(COMMENT_STATUS_HIDDEN);
        int hiddenCount = commentMapper.update(update, visibleQuery);
        if (hiddenCount <= 0) {
            return;
        }
        postCounterMapper.incrComment(postId, -hiddenCount);
        afterCommit.execute(() -> postCounterRedis.incrComment(postId, -hiddenCount), "post comment hide counter:" + postId);
        events.publish(CommentUnavailableEvent.builder()
                .commentId(comment.getId())
                .postId(postId)
                .actorUid(actorUid)
                .reason("Comment was hidden after a report")
                .cascade(comment.getRootId() == null || comment.getRootId() == 0L)
                .build());
        if ((comment.getRootId() == null || comment.getRootId() == 0)
                && (comment.getParentId() == null || comment.getParentId() == 0)) {
            trustedContentService.onRootCommentUnavailable(
                    PostDTO.builder()
                            .id(post.getId())
                            .authorId(post.getAuthorId())
                            .postType(post.getPostType())
                            .visibility(post.getVisibility())
                            .postStatus(post.getPostStatus())
                            .build(),
                    comment.getId(),
                    actorUid);
        }
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private Long safeCursor(Long cursor) {
        return cursor == null || cursor <= 0 ? null : cursor;
    }

    private int requireKnownStatus(int status) {
        if (status == STATUS_PENDING || status == STATUS_APPROVED || status == STATUS_REJECTED || status == STATUS_CLOSED) {
            return status;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private void requireReporter(Long reporterUid) {
        if (reporterUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String clean(String value, int maxLen, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }

    private CommentReportDTO toDto(CommentReportPO po) {
        if (po == null) {
            return null;
        }
        CommentPO comment = commentMapper.selectById(po.getCommentId());
        Post post = postRepo.findById(po.getPostId()).orElse(null);
        return toDto(po, comment, post);
    }

    private CommentReportDTO toDto(CommentReportPO po, CommentPO comment, Post post) {
        if (po == null) {
            return null;
        }
        return CommentReportDTO.builder()
                .id(po.getId())
                .commentId(po.getCommentId())
                .postId(po.getPostId())
                .postTitle(post == null ? null : post.getTitle())
                .commentSummary(comment == null ? null : summary(comment.getContent()))
                .reporterUid(po.getReporterUid())
                .reason(po.getReason())
                .detail(po.getDetail())
                .reportStatus(po.getReportStatus())
                .userStatus(toUserStatus(po.getReportStatus()))
                .postAvailable(post != null && post.isVisibleTo(po.getReporterUid(), false))
                .commentAvailable(isVisibleComment(comment))
                .contentStatus(toContentStatus(post != null && post.isVisibleTo(po.getReporterUid(), false), isVisibleComment(comment)))
                .reviewerUid(po.getReviewerUid())
                .reviewNote(po.getReviewNote())
                .reviewTime(po.getReviewTime())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private CommentReportDTO toUserDto(CommentReportPO po, Long reporterUid) {
        if (po == null) {
            return null;
        }
        CommentPO comment = commentMapper.selectById(po.getCommentId());
        PostDTO post = postFacade.getPost(po.getPostId(), reporterUid);
        boolean postAvailable = post != null;
        boolean commentAvailable = postAvailable && isVisibleComment(comment);
        return CommentReportDTO.builder()
                .id(po.getId())
                .commentId(po.getCommentId())
                .postId(po.getPostId())
                .postTitle(postAvailable ? post.getTitle() : null)
                .commentSummary(commentAvailable ? summary(comment.getContent()) : null)
                .reporterUid(po.getReporterUid())
                .reason(po.getReason())
                .detail(po.getDetail())
                .reportStatus(po.getReportStatus())
                .userStatus(toUserStatus(po.getReportStatus()))
                .postAvailable(postAvailable)
                .commentAvailable(commentAvailable)
                .contentStatus(toContentStatus(postAvailable, commentAvailable))
                .reviewTime(po.getReviewTime())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private Map<Long, CommentPO> loadCommentsByIds(Collection<Long> commentIds) {
        Set<Long> ids = commentIds == null ? Set.of() : commentIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return commentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CommentPO::getId, comment -> comment, (left, right) -> left));
    }

    private boolean isVisibleComment(CommentPO comment) {
        return comment != null
                && comment.getCommentStatus() != null
                && comment.getCommentStatus() == COMMENT_STATUS_NORMAL
                && (comment.getIsDeleted() == null || comment.getIsDeleted() == 0);
    }

    private String toUserStatus(Integer status) {
        if (status != null && status == STATUS_APPROVED) {
            return USER_STATUS_ACTION_TAKEN;
        }
        if (status != null && status == STATUS_REJECTED) {
            return USER_STATUS_NOT_ACCEPTED;
        }
        if (status != null && status == STATUS_CLOSED) {
            return USER_STATUS_CLOSED;
        }
        return USER_STATUS_PROCESSING;
    }

    private String toContentStatus(boolean postAvailable, boolean commentAvailable) {
        if (!postAvailable) {
            return CONTENT_STATUS_POST_UNAVAILABLE;
        }
        if (!commentAvailable) {
            return CONTENT_STATUS_COMMENT_UNAVAILABLE;
        }
        return CONTENT_STATUS_AVAILABLE;
    }

    private boolean isSyntheticReport(CommentReportDTO dto) {
        if (dto == null) {
            return false;
        }
        return PublicContentFilter.isSyntheticText(dto.getPostTitle())
                || PublicContentFilter.isSyntheticText(dto.getCommentSummary())
                || PublicContentFilter.isSyntheticText(dto.getReason())
                || PublicContentFilter.isSyntheticText(dto.getDetail());
    }

    private String summary(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.replaceAll("[#>*`_\\[\\]()-]", " ").replaceAll("\\s+", " ").trim();
        return text.length() <= 140 ? text : text.substring(0, 140);
    }
}

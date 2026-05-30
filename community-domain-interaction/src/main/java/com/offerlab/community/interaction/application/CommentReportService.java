package com.offerlab.community.interaction.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.interaction.api.dto.CommentReportDTO;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentReportMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentPO;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentReportPO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommentReportService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;

    private static final int COMMENT_STATUS_NORMAL = 1;
    private static final int COMMENT_STATUS_HIDDEN = 3;
    private static final int MAX_REASON_LEN = 64;
    private static final int MAX_DETAIL_LEN = 1000;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_REPORTS_PER_DAY = 50;

    private final CommentMapper commentMapper;
    private final CommentReportMapper reportMapper;
    private final PostCounterMapper postCounterMapper;
    private final PostCounterRedis postCounterRedis;
    private final PostRepository postRepo;
    private final SnowflakeIdGenerator idGen;
    private final ContentModerationService contentModerationService;
    private final AdminAuditService adminAuditService;

    @Transactional
    public Long reportComment(Long commentId, Long reporterUid, String reason, String detail) {
        if (reporterUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        CommentPO comment = requireVisibleComment(commentId);
        contentModerationService.requireUserCanPublish(reporterUid);
        contentModerationService.requireContentAllowed(reporterUid, ContentModerationService.SCOPE_REPORT, reason, detail);
        if (reportMapper.findPendingByReporter(commentId, reporterUid) != null) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        if (reportMapper.countRecentByReporter(reporterUid, LocalDateTime.now().minusDays(1)) >= MAX_REPORTS_PER_DAY) {
            throw new BizException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        long reportId = idGen.nextId();
        CommentReportPO po = new CommentReportPO();
        po.setId(reportId);
        po.setCommentId(commentId);
        po.setPostId(comment.getPostId());
        po.setReporterUid(reporterUid);
        po.setReason(clean(reason, MAX_REASON_LEN, "OTHER"));
        po.setDetail(clean(detail, MAX_DETAIL_LEN, null));
        po.setReportStatus(STATUS_PENDING);
        reportMapper.insert(po);
        return reportId;
    }

    public List<CommentReportDTO> listRecent(Integer status, int limit) {
        Integer effectiveStatus = status == null ? null : requireKnownStatus(status);
        return reportMapper.selectRecent(effectiveStatus, clampLimit(limit)).stream()
                .map(this::toDto)
                .toList();
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
        if (report.getReportStatus() == null || report.getReportStatus() != STATUS_PENDING) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }

        int nextStatus = approved ? STATUS_APPROVED : STATUS_REJECTED;
        int updated = reportMapper.reviewPending(reportId, nextStatus, reviewerUid, reviewNote);
        if (updated <= 0) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (approved) {
            hideCommentBranch(report.getCommentId(), report.getPostId());
        }
        CommentReportDTO dto = toDto(reportMapper.selectById(reportId));
        adminAuditService.record(reviewerUid, approved ? "COMMENT_REPORT_APPROVE" : "COMMENT_REPORT_REJECT",
                "COMMENT_REPORT", reportId, report,
                Map.of("approved", approved, "commentId", report.getCommentId(), "postId", report.getPostId()), reviewNote);
        return dto;
    }

    private CommentPO requireVisibleComment(Long commentId) {
        CommentPO comment = commentMapper.selectById(commentId);
        if (comment == null
                || comment.getCommentStatus() == null
                || comment.getCommentStatus() != COMMENT_STATUS_NORMAL
                || (comment.getIsDeleted() != null && comment.getIsDeleted() != 0)) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return comment;
    }

    private void hideCommentBranch(Long commentId, Long postId) {
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
        commentMapper.update(update, visibleQuery);
        postCounterRedis.incrComment(postId, -visibleCount);
        postCounterMapper.incrComment(postId, -visibleCount);
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private int requireKnownStatus(int status) {
        if (status == STATUS_PENDING || status == STATUS_APPROVED || status == STATUS_REJECTED) {
            return status;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
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
                .reviewerUid(po.getReviewerUid())
                .reviewNote(po.getReviewNote())
                .reviewTime(po.getReviewTime())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private String summary(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.replaceAll("[#>*`_\\[\\]()-]", " ").replaceAll("\\s+", " ").trim();
        return text.length() <= 140 ? text : text.substring(0, 140);
    }
}

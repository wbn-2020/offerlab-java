package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.redis.cache.CacheKeyBuilder;
import com.offerlab.community.infra.redis.cache.MultiLevelCache;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostReportDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostReportMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostReportPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostReportService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;

    private static final int MAX_REASON_LEN = 64;
    private static final int MAX_DETAIL_LEN = 1000;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_REPORTS_PER_DAY = 50;

    private final PostRepository postRepo;
    private final PostReportMapper reportMapper;
    private final SnowflakeIdGenerator idGen;
    private final MultiLevelCache<PostDTO> postDetailCache;
    private final ContentModerationService contentModerationService;
    private final AdminAuditService adminAuditService;

    @Transactional
    public Long reportPost(Long postId, Long reporterUid, String reason, String detail) {
        if (reporterUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (!post.isVisibleTo(null, false)) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        contentModerationService.requireUserCanPublish(reporterUid);
        contentModerationService.requireContentAllowed(reporterUid, ContentModerationService.SCOPE_REPORT, reason, detail);
        if (reportMapper.findPendingByReporter(postId, reporterUid) != null) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        if (reportMapper.countRecentByReporter(reporterUid, LocalDateTime.now().minusDays(1)) >= MAX_REPORTS_PER_DAY) {
            throw new BizException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        long reportId = idGen.nextId();
        PostReportPO po = new PostReportPO();
        po.setId(reportId);
        po.setPostId(postId);
        po.setReporterUid(reporterUid);
        po.setReason(clean(reason, MAX_REASON_LEN, "OTHER"));
        po.setDetail(clean(detail, MAX_DETAIL_LEN, null));
        po.setReportStatus(STATUS_PENDING);
        reportMapper.insert(po);
        return reportId;
    }

    public List<PostReportDTO> listRecent(Integer status, int limit) {
        Integer effectiveStatus = status == null ? null : requireKnownStatus(status);
        return reportMapper.selectRecent(effectiveStatus, clampLimit(limit)).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public PostReportDTO reviewReport(Long reportId, Long reviewerUid, Boolean approved, String note) {
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
        PostReportPO report = reportMapper.selectById(reportId);
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
            takeDownPost(report.getPostId());
        }

        PostReportDTO dto = toDto(reportMapper.selectById(reportId));
        adminAuditService.record(reviewerUid, approved ? "POST_REPORT_APPROVE" : "POST_REPORT_REJECT",
                "POST_REPORT", reportId, report, Map.of("approved", approved, "postId", report.getPostId()), reviewNote);
        return dto;
    }

    private void takeDownPost(Long postId) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (post.getPostStatus() == null || post.getPostStatus() != Post.STATUS_TAKEN_DOWN) {
            post.setPostStatus(Post.STATUS_TAKEN_DOWN);
            postRepo.update(post);
        }
        postDetailCache.evict(CacheKeyBuilder.postDetail(postId));
        postDetailCache.evict(CacheKeyBuilder.postDetailRaw(postId));
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

    private PostReportDTO toDto(PostReportPO po) {
        if (po == null) {
            return null;
        }
        Post post = postRepo.findById(po.getPostId()).orElse(null);
        return PostReportDTO.builder()
                .id(po.getId())
                .postId(po.getPostId())
                .postTitle(post == null ? null : post.getTitle())
                .postSummary(post == null ? null : summary(post.getContent()))
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

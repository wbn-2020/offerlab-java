package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.redis.cache.CacheKeyBuilder;
import com.offerlab.community.infra.redis.cache.MultiLevelCache;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.review.ReviewQueueItemCommand;
import com.offerlab.community.infra.review.ReviewQueuePublisher;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostReportDTO;
import com.offerlab.community.post.api.dto.PostReportReceiptDTO;
import com.offerlab.community.post.api.event.PostReportReviewedEvent;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostReportMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostReportPO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
    public static final int STATUS_CLOSED = 3;
    public static final String USER_STATUS_PROCESSING = "PROCESSING";
    public static final String USER_STATUS_ACTION_TAKEN = "ACTION_TAKEN";
    public static final String USER_STATUS_NOT_ACCEPTED = "NOT_ACCEPTED";
    public static final String USER_STATUS_CLOSED = "CLOSED";

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
    private final ReviewQueuePublisher reviewQueuePublisher;
    private final DomainModeratorService domainModeratorService;

    @Autowired(required = false)
    private ApplicationEventPublisher applicationEventPublisher;

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
        publishReportQueueItem(reportId, post, po);
        return reportId;
    }

    public List<PostReportDTO> listRecent(Integer status, int limit) {
        return listRecent(status, limit, false);
    }

    public List<PostReportDTO> listRecent(Integer status, int limit, boolean includeTestData) {
        return listRecent(status, null, limit, includeTestData);
    }

    public List<PostReportDTO> listRecent(Integer status, Integer domain, int limit, boolean includeTestData) {
        Integer effectiveStatus = status == null ? null : requireKnownStatus(status);
        int safeLimit = clampLimit(limit);
        int queryLimit = includeTestData ? safeLimit : clampLimit(safeLimit * 5);
        List<PostReportPO> reports = reportMapper.selectRecent(effectiveStatus, domain, queryLimit);
        Map<Long, Post> postsById = batchLoadReportPosts(reports);
        return reports.stream()
                .map(po -> toDto(po, postsById.get(po.getPostId())))
                .filter(dto -> includeTestData || !isSyntheticReport(dto))
                .limit(safeLimit)
                .toList();
    }

    public List<PostReportReceiptDTO> listMyReceipts(Long reporterUid, int limit) {
        return listMyReceipts(reporterUid, null, null, limit);
    }

    public List<PostReportReceiptDTO> listMyReceipts(Long reporterUid, Integer status, Long cursor, int limit) {
        requireReporter(reporterUid);
        Integer effectiveStatus = status == null ? null : requireKnownStatus(status);
        List<PostReportPO> reports = reportMapper.selectByReporter(reporterUid, effectiveStatus, safeCursor(cursor), clampLimit(limit));
        Map<Long, Post> postsById = batchLoadReportPosts(reports);
        return reports.stream()
                .map(po -> toReceiptDto(po, postsById.get(po.getPostId()), reporterUid))
                .toList();
    }

    public PostReportReceiptDTO getMyReceipt(Long reportId, Long reporterUid) {
        requireReporter(reporterUid);
        PostReportPO report = reportMapper.selectByIdAndReporter(reportId, reporterUid);
        if (report == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Post post = postRepo.findById(report.getPostId()).orElse(null);
        return toReceiptDto(report, post, reporterUid);
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
        Post post = postRepo.findById(report.getPostId())
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        domainModeratorService.requireModerateDomain(reviewerUid, post.getDomain());
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

        publishReportReviewedEvent(report, approved ? USER_STATUS_ACTION_TAKEN : USER_STATUS_NOT_ACCEPTED);
        PostReportDTO dto = toDto(reportMapper.selectById(reportId));
        adminAuditService.recordRequired(reviewerUid, approved ? "POST_REPORT_APPROVE" : "POST_REPORT_REJECT",
                "POST_REPORT", reportId, report, Map.of("approved", approved, "postId", report.getPostId()), reviewNote);
        reviewQueuePublisher.resolve("POST_REPORT", reportId,
                approved ? "approved" : "rejected",
                approved ? "post taken down" : "report rejected",
                reviewNote,
                reviewerUid);
        return dto;
    }

    @Transactional
    public PostReportDTO closeReportFromQueue(Long reportId, Long reviewerUid, String note) {
        if (reviewerUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String reviewNote = clean(note, MAX_DETAIL_LEN, "Review queue closed");
        PostReportPO report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Post post = postRepo.findById(report.getPostId())
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        domainModeratorService.requireModerateDomain(reviewerUid, post.getDomain());
        if (report.getReportStatus() == null || report.getReportStatus() != STATUS_PENDING) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        int updated = reportMapper.reviewPending(reportId, STATUS_CLOSED, reviewerUid, reviewNote);
        if (updated <= 0) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        publishReportReviewedEvent(report, USER_STATUS_CLOSED);
        adminAuditService.recordRequired(reviewerUid, "POST_REPORT_CLOSE",
                "POST_REPORT", reportId, report, Map.of("closed", true, "postId", report.getPostId()), reviewNote);
        return toDto(reportMapper.selectById(reportId));
    }

    private void publishReportQueueItem(Long reportId, Post post, PostReportPO report) {
        String title = post == null || !StringUtils.hasText(post.getTitle())
                ? "帖子举报 " + report.getPostId()
                : "帖子举报：" + post.getTitle();
        String summary = String.join(" / ", List.of(
                "原因：" + clean(report.getReason(), MAX_REASON_LEN, "OTHER"),
                "说明：" + clean(report.getDetail(), 180, "")
        )).trim();
        reviewQueuePublisher.upsert(new ReviewQueueItemCommand(
                "POST_REPORT",
                reportId,
                title,
                summary,
                "high",
                report.getReporterUid(),
                80,
                "{\"postId\":" + report.getPostId() + "}",
                "post report created"
        ));
    }

    private void publishReportReviewedEvent(PostReportPO report, String userStatus) {
        if (applicationEventPublisher == null || report == null || report.getReporterUid() == null || report.getId() == null) {
            return;
        }
        applicationEventPublisher.publishEvent(PostReportReviewedEvent.builder()
                .reporterUid(report.getReporterUid())
                .reportId(report.getId())
                .postId(report.getPostId())
                .userStatus(userStatus)
                .targetPath(report.getPostId() == null ? null : "/post/" + report.getPostId())
                .build());
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

    private Map<Long, Post> batchLoadReportPosts(List<PostReportPO> reports) {
        if (reports == null || reports.isEmpty()) {
            return Map.of();
        }
        List<Long> postIds = reports.stream()
                .map(PostReportPO::getPostId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (postIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Post> posts = postRepo.batchFindByIds(postIds);
        return posts == null ? Map.of() : posts;
    }

    private PostReportDTO toDto(PostReportPO po) {
        return toDto(po, po == null ? null : postRepo.findById(po.getPostId()).orElse(null));
    }

    private PostReportDTO toDto(PostReportPO po, Post post) {
        if (po == null) {
            return null;
        }
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

    private PostReportReceiptDTO toReceiptDto(PostReportPO po, Post post, Long viewerUid) {
        if (po == null) {
            return null;
        }
        boolean targetAvailable = post != null && post.isVisibleTo(viewerUid, false);
        return PostReportReceiptDTO.builder()
                .id(po.getId())
                .postId(po.getPostId())
                .targetAvailable(targetAvailable)
                .postTitle(targetAvailable ? post.getTitle() : null)
                .postSummary(targetAvailable ? summary(post.getContent()) : null)
                .reason(po.getReason())
                .detail(po.getDetail())
                .reportStatus(po.getReportStatus())
                .userStatus(toUserStatus(po.getReportStatus()))
                .statusMessage(toUserStatusMessage(po.getReportStatus()))
                .reviewTime(po.getReviewTime())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private String toUserStatus(Integer reportStatus) {
        if (reportStatus != null && reportStatus == STATUS_APPROVED) {
            return USER_STATUS_ACTION_TAKEN;
        }
        if (reportStatus != null && reportStatus == STATUS_REJECTED) {
            return USER_STATUS_NOT_ACCEPTED;
        }
        if (reportStatus != null && reportStatus == STATUS_CLOSED) {
            return USER_STATUS_CLOSED;
        }
        return USER_STATUS_PROCESSING;
    }

    private String toUserStatusMessage(Integer reportStatus) {
        if (reportStatus != null && reportStatus == STATUS_CLOSED) {
            return "举报已关闭，平台已记录该反馈";
        }
        if (reportStatus != null && reportStatus == STATUS_APPROVED) {
            return "平台已处理该内容";
        }
        if (reportStatus != null && reportStatus == STATUS_REJECTED) {
            return "经复核，暂未发现明确违规";
        }
        return "平台已收到，正在处理";
    }

    private boolean isSyntheticReport(PostReportDTO dto) {
        if (dto == null) {
            return false;
        }
        return PublicContentFilter.isSyntheticText(dto.getPostTitle())
                || PublicContentFilter.isSyntheticText(dto.getPostSummary())
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

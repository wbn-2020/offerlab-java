package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.redis.cache.CacheKeyBuilder;
import com.offerlab.community.infra.redis.cache.MultiLevelCache;
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

@Service
@RequiredArgsConstructor
public class PostReportService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;

    private static final int MAX_REASON_LEN = 64;
    private static final int MAX_DETAIL_LEN = 1000;
    private static final int MAX_LIMIT = 100;

    private final PostRepository postRepo;
    private final PostReportMapper reportMapper;
    private final SnowflakeIdGenerator idGen;
    private final MultiLevelCache<PostDTO> postDetailCache;

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
        PostReportPO report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (report.getReportStatus() == null || report.getReportStatus() != STATUS_PENDING) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }

        int nextStatus = approved ? STATUS_APPROVED : STATUS_REJECTED;
        int updated = reportMapper.reviewPending(reportId, nextStatus, reviewerUid, clean(note, MAX_DETAIL_LEN, null));
        if (updated <= 0) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }

        if (approved) {
            takeDownPost(report.getPostId());
        }

        return toDto(reportMapper.selectById(reportId));
    }

    private void takeDownPost(Long postId) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (post.getPostStatus() == null || post.getPostStatus() != Post.STATUS_TAKEN_DOWN) {
            post.setPostStatus(Post.STATUS_TAKEN_DOWN);
            postRepo.update(post);
        }
        postDetailCache.evict(CacheKeyBuilder.postDetail(postId));
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
        return PostReportDTO.builder()
                .id(po.getId())
                .postId(po.getPostId())
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
}

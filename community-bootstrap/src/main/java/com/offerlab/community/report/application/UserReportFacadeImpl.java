package com.offerlab.community.report.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.interaction.api.dto.CommentReportDTO;
import com.offerlab.community.interaction.application.CommentReportService;
import com.offerlab.community.post.api.dto.PostReportReceiptDTO;
import com.offerlab.community.post.application.PostReportService;
import com.offerlab.community.report.api.UserReportFacade;
import com.offerlab.community.report.api.dto.UserReportReceiptDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class UserReportFacadeImpl implements UserReportFacade {

    private static final String SOURCE_POST = "POST_REPORT";
    private static final String SOURCE_COMMENT = "COMMENT_REPORT";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_ACTION_TAKEN = "ACTION_TAKEN";
    private static final String STATUS_NOT_ACCEPTED = "NOT_ACCEPTED";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final PostReportService postReportService;
    private final CommentReportService commentReportService;

    public UserReportFacadeImpl(PostReportService postReportService,
                                CommentReportService commentReportService) {
        this.postReportService = postReportService;
        this.commentReportService = commentReportService;
    }

    @Override
    public PageResult<UserReportReceiptDTO> listMyReports(Long uid, String sourceType, String status, String cursor, Integer limit) {
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String effectiveSource = normalizeSourceType(sourceType, false);
        Integer reportStatus = normalizeStatus(status);
        Long cursorId = parseCursor(cursor);
        int safeLimit = clampLimit(limit);
        int fetchLimit = safeLimit + 1;

        List<UserReportReceiptDTO> rows = new ArrayList<>();
        if (effectiveSource == null || SOURCE_POST.equals(effectiveSource)) {
            postReportService.listMyReceipts(uid, reportStatus, cursorId, fetchLimit).stream()
                    .map(this::fromPost)
                    .forEach(rows::add);
        }
        if (effectiveSource == null || SOURCE_COMMENT.equals(effectiveSource)) {
            commentReportService.listUserReports(uid, reportStatus, cursorId, fetchLimit).stream()
                    .map(this::fromComment)
                    .forEach(rows::add);
        }

        rows.sort(reportComparator());
        boolean hasMore = rows.size() > safeLimit;
        List<UserReportReceiptDTO> pageRows = hasMore ? rows.subList(0, safeLimit) : rows;
        String nextCursor = hasMore && !pageRows.isEmpty()
                ? String.valueOf(pageRows.get(pageRows.size() - 1).reportId())
                : null;
        return PageResult.of(pageRows, nextCursor, hasMore);
    }

    @Override
    public UserReportReceiptDTO getMyReport(Long uid, String sourceType, Long reportId) {
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (reportId == null || reportId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String effectiveSource = normalizeSourceType(sourceType, true);
        if (SOURCE_POST.equals(effectiveSource)) {
            return fromPost(postReportService.getMyReceipt(reportId, uid));
        }
        return fromComment(commentReportService.getUserReport(reportId, uid));
    }

    private UserReportReceiptDTO fromPost(PostReportReceiptDTO dto) {
        if (dto == null) {
            return null;
        }
        boolean targetAvailable = Boolean.TRUE.equals(dto.getTargetAvailable());
        Long postId = dto.getPostId();
        return new UserReportReceiptDTO(
                dto.getId(),
                SOURCE_POST,
                postId,
                postId,
                dto.getPostTitle(),
                dto.getPostSummary(),
                dto.getReason(),
                dto.getDetail(),
                normalizeUserStatus(dto.getUserStatus()),
                resultText(dto.getUserStatus(), dto.getStatusMessage()),
                targetAvailable && postId != null ? "/post/" + postId : null,
                dto.getCreateTime(),
                dto.getReviewTime(),
                targetAvailable
        );
    }

    private UserReportReceiptDTO fromComment(CommentReportDTO dto) {
        if (dto == null) {
            return null;
        }
        boolean targetAvailable = Boolean.TRUE.equals(dto.getPostAvailable())
                && Boolean.TRUE.equals(dto.getCommentAvailable());
        Long postId = dto.getPostId();
        return new UserReportReceiptDTO(
                dto.getId(),
                SOURCE_COMMENT,
                dto.getCommentId(),
                postId,
                dto.getPostTitle(),
                dto.getCommentSummary(),
                dto.getReason(),
                dto.getDetail(),
                normalizeUserStatus(dto.getUserStatus()),
                resultText(dto.getUserStatus(), null),
                targetAvailable && postId != null ? "/post/" + postId + "#comments" : null,
                dto.getCreateTime(),
                dto.getReviewTime(),
                targetAvailable
        );
    }

    private Comparator<UserReportReceiptDTO> reportComparator() {
        return Comparator
                .comparing(UserReportReceiptDTO::createTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(UserReportReceiptDTO::reportId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private String normalizeSourceType(String sourceType, boolean required) {
        if (!StringUtils.hasText(sourceType)) {
            if (required) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            return null;
        }
        String value = sourceType.trim().toUpperCase();
        if ("POST".equals(value)) {
            return SOURCE_POST;
        }
        if ("COMMENT".equals(value)) {
            return SOURCE_COMMENT;
        }
        if (SOURCE_POST.equals(value) || SOURCE_COMMENT.equals(value)) {
            return value;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private Integer normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String value = status.trim().toUpperCase();
        return switch (value) {
            case "0", STATUS_PROCESSING, "PENDING" -> PostReportService.STATUS_PENDING;
            case "1", STATUS_ACTION_TAKEN, "APPROVED", "ACCEPTED" -> PostReportService.STATUS_APPROVED;
            case "2", STATUS_NOT_ACCEPTED, STATUS_CLOSED, "REJECTED" -> PostReportService.STATUS_REJECTED;
            default -> throwParamError();
        };
    }

    private Integer throwParamError() {
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private Long parseCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            long value = Long.parseLong(cursor.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private int clampLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        return Math.max(1, Math.min(value, MAX_LIMIT));
    }

    private String normalizeUserStatus(String userStatus) {
        if (STATUS_ACTION_TAKEN.equals(userStatus)
                || STATUS_NOT_ACCEPTED.equals(userStatus)
                || STATUS_CLOSED.equals(userStatus)) {
            return userStatus;
        }
        return STATUS_PROCESSING;
    }

    private String resultText(String userStatus, String fallback) {
        String normalized = normalizeUserStatus(userStatus);
        if (StringUtils.hasText(fallback)) {
            return fallback;
        }
        return switch (normalized) {
            case STATUS_ACTION_TAKEN -> "平台已处理该内容";
            case STATUS_NOT_ACCEPTED -> "经复核，暂未发现明确违规";
            case STATUS_CLOSED -> "举报已关闭";
            default -> "平台已收到，正在处理";
        };
    }
}

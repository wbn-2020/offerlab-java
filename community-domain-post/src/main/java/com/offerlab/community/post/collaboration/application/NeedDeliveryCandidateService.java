package com.offerlab.community.post.collaboration.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.collaboration.api.NeedDeliveryCandidateDTO;
import com.offerlab.community.post.collaboration.infrastructure.persistence.NeedDeliveryCandidateMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.NeedDeliveryCandidateRows;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NeedDeliveryCandidateService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_KEYWORD_LENGTH = 80;

    private final NeedDeliveryCandidateMapper mapper;

    public PageResult<NeedDeliveryCandidateDTO> list(Long needId, Long uid,
                                                      String resolutionType, String keyword,
                                                      String cursor, int size) {
        requirePositive(needId, "needId");
        requirePositive(uid, "uid");
        int pageSize = safePageSize(size);
        String normalizedType = normalizeResolutionType(resolutionType);
        String normalizedKeyword = normalizeKeyword(keyword);

        NeedDeliveryCandidateRows.NeedContextRow need = mapper.selectNeed(needId);
        if (need == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!uid.equals(need.getClaimedByUid())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!"CLAIMED".equals(need.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "delivery candidates are only available for a claimed need");
        }

        DeliveryCursor pageCursor = DeliveryCursor.parse(cursor);
        List<NeedDeliveryCandidateRows.CandidateRow> rows = mapper.listCandidates(
                uid,
                normalizedType,
                normalizedKeyword,
                pageCursor.updatedAt(),
                pageCursor.id(),
                pageSize + 1);
        List<NeedDeliveryCandidateRows.CandidateRow> safeRows = rows == null ? List.of() : rows;
        boolean hasMore = safeRows.size() > pageSize;
        List<NeedDeliveryCandidateRows.CandidateRow> visibleRows = safeRows.stream()
                .limit(pageSize)
                .toList();
        List<NeedDeliveryCandidateDTO> items = visibleRows.stream()
                .map(row -> toDto(row, need))
                .toList();
        String nextCursor = hasMore && !visibleRows.isEmpty()
                ? DeliveryCursor.from(visibleRows.get(visibleRows.size() - 1)).encode()
                : null;
        return PageResult.of(items, nextCursor, hasMore)
                .withMetadata("collaboration-delivery-candidates", false, null, pageSize + 1);
    }

    private NeedDeliveryCandidateDTO toDto(NeedDeliveryCandidateRows.CandidateRow row,
                                           NeedDeliveryCandidateRows.NeedContextRow need) {
        String reason = null;
        String ineligibleReason = null;
        boolean domainMatches = need.getDomain() != null && need.getDomain().equals(row.getDomain());
        boolean formatMatches = formatMatches(need.getContentFormat(), row.getResolutionType());
        if (!domainMatches) {
            ineligibleReason = "DOMAIN_MISMATCH";
        } else if (!formatMatches) {
            ineligibleReason = "CONTENT_FORMAT_MISMATCH";
        } else {
            reason = "ELIGIBLE_PUBLIC_OWNED_RESOURCE";
        }
        return NeedDeliveryCandidateDTO.builder()
                .id(row.getId())
                .resolutionType(row.getResolutionType())
                .title(row.getTitle())
                .domain(row.getDomain())
                .postType(row.getPostType())
                .publicPath(row.getPublicPath())
                .eligible(ineligibleReason == null)
                .eligibilityReason(reason)
                .ineligibleReason(ineligibleReason)
                .createTime(row.getCreateTime())
                .updateTime(row.getUpdateTime())
                .build();
    }

    private static boolean formatMatches(String contentFormat, String resolutionType) {
        if (!StringUtils.hasText(contentFormat)) {
            return true;
        }
        String format = contentFormat.trim().toUpperCase(Locale.ROOT);
        if ("QUESTION".equals(format)) {
            return "QUESTION".equals(resolutionType);
        }
        return "POST".equals(resolutionType) || "SERIES".equals(resolutionType);
    }

    private static String normalizeResolutionType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("POST", "QUESTION", "SERIES").contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "resolutionType is invalid");
        }
        return normalized;
    }

    private static String normalizeKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "keyword is too long");
        }
        return normalized;
    }

    private static int safePageSize(int size) {
        if (size <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), name + " is invalid");
        }
    }

    private record DeliveryCursor(LocalDateTime updatedAt, Long id) {

        private static DeliveryCursor parse(String value) {
            if (!StringUtils.hasText(value) || "0".equals(value.trim())) {
                return new DeliveryCursor(null, null);
            }
            try {
                String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
                String[] parts = raw.split("\\|", -1);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("cursor shape");
                }
                long id = Long.parseLong(parts[1]);
                if (id <= 0) {
                    throw new IllegalArgumentException("cursor id");
                }
                return new DeliveryCursor(LocalDateTime.parse(parts[0]), id);
            } catch (RuntimeException ex) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "cursor is invalid");
            }
        }

        private static DeliveryCursor from(NeedDeliveryCandidateRows.CandidateRow row) {
            if (row == null || row.getUpdateTime() == null || row.getId() == null) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                        "delivery candidate cursor cannot be generated");
            }
            return new DeliveryCursor(row.getUpdateTime(), row.getId());
        }

        private String encode() {
            String raw = updatedAt + "|" + id;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }
}

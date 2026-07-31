package com.offerlab.community.post.collaboration.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.collaboration.api.NeedDiscoveryItemDTO;
import com.offerlab.community.post.collaboration.api.NeedDiscoverySort;
import com.offerlab.community.post.collaboration.infrastructure.persistence.NeedDiscoveryMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.NeedDiscoveryRows;
import com.offerlab.community.post.domain.model.PostDomain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NeedDiscoveryService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_KEYWORD_LENGTH = 120;
    private static final List<String> STATUSES =
            List.of("OPEN", "CLAIMED", "SUBMITTED", "COMPLETED", "CLOSED", "MERGED");
    private static final List<String> SOURCE_TYPES =
            List.of("COMMUNITY", "POST", "TOPIC", "ACTIVITY", "EXTERNAL", "SEARCH_GAP");
    private static final List<String> CONTENT_FORMATS =
            List.of("ARTICLE", "QUESTION", "GUIDE", "CHECKLIST", "RESOURCE");

    private final NeedDiscoveryMapper mapper;

    public PageResult<NeedDiscoveryItemDTO> list(String keyword, Integer domain, String status,
                                                  String contentFormat, String sourceType, String sort,
                                                  String cursor, int size, Long viewerUid) {
        Integer normalizedDomain = normalizeDomain(domain);
        String normalizedStatus = normalizeEnumFilter(status, STATUSES, "status");
        String normalizedFormat = normalizeEnumFilter(contentFormat, CONTENT_FORMATS, "contentFormat");
        String normalizedSourceType = normalizeEnumFilter(sourceType, SOURCE_TYPES, "sourceType");
        String normalizedKeyword = normalizeKeyword(keyword);
        NeedDiscoverySort normalizedSort = normalizeSort(sort);
        int pageSize = safePageSize(size);
        DiscoveryCursor pageCursor = DiscoveryCursor.parse(cursor, normalizedSort);

        List<NeedDiscoveryRows.NeedRow> rows = mapper.listNeeds(
                viewerUid,
                normalizedDomain,
                normalizedStatus,
                normalizedFormat,
                normalizedSourceType,
                normalizedKeyword,
                normalizedSort.name(),
                pageCursor.time(),
                pageCursor.id(),
                pageCursor.stalled(),
                pageSize + 1);
        List<NeedDiscoveryRows.NeedRow> safeRows = rows == null ? List.of() : rows;
        boolean hasMore = safeRows.size() > pageSize;
        List<NeedDiscoveryRows.NeedRow> visibleRows = safeRows.stream()
                .limit(pageSize)
                .toList();
        List<NeedDiscoveryItemDTO> items = visibleRows.stream()
                .map(row -> toDto(row, normalizedDomain, normalizedFormat, normalizedSourceType))
                .toList();
        String nextCursor = hasMore && !visibleRows.isEmpty()
                ? DiscoveryCursor.from(visibleRows.get(visibleRows.size() - 1), normalizedSort).encode()
                : null;
        return PageResult.of(items, nextCursor, hasMore)
                .withMetadata("collaboration-need-discovery", false, null, pageSize + 1);
    }

    private NeedDiscoveryItemDTO toDto(NeedDiscoveryRows.NeedRow row,
                                       Integer domain, String contentFormat, String sourceType) {
        List<String> reasons = new ArrayList<>();
        if (domain != null && domain.equals(row.getDomain())) {
            reasons.add("FILTER_DOMAIN_MATCH");
        }
        if (contentFormat != null && contentFormat.equals(row.getContentFormat())) {
            reasons.add("FILTER_CONTENT_FORMAT_MATCH");
        }
        if (sourceType != null && sourceType.equals(row.getSourceType())) {
            reasons.add("FILTER_SOURCE_TYPE_MATCH");
        }
        if (row.getViewerDomainMatch() != null && row.getViewerDomainMatch() == 1) {
            reasons.add("PUBLIC_DOMAIN_CONTRIBUTION_MATCH");
        }
        if (row.getViewerFormatMatch() != null && row.getViewerFormatMatch() == 1) {
            reasons.add("PUBLIC_CONTENT_FORMAT_CONTRIBUTION_MATCH");
        }
        return NeedDiscoveryItemDTO.builder()
                .id(row.getId())
                .creatorUid(row.getCreatorUid())
                .domain(row.getDomain())
                .sourceType(row.getSourceType())
                .sourceRefId(row.getSourceRefId())
                .contentFormat(row.getContentFormat())
                .title(row.getTitle())
                .description(row.getDescription())
                .acceptanceCriteria(row.getAcceptanceCriteria())
                .status(row.getStatus())
                .claimedByUid(row.getClaimedByUid())
                .claimedAt(row.getClaimedAt())
                .lastProgressAt(row.getLastProgressAt())
                .stalled(row.getStalled() != null && row.getStalled() == 1)
                .mergedIntoNeedId(row.getMergedIntoNeedId())
                .resolutionType(row.getResolutionType())
                .resolutionId(row.getResolutionId())
                .resolutionPostId(row.getResolutionPostId())
                .followerCount(row.getFollowerCount() == null ? 0 : row.getFollowerCount())
                .followed(row.getFollowed() != null && row.getFollowed() == 1)
                .matchReasons(List.copyOf(reasons))
                .createTime(row.getCreateTime())
                .updateTime(row.getUpdateTime())
                .build();
    }

    private static NeedDiscoverySort normalizeSort(String value) {
        try {
            return NeedDiscoverySort.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "sort is invalid");
        }
    }

    private static Integer normalizeDomain(Integer domain) {
        if (domain == null) {
            return null;
        }
        if (!PostDomain.isValid(domain)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "domain is invalid");
        }
        return domain;
    }

    private static String normalizeEnumFilter(String value, List<String> allowed, String name) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), name + " is invalid");
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

    private record DiscoveryCursor(LocalDateTime time, Long id, Integer stalled,
                                   NeedDiscoverySort sort) {

        private static DiscoveryCursor parse(String value, NeedDiscoverySort sort) {
            if (!StringUtils.hasText(value) || "0".equals(value.trim())) {
                return new DiscoveryCursor(null, null, null, sort);
            }
            try {
                String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
                String[] parts = raw.split("\\|", -1);
                if (parts.length != 3) {
                    throw new IllegalArgumentException("cursor shape");
                }
                long id = Long.parseLong(parts[1]);
                int stalled = Integer.parseInt(parts[2]);
                if (id <= 0 || stalled < 0 || stalled > 1) {
                    throw new IllegalArgumentException("cursor values");
                }
                return new DiscoveryCursor(LocalDateTime.parse(parts[0]), id, stalled, sort);
            } catch (RuntimeException ex) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "cursor is invalid");
            }
        }

        private static DiscoveryCursor from(NeedDiscoveryRows.NeedRow row, NeedDiscoverySort sort) {
            if (row == null || row.getId() == null || row.getUpdateTime() == null
                    || row.getCreateTime() == null) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                        "discovery cursor cannot be generated");
            }
            LocalDateTime time = sort == NeedDiscoverySort.LATEST
                    ? row.getCreateTime() : row.getUpdateTime();
            int stalled = row.getStalled() != null && row.getStalled() == 1 ? 1 : 0;
            return new DiscoveryCursor(time, row.getId(), stalled, sort);
        }

        private String encode() {
            String raw = time + "|" + id + "|" + (stalled == null ? 0 : stalled);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }
}

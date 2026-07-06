package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.CreatorCurationFeedbackDTO;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.post.api.CreatorCurationFeedbackFacade;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.event.OperationCurationSelectedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CreatorCurationFeedbackService {

    private static final int RECENT_LIMIT = 5;
    private static final String NO_FEEDBACK = "NO_FEEDBACK";
    private static final String FEEDBACK_SOURCE_UNAVAILABLE = "FEEDBACK_SOURCE_UNAVAILABLE";

    private final CreatorCurationFeedbackFacade creatorCurationFeedbackFacade;

    public CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO summary(Long uid) {
        requireUser(uid);
        List<CreatorCurationFeedbackDTO> items;
        try {
            items = creatorCurationFeedbackFacade.listCreatorCurationFeedback(uid, 20).stream()
                    .filter(CreatorCurationFeedbackService::isTrustedPublicFeedback)
                    .map(this::toDto)
                    .sorted(Comparator.comparing(CreatorCurationFeedbackDTO::getTriggeredAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } catch (RuntimeException ignored) {
            return CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO.builder()
                    .updatedAt(LocalDateTime.now())
                    .degraded(true)
                    .fallbackReason(FEEDBACK_SOURCE_UNAVAILABLE)
                    .total(0)
                    .items(List.of())
                    .recentItems(List.of())
                    .build();
        }
        return CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO.builder()
                .updatedAt(LocalDateTime.now())
                .degraded(false)
                .fallbackReason(items.isEmpty() ? NO_FEEDBACK : null)
                .total(items.size())
                .items(items)
                .recentItems(items.stream().limit(RECENT_LIMIT).toList())
                .build();
    }

    private CreatorCurationFeedbackDTO toDto(com.offerlab.community.post.api.dto.OperationCurationFeedbackDTO item) {
        return CreatorCurationFeedbackDTO.builder()
                .eventType(OperationCurationSelectedEvent.OPERATION_CURATION_SELECTED)
                .eventId(item.getPlacementType() + ":" + item.getPlacementId() + ":" + item.getContentId())
                .contentId(item.getContentId())
                .contentTitle(item.getContentTitle())
                .placementType(item.getPlacementType())
                .placementId(item.getPlacementId())
                .placementLabel(item.getPlacementKey())
                .topicSlug("TOPIC".equals(item.getPlacementType()) ? item.getPlacementKey() : null)
                .sectionKey(item.getSectionKey())
                .reasonText(item.getReason())
                .href(sanitizeFeedbackHref(item.getEntrance(), item.getContentId()))
                .triggeredAt(item.getUpdateTime())
                .status(item.getStatus())
                .source("operation-curation")
                .visibilityScope("public_only")
                .governanceStatus("public_governed")
                .publicVisible(true)
                .anonymousProtected(true)
                .publicMetrics(CreatorCurationFeedbackDTO.CreatorCurationMetricsDTO.builder()
                        .viewCount(0L)
                        .likeCount(0L)
                        .favoriteCount(0L)
                        .commentCount(0L)
                        .build())
                .build();
    }

    private static boolean isTrustedPublicFeedback(com.offerlab.community.post.api.dto.OperationCurationFeedbackDTO item) {
        if (item == null || item.getContentId() == null || item.getContentId() <= 0) {
            return false;
        }
        if (!"PUBLISHED".equals(normalize(item.getStatus()))) {
            return false;
        }
        if (!"SLOT".equals(normalize(item.getPlacementType())) && !"TOPIC".equals(normalize(item.getPlacementType()))) {
            return false;
        }
        return isSafePublicText(item.getContentTitle())
                && isSafePublicText(item.getPlacementKey())
                && isSafePublicText(item.getSectionKey())
                && isSafePublicText(item.getReason());
    }

    private static boolean isSafePublicText(String value) {
        return !PublicContentFilter.isSyntheticText(value)
                && !PublicContentFilter.isUnsafeSuggestionText(value);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private static String sanitizeFeedbackHref(String href, Long contentId) {
        if (href != null && href.startsWith("/") && !href.startsWith("//") && !href.startsWith("/api/") && !href.contains(" ")) {
            return href;
        }
        return contentId == null ? null : "/post/" + contentId;
    }

    private static void requireUser(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }
}

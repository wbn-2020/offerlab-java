package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.CreatorCurationFeedbackDTO;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.post.api.CreatorCurationFeedbackFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreatorCurationFeedbackService {

    private static final int RECENT_LIMIT = 5;
    private static final String NO_FEEDBACK = "NO_FEEDBACK";

    private final CreatorCurationFeedbackFacade creatorCurationFeedbackFacade;

    public CreatorCurationFeedbackDTO.CreatorCurationFeedbackSummaryDTO summary(Long uid) {
        requireUser(uid);
        List<CreatorCurationFeedbackDTO> items = creatorCurationFeedbackFacade.listCreatorCurationFeedback(uid, 20).stream()
                .map(this::toDto)
                .sorted(Comparator.comparing(CreatorCurationFeedbackDTO::getTriggeredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
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
                .publicMetrics(CreatorCurationFeedbackDTO.CreatorCurationMetricsDTO.builder()
                        .viewCount(0L)
                        .likeCount(0L)
                        .favoriteCount(0L)
                        .commentCount(0L)
                        .build())
                .build();
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

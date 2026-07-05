package com.offerlab.community.analytics.application;

import com.offerlab.community.post.api.CreatorCurationFeedbackFacade;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatorCurationFeedbackServiceTest {

    @Test
    void summaryUsesStableEmptyStateWhenNoPublishedAuthorVisibleFeedbackExists() {
        CreatorCurationFeedbackService service = new CreatorCurationFeedbackService((authorUid, limit) -> List.of());

        var summary = service.summary(18L);

        assertFalse(summary.isDegraded());
        assertEquals("NO_FEEDBACK", summary.getFallbackReason());
        assertEquals(0, summary.getTotal());
        assertTrue(summary.getItems().isEmpty());
        assertTrue(summary.getRecentItems().isEmpty());
    }

    @Test
    void summaryKeepsActiveStateWhenFeedbackExists() {
        CreatorCurationFeedbackFacade facade = (authorUid, limit) -> List.of(
                com.offerlab.community.post.api.dto.OperationCurationFeedbackDTO.builder()
                        .contentId(1001L)
                        .contentTitle("Spring cache fallback review")
                        .placementType("SLOT")
                        .placementId(2001L)
                        .placementKey("HOME_FEATURED")
                        .reason("Selected for a public operation slot.")
                        .entrance("/api/v1/creator-growth/curation-feedback?placementType=SLOT&placementKey=HOME_FEATURED")
                        .status("PUBLISHED")
                        .updateTime(LocalDateTime.of(2026, 7, 5, 10, 0))
                        .build()
        );
        CreatorCurationFeedbackService service = new CreatorCurationFeedbackService(facade);

        var summary = service.summary(18L);

        assertFalse(summary.isDegraded());
        assertNull(summary.getFallbackReason());
        assertEquals(1, summary.getTotal());
        assertEquals("operation-curation", summary.getItems().get(0).getSource());
    }

    @Test
    void summarySortsRecentItemsByTriggeredAtDescending() {
        CreatorCurationFeedbackFacade facade = (authorUid, limit) -> List.of(
                feedback(1001L, LocalDateTime.of(2026, 7, 5, 9, 0)),
                feedback(1002L, LocalDateTime.of(2026, 7, 5, 11, 0)),
                feedback(1003L, LocalDateTime.of(2026, 7, 5, 10, 0))
        );
        CreatorCurationFeedbackService service = new CreatorCurationFeedbackService(facade);

        var summary = service.summary(18L);

        assertEquals(1002L, summary.getItems().get(0).getContentId());
        assertEquals(1002L, summary.getRecentItems().get(0).getContentId());
        assertEquals(1003L, summary.getRecentItems().get(1).getContentId());
        assertEquals(1001L, summary.getRecentItems().get(2).getContentId());
    }

    private static com.offerlab.community.post.api.dto.OperationCurationFeedbackDTO feedback(Long contentId,
                                                                                           LocalDateTime updateTime) {
        return com.offerlab.community.post.api.dto.OperationCurationFeedbackDTO.builder()
                .contentId(contentId)
                .contentTitle("Public curation feedback " + contentId)
                .placementType("SLOT")
                .placementId(2001L)
                .placementKey("HOME_FEATURED")
                .reason("Selected for a public operation slot.")
                .entrance("/growth/profile?section=curation-feedback")
                .status("PUBLISHED")
                .updateTime(updateTime)
                .build();
    }
}

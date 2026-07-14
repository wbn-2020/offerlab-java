package com.offerlab.community.interaction.application;

import com.offerlab.community.interaction.api.dto.CommentReportDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentReportQueueActionHandlerTest {

    @Test
    void approvedQueueActionApprovesCommentReport() {
        CapturingCommentReportService reportService = new CapturingCommentReportService();
        CommentReportQueueActionHandler handler = new CommentReportQueueActionHandler(reportService);

        assertTrue(handler.supports("COMMENT_REPORT"));
        handler.handle("COMMENT_REPORT", 9101L, "approved", "approved", "confirmed violation", 77L);

        assertEquals(9101L, reportService.reportId);
        assertEquals(77L, reportService.reviewerUid);
        assertTrue(reportService.approved);
        assertEquals("confirmed violation", reportService.note);
    }

    @Test
    void rejectedQueueActionRejectsCommentReport() {
        CapturingCommentReportService reportService = new CapturingCommentReportService();
        CommentReportQueueActionHandler handler = new CommentReportQueueActionHandler(reportService);

        handler.handle("COMMENT_REPORT", 9102L, "rejected", "rejected", "not a violation", 78L);

        assertEquals(9102L, reportService.reportId);
        assertEquals(78L, reportService.reviewerUid);
        assertFalse(reportService.approved);
        assertEquals("not a violation", reportService.note);
    }

    private static final class CapturingCommentReportService extends CommentReportService {
        private Long reportId;
        private Long reviewerUid;
        private Boolean approved;
        private String note;

        private CapturingCommentReportService() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public CommentReportDTO reviewReport(Long reportId, Long reviewerUid, Boolean approved, String note) {
            this.reportId = reportId;
            this.reviewerUid = reviewerUid;
            this.approved = approved;
            this.note = note;
            return CommentReportDTO.builder().id(reportId).reportStatus(approved ? STATUS_APPROVED : STATUS_REJECTED).build();
        }
    }
}

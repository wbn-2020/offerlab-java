package com.offerlab.community.post.application;

import com.offerlab.community.post.api.dto.PostReportDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostReportQueueActionHandlerTest {

    @Test
    void approvedQueueActionApprovesPostReport() {
        CapturingPostReportService reportService = new CapturingPostReportService();
        PostReportQueueActionHandler handler = new PostReportQueueActionHandler(reportService);

        assertTrue(handler.supports("POST_REPORT"));
        handler.handle("POST_REPORT", 9001L, "approved", "approved", "confirmed violation", 77L);

        assertEquals(9001L, reportService.reportId);
        assertEquals(77L, reportService.reviewerUid);
        assertTrue(reportService.approved);
        assertEquals("confirmed violation", reportService.note);
    }

    @Test
    void rejectedQueueActionRejectsPostReport() {
        CapturingPostReportService reportService = new CapturingPostReportService();
        PostReportQueueActionHandler handler = new PostReportQueueActionHandler(reportService);

        handler.handle("POST_REPORT", 9002L, "rejected", "rejected", "not a violation", 78L);

        assertEquals(9002L, reportService.reportId);
        assertEquals(78L, reportService.reviewerUid);
        assertFalse(reportService.approved);
        assertEquals("not a violation", reportService.note);
    }

    private static final class CapturingPostReportService extends PostReportService {
        private Long reportId;
        private Long reviewerUid;
        private Boolean approved;
        private String note;

        private CapturingPostReportService() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public PostReportDTO reviewReport(Long reportId, Long reviewerUid, Boolean approved, String note) {
            this.reportId = reportId;
            this.reviewerUid = reviewerUid;
            this.approved = approved;
            this.note = note;
            return PostReportDTO.builder().id(reportId).reportStatus(approved ? STATUS_APPROVED : STATUS_REJECTED).build();
        }
    }
}

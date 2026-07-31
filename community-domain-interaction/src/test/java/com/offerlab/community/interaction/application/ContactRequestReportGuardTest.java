package com.offerlab.community.interaction.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactRequestReportGuardTest {

    @Test
    void reportingSurvivesSourceRemovalWhileCreationStaysValidated() throws Exception {
        String service = Files.readString(
                Path.of("src/main/java/com/offerlab/community/interaction/application/ContactRequestService.java"),
                StandardCharsets.UTF_8);

        // Creation must still validate the source binding.
        assertTrue(service.contains("sourceId = validateSourceBinding(requesterUid, receiverUid, sourceType, sourceId);"),
                "contact request creation must keep validating the source binding");

        // The report entry must verify receiver ownership itself.
        int reportStart = service.indexOf("public ContactRequestDTO report(");
        assertTrue(reportStart >= 0, "report entry must exist");
        String reportBody = service.substring(reportStart, service.indexOf("private ContactRequestDTO handle(", reportStart));
        assertTrue(reportBody.contains("po.getReceiverUid(), receiverUid"),
                "report entry must verify the caller is the receiver");

        // The report path must NOT re-validate source visibility: a deleted or
        // privatized source must never block an abuse report.
        int createReportStart = service.indexOf("private Long createReportForContactRequest(");
        assertTrue(createReportStart >= 0, "report creation helper must exist");
        String createReportBody = service.substring(createReportStart,
                service.indexOf("private void publishContactRequestQueueItem(", createReportStart));
        assertFalse(createReportBody.contains("validateSourceBinding("),
                "reporting must survive source deletion; do not re-validate source visibility in the report path");
    }
}

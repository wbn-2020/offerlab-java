package com.offerlab.community.post.collaboration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the claimant fulfillment loop: submit -> accept / reject / withdraw.
 * These are source-contract assertions (no Spring context) mirroring MyClaimedNeedsGuardTest.
 */
class NeedFulfillmentLoopGuardTest {

    @Test
    void submitAcceptRejectWithdrawMustStayConsistent() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/post/collaboration/controller/CollaborationController.java");
        String service = read("src/main/java/com/offerlab/community/post/collaboration/application/CollaborationService.java");
        String mapper = read("src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/CollaborationMapper.java");
        String models = read("src/main/java/com/offerlab/community/post/collaboration/api/CollaborationModels.java");
        String rows = read("src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/CollaborationRows.java");
        String acceptMethod = slice(service, "public NeedDTO acceptNeed(", "public NeedDTO rejectNeed(");
        String rejectMethod = slice(service, "public NeedDTO rejectNeed(", "public NeedDTO withdrawNeed(");
        String migration = Files.readString(
                Path.of("../db/migration/20260717_collab_need_submission.sql").normalize(),
                StandardCharsets.UTF_8);

        // --- Endpoints: all four are authenticated + rate-limited writes ---
        assertContains(controller, "@PostMapping(\"/needs/{needId}/submit\")");
        assertContains(controller, "@PostMapping(\"/needs/{needId}/accept\")");
        assertContains(controller, "@PostMapping(\"/needs/{needId}/reject\")");
        assertContains(controller, "@PostMapping(\"/needs/{needId}/withdraw\")");
        assertContains(controller, "service.submitNeed(needId,");
        assertContains(controller, "service.acceptNeed(needId,");
        assertContains(controller, "service.rejectNeed(needId,");
        assertContains(controller, "service.withdrawNeed(needId,");
        assertContains(controller, "'collaboration:need:submit:' + #uid");
        assertContains(controller, "'collaboration:need:accept:' + #uid");
        assertContains(controller, "'collaboration:need:reject:' + #uid");
        assertContains(controller, "'collaboration:need:withdraw:' + #uid");

        // --- Reward invariant: accept re-emits the existing NEED_FULFILLED reward event,
        //     NOT a new bespoke reward path. Actor=creator, contributor=claimant. ---
        assertContains(acceptMethod, "requireIndependentNeedReviewer(claimant, uid)");
        assertContains(rejectMethod,
                "requireIndependentNeedReviewer(need.getClaimedByUid(), uid)");
        assertContains(service, "claimants cannot review their own submission");
        assertContains(acceptMethod,
                "if (!Objects.equals(resolution.contributorUid(), uid)");
        assertContains(acceptMethod,
                "publishNeedFulfilled(resolution.contributorUid(), need, resolution, \"NEED_FULFILLED\"");
        assertContains(acceptMethod,
                "publishNeedFulfilled(null, need, resolution, \"NEED_FULFILLED_SYNC\"");
        assertContains(service, "mapper.completeNeed(need.getId(), resolution.type(), resolution.id(),");
        assertContains(service, "if (!\"OPEN\".equals(need.getStatus()) && !\"CLAIMED\".equals(need.getStatus()))");

        // --- Authority split: claimant submits/withdraws, creator or moderator accepts/rejects ---
        assertContains(service, "only the claimant can submit content for this need");
        assertContains(service, "only the claimant can withdraw this submission");
        assertContains(service, "!Objects.equals(need.getSubmittedByUid(), uid)");
        assertContains(service, "!Objects.equals(need.getSubmittedByUid(), claimant)");
        // accept & reject go through requireManage (creator or moderator)
        assertContains(service, "requireManage(need.getCreatorUid(), need.getDomain(), uid)");

        // --- State machine SQL guards ---
        assertContains(mapper, "int submitNeed(");
        assertContains(mapper, "int revertSubmittedToClaimed(");
        // submit only from CLAIMED by the claimant
        assertContains(mapper, "AND need_status = 'CLAIMED'");
        assertContains(mapper, "AND claimed_by_uid = #{uid}");
        // completeNeed WHERE was widened to allow SUBMITTED (so accept can reuse it)
        assertContains(mapper, "AND need_status IN ('OPEN', 'CLAIMED', 'SUBMITTED')");
        // revert stays SUBMITTED -> CLAIMED
        assertTrue(mapper.contains("SET need_status = 'CLAIMED',\n                <if test=\"rejectReason != null\">"),
                "revertSubmittedToClaimed must move SUBMITTED back to CLAIMED and record reject_reason");
        assertContains(mapper, "AND submitted_by_uid = #{submittedByUid}");

        // --- DTO + persistence staging contract ---
        for (String field : new String[]{
                "submittedByUid", "submittedAt", "submissionResolutionType",
                "submissionResolutionId", "submissionNote", "rejectReason"}) {
            assertContains(models, field);
            assertContains(rows, field);
        }
        for (String column : new String[]{
                "submitted_by_uid", "submitted_at", "submission_resolution_type",
                "submission_resolution_id", "submission_note", "reject_reason"}) {
            assertContains(migration, "ADD COLUMN " + column);
        }
        assertFalse(migration.contains("DROP ") || migration.contains("DELETE ") || migration.contains("UPDATE "),
                "the submission migration must remain additive");

        // --- Claimant may not self-accept even when the claimant also has moderator authority. ---
        assertFalse(acceptMethod.contains("only the claimant can accept"),
                "acceptance must never be a claimant self-service action");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Expected source to contain: " + expected);
    }

    private static String slice(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertTrue(startIndex >= 0 && endIndex > startIndex,
                () -> "Expected source section: " + start + " ... " + end);
        return source.substring(startIndex, endIndex);
    }
}

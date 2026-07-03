package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationCurationGuardTest {

    @Test
    void operationCurationMustStayGovernedAuditedAndNonCommercial() throws Exception {
        String service = read("src/main/java/com/offerlab/community/post/application/OperationCurationService.java");
        String controller = read("src/main/java/com/offerlab/community/post/controller/OperationCurationController.java");
        String mapper = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java");
        String migration = read("../db/migration/20260703_operation_curation.sql");

        assertTrue(controller.contains("@RequestMapping(\"/api/v1/operations\")"),
                "operation orchestration API route must stay explicit");
        assertTrue(controller.contains("adminPermissionService.requireStrictAdmin(uid)"),
                "admin operation endpoints must reject non-admin operators, including scoped non-admin roles");
        assertTrue(controller.contains("adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_OPS)"),
                "publish/offline/rollback must require ops permission after strict admin entry");
        assertTrue(count(controller, "requireOpsMutation()") >= 3,
                "publish/offline/rollback endpoints must all use the ops mutation guard");
        assertTrue(controller.contains("RiskConfirmation.requireCritical(req == null ? null : req.getNote(),"),
                "publish/offline/rollback actions must require a critical confirmation note and phrase");
        assertTrue(count(controller, "requireCriticalNote(req)") >= 3,
                "publish/offline/rollback endpoints must all use the critical confirmation guard");
        assertTrue(controller.contains("private String confirmationPhrase;"),
                "lifecycle action payload must carry the critical confirmation phrase");
        assertTrue(controller.contains("/admin/audit-logs"),
                "operation orchestration must expose a bounded admin audit view");

        assertTrue(service.contains("adminAuditService.recordRequired"), "operation writes must require admin audit");
        assertTrue(service.contains("PublicContentFilter.isDistributablePost"), "operation display must re-check public content");
        assertTrue(service.contains("PublicContentFilter.isUnsafeSuggestionText(value)"),
                "operation display copy must reject unsafe or commercialized wording");
        assertTrue(service.contains("filterTopicSnapshot(copyTopicSnapshot(before))"),
                "publishing must filter a copy so audit before snapshots are not mutated in place");
        assertTrue(service.contains("po.setRollbackSnapshotJson(writeJson(before))"),
                "rollback must preserve the replaced published snapshot as the next rollback baseline");
        assertTrue(service.contains(".previewToken(publicOnly ? null : slot.getPreviewToken())"),
                "public operation slots must not leak preview tokens");
        assertTrue(service.contains(".previewToken(publicOnly ? null : topic.getPreviewToken())"),
                "public operation topics must not leak preview tokens from live DTOs");
        assertTrue(service.contains("snapshot.setPreviewToken(null)"),
                "public operation topic snapshots must not leak preview tokens");
        assertTrue(service.contains("MAX_OPERATION_SLOTS = 2"), "P0 must cap operation slots to one or two slots");
        assertTrue(service.contains("MIN_SLOT_LIMIT = 3"), "slot display must default to at least three items");
        assertTrue(service.contains("MAX_SLOT_LIMIT = 5"), "slot display must cap at five items");

        assertTrue(mapper.contains("p.is_deleted = 0"), "candidate SQL must exclude deleted posts");
        assertTrue(mapper.contains("p.post_status = 1"), "candidate SQL must exclude reviewing or taken-down posts");
        assertTrue(mapper.contains("p.visibility = 1"), "candidate SQL must exclude private or restricted posts");

        assertTrue(migration.contains("t_operation_curation_item"), "migration must persist curation pool items");
        assertTrue(migration.contains("t_operation_slot"), "migration must persist operation slots");
        assertTrue(migration.contains("t_operation_topic"), "migration must persist operation topics");

        String combined = (service + controller + migration
                + read("src/main/java/com/offerlab/community/post/api/dto/OperationCandidateDTO.java")
                + read("src/main/java/com/offerlab/community/post/api/dto/OperationCurationItemDTO.java")
                + read("src/main/java/com/offerlab/community/post/api/dto/OperationSlotDTO.java")
                + read("src/main/java/com/offerlab/community/post/api/dto/OperationSlotItemDTO.java")
                + read("src/main/java/com/offerlab/community/post/api/dto/OperationTopicDTO.java")
                + read("src/main/java/com/offerlab/community/post/api/dto/OperationTopicSectionDTO.java")).toLowerCase();
        for (String forbidden : new String[] {
                "bid",
                "budget",
                "revenue",
                "member benefit",
                "advertising slot",
                "sponsor slot",
                "paid pin"
        }) {
            assertFalse(combined.matches("(?s).*\\b" + forbidden + "\\b.*"),
                    "operation workflow must not introduce commercial capability term: " + forbidden);
        }
        for (String forbidden : new String[] {
                "限时购买",
                "会员专享",
                "官方背书",
                "平台担保",
                "赞助推荐",
                "付费置顶",
                "权威认证",
                "保证有效",
                "商业合作",
                "广告位",
                "赞助位",
                "商业推荐",
                "会员权益",
                "收益分成"
        }) {
            assertFalse(combined.contains(forbidden.toLowerCase()),
                    "operation workflow must not introduce commercial copy or capability term: " + forbidden);
        }
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static int count(String source, String needle) {
        int total = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            total++;
            index += needle.length();
        }
        return total;
    }
}

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
        assertTrue(service.contains(".reasonText(publicCurationReason(null, section.getNote()))"),
                "public operation topic sections must expose safe curation reasons separately from admin notes");
        assertTrue(service.contains("section.setReasonText(publicCurationReason(null, section.getReasonText(), section.getNote()))"),
                "published operation topic snapshots must preserve curation reasons while filtering admin notes");
        assertTrue(service.contains("MAX_OPERATION_SLOTS = 2"), "P0 must cap operation slots to one or two slots");
        assertTrue(service.contains("MIN_SLOT_LIMIT = 3"), "slot display must default to at least three items");
        assertTrue(service.contains("MAX_SLOT_LIMIT = 5"), "slot display must cap at five items");
        assertTrue(service.contains("HOME_FEATURED_SLOT_CODE = \"HOME_FEATURED\""),
                "V3 must keep the first-stage HOME_FEATURED public curation slot");
        assertTrue(service.contains("DISCOVERY_FEATURED_TOPICS_SLOT_CODE = \"DISCOVERY_FEATURED_TOPICS\""),
                "V3 phase 2 must add the discovery featured topics slot");
        assertTrue(service.contains("SUPPORTED_OPERATION_SLOT_CODES"),
                "public operation slots must stay behind an explicit allowlist");
        assertTrue(service.contains("V3 P0 only supports HOME_FEATURED and DISCOVERY_FEATURED_TOPICS"),
                "unsupported operation slots must fail closed instead of becoming dynamic CMS slots");
        assertTrue(service.contains("publishSlot("), "HOME_FEATURED slot must have a dedicated publish workflow");
        assertTrue(service.contains("offlineSlot("), "HOME_FEATURED slot must have a dedicated offline workflow");
        assertTrue(service.contains("rollbackSlot("), "HOME_FEATURED slot must have a dedicated rollback workflow");
        assertTrue(service.contains("requireSlotForUpdate(slotId)")
                        && service.contains("requireSlotForUpdate(existing.getSlotId())"),
                "slot lifecycle and item arrangement writes must serialize on the owning slot row");
        assertTrue(service.contains("publishIfCurrentVersion("),
                "slot publish must retain a compare-and-set guard after acquiring the slot row lock");
        assertTrue(service.contains("getPublishedSnapshotJson()"),
                "HOME_FEATURED public reads must use a published snapshot instead of mutable draft rows");
        assertTrue(service.contains("publicSlotItemForViewer"),
                "HOME_FEATURED public reads must validate and hydrate each item in one controlled pass");
        assertTrue(service.contains("safeLoadPublicSlotPost"),
                "HOME_FEATURED public snapshot filtering must drop deleted or unavailable posts instead of failing open or throwing");
        assertTrue(service.contains("String lifecycleStatus = existing == null ? STATUS_DRAFT : existing.getSlotStatus()"),
                "ordinary slot upsert must preserve existing HOME_FEATURED lifecycle status");
        assertTrue(service.contains("po.setSlotStatus(lifecycleStatus)"),
                "ordinary slot upsert must not publish, preview, or offline HOME_FEATURED directly");
        assertTrue(controller.contains("/admin/slots/{slotId}/publish"),
                "controller must expose a dedicated HOME_FEATURED slot publish endpoint");
        assertTrue(controller.contains("/admin/slots/{slotId}/offline"),
                "controller must expose a dedicated HOME_FEATURED slot offline endpoint");
        assertTrue(controller.contains("/admin/slots/{slotId}/rollback"),
                "controller must expose a dedicated HOME_FEATURED slot rollback endpoint");
        assertTrue(controller.contains("operationCurationService.upsertSlot(cmd, requireOpsMutation())")
                        && controller.contains("operationCurationService.upsertSlotItem(slotId, cmd, requireOpsMutation())")
                        && controller.contains("operationCurationService.deleteSlotItem(itemId, requireOpsMutation(), note)"),
                "HOME_FEATURED slot and item arrangement writes must require ops mutation permission, not admin entry alone");
        assertTrue(count(controller, "operationCurationService.publishSlot") >= 1
                        && count(controller, "operationCurationService.offlineSlot") >= 1
                        && count(controller, "operationCurationService.rollbackSlot") >= 1,
                "slot lifecycle endpoints must delegate to service lifecycle methods");

        String discoveryController = read("src/main/java/com/offerlab/community/post/controller/DiscoveryMapController.java");
        String discoveryService = read("src/main/java/com/offerlab/community/post/application/DiscoveryMapService.java");
        String discoveryDto = read("src/main/java/com/offerlab/community/post/api/dto/DiscoveryMapDTO.java");
        String v3SlotMigration = read("../db/migration/20260705_v3_home_featured_slot.sql");
        assertTrue(discoveryController.contains("@PublicApi")
                        && discoveryController.contains("@RequestMapping(\"/api/v1/discovery\")")
                        && discoveryController.contains("@GetMapping(\"/map\")"),
                "DiscoveryMap must expose a dedicated public /api/v1/discovery/map endpoint");
        assertTrue(discoveryService.contains("DISCOVERY_FEATURED_TOPICS_SLOT_CODE"),
                "DiscoveryMap must read the dedicated DISCOVERY_FEATURED_TOPICS operation slot");
        assertTrue(discoveryService.contains("SOURCE_OPERATION_CURATION = \"operation-curation\"")
                        && discoveryService.contains("SOURCE_COMMUNITY_TOPIC = \"community-topic\"")
                        && discoveryService.contains("SOURCE_SEARCH_ANALYTICS = \"search-analytics\"")
                        && discoveryService.contains("SOURCE_PUBLIC_CONTENT_QUERY = \"public-content-query\"")
                        && discoveryService.contains("SOURCE_FALLBACK_DEMO = \"fallback-demo\"")
                        && discoveryService.contains("SOURCE_UNAVAILABLE = \"unavailable\""),
                "DiscoveryMap source taxonomy must include displayable and non-displayable states");
        assertTrue(discoveryService.contains("isDisplayableItem"),
                "DiscoveryMap real item arrays must filter non-displayable sources");
        assertTrue(discoveryService.contains("hasSafeHref(item.getHref())"),
                "DiscoveryMap real item arrays must require safe internal hrefs");
        assertTrue(discoveryService.contains("FORBIDDEN_DISCOVERY_TEXT")
                        && discoveryService.contains("CodeCoachAI")
                        && discoveryService.contains("mockInterview")
                        && discoveryService.contains("resumeMatch")
                        && discoveryService.contains("applicationTask")
                        && discoveryService.contains("私人训练")
                        && discoveryService.contains("模拟面试")
                        && discoveryService.contains("简历/JD")
                        && discoveryService.contains("投递任务"),
                "DiscoveryMap backend must also filter private training and CodeCoachAI copy before returning public items");
        assertTrue(discoveryService.contains("normalized.contains(\"fixture\")")
                        && discoveryService.contains("normalized.contains(\"local_demo\")"),
                "DiscoveryMap href filtering must reject demo, fallback, fixture, and local demo paths");
        assertTrue(discoveryDto.contains("private Map<String, DiscoveryModuleDTO> modules;"),
                "DiscoveryMap must expose module status separately from real items");
        assertTrue(discoveryDto.contains("private List<DiscoveryItemDTO> contentForms;")
                        && discoveryService.contains(".contentForms(contentForms)")
                        && discoveryService.contains("modules.put(\"contentForms\""),
                "DiscoveryMap must expose the five content forms as a first-class public module");
        assertTrue(discoveryService.contains("catch (BizException e)")
                        && count(discoveryService, "return List.of();") >= 2,
                "DiscoveryMap module failures must degrade to empty modules instead of failing the whole public map");
        assertTrue(v3SlotMigration.contains("'DISCOVERY_FEATURED_TOPICS'")
                        && v3SlotMigration.contains("'DRAFT'")
                        && v3SlotMigration.contains("no demo, fixture, fallback, or published content is seeded")
                        && !v3SlotMigration.contains("INSERT INTO t_operation_slot_item"),
                "Phase 2 migration may create only an empty draft DISCOVERY_FEATURED_TOPICS slot");

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

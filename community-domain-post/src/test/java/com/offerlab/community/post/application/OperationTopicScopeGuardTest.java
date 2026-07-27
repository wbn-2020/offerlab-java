package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationTopicScopeGuardTest {

    @Test
    void topicScopeStaysExplicitAndNeverFallsBackToAConcreteChannel() throws Exception {
        String service = read("src/main/java/com/offerlab/community/post/application/OperationCurationService.java");
        String dto = read("src/main/java/com/offerlab/community/post/api/dto/OperationTopicDTO.java");

        assertTrue(dto.contains("private String topicScope;"),
                "OperationTopicDTO must expose the read-only derived topicScope field");

        assertTrue(service.contains("SCOPE_DOMAIN = \"DOMAIN\"")
                        && service.contains("SCOPE_CROSS_DOMAIN = \"CROSS_DOMAIN\""),
                "topic scope must be a two-state explicit contract (DOMAIN / CROSS_DOMAIN)");
        assertTrue(service.contains("return domain == null ? SCOPE_CROSS_DOMAIN : SCOPE_DOMAIN;"),
                "topicScope must derive from domain only, with null meaning explicit cross-domain");
        assertTrue(service.contains(".topicScope(topicScopeOf(topic.getDomain()))"),
                "live topic DTO assembly must emit the derived topicScope");
        assertTrue(service.contains("snapshot.setTopicScope(topicScopeOf(snapshot.getDomain()))"),
                "snapshot reads must re-derive topicScope so legacy snapshots stay consistent");

        assertTrue(service.contains("BLOCK_DOMAIN_MISMATCH_FOR_SCOPED_TOPIC = \"DOMAIN_MISMATCH_FOR_SCOPED_TOPIC\"")
                        && service.contains("BLOCK_POST_DOMAIN_UNKNOWN = \"POST_DOMAIN_UNKNOWN\""),
                "candidate domain block reasons must exist as explicit constants");
        assertTrue(service.contains("return List.of(BLOCK_POST_DOMAIN_UNKNOWN)")
                        && service.contains("return List.of(BLOCK_DOMAIN_MISMATCH_FOR_SCOPED_TOPIC)"),
                "the shared scope validator must return both explicit block reasons");
        assertTrue(service.contains("blockReasons.addAll(topicScopeBlockReasons(topic.getDomain(), post))")
                        && service.contains("List<String> scopeBlockReasons = topicScopeBlockReasons(topic.getDomain(), candidate.getPost())"),
                "both hint and query candidates must use the shared topic scope validator");
        assertTrue(service.contains("validateTopicSectionCommands(po, cmd.getSections())")
                        && service.contains("requireStoredTopicSectionsWithinScope(po)"),
                "create/update writes and scope narrowing must validate the final topic contents");
        assertTrue(service.contains("isTopicSnapshotScopeConsistent(preview)")
                        && service.contains("requireTopicSnapshotScopeConsistent(before")
                        && service.contains("topicScopeBlockReasons(snapshot.getDomain(), post)"),
                "publish checks, publish, and public snapshot filtering must enforce the same scope contract");

        // A null topic/post domain must never be silently mapped onto a concrete channel.
        assertFalse(service.matches("(?s).*==\\s*null\\s*\\?\\s*Post\\.DOMAIN_.*"),
                "a null domain must never fall back to a concrete channel constant");
        assertFalse(service.contains("DOMAIN_TECH : topic.getDomain()")
                        || service.contains("DOMAIN_TECH : post.getDomain()"),
                "no TECH fallback for missing domains in curation paths");
    }

    @Test
    void draftConcurrencyAndSectionIdentityContractsStayWired() throws Exception {
        String service = read("src/main/java/com/offerlab/community/post/application/OperationCurationService.java");
        String po = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/po/OperationTopicPO.java");
        String controller = read("src/main/java/com/offerlab/community/post/controller/OperationCurationController.java");
        Path root = repositoryRoot();
        String mybatisConfig = Files.readString(
                root.resolve("community-infrastructure/src/main/java/com/offerlab/community/infra/"
                        + "mybatis/config/MybatisPlusConfig.java"),
                StandardCharsets.UTF_8);
        String migrationCheck = Files.readString(
                root.resolve("community-infrastructure/src/main/java/com/offerlab/community/infra/"
                        + "db/MigrationCheckService.java"),
                StandardCharsets.UTF_8);
        String migration = Files.readString(
                root.resolve("db/migration/20260727_operation_topic_draft_revision.sql"),
                StandardCharsets.UTF_8);
        String flyway = Files.readString(
                root.resolve("community-bootstrap/src/main/resources/db/flyway/core/"
                        + "V20260727.01__operation_topic_draft_revision.sql"),
                StandardCharsets.UTF_8);

        assertTrue(po.contains("@Version") && po.contains("private Integer draftRevision;"),
                "draftRevision must remain a MyBatis-Plus optimistic-lock field");
        assertTrue(mybatisConfig.contains("new OptimisticLockerInnerInterceptor()"),
                "the production MyBatis chain must install optimistic locking");
        assertTrue(service.contains("if (topicMapper.updateById(topic) != 1)")
                        && service.contains("throw concurrentTopicModification();")
                        && service.contains("draftRevisionOf(topic) != previousRevision + 1"),
                "all draft writes must fail closed when the mapper CAS affects no row");
        int updateStart = service.indexOf("public OperationTopicDTO updateTopic(");
        int updateEnd = service.indexOf("\n    @Transactional", updateStart + 1);
        String updateMethod = service.substring(updateStart, updateEnd);
        assertTrue(updateMethod.indexOf("updateTopicWithDraftRevision(po);")
                        < updateMethod.indexOf("replaceSections(po, cmd.getSections(), operatorUid);"),
                "topic CAS must complete before replacement sections are touched");
        assertTrue(service.contains("topic.setDraftRevision(null);")
                        && service.contains("snapshot.setDraftRevision(null);"),
                "published/public snapshots must never expose the admin draft revision");
        assertTrue(service.contains("legacyTopicSectionKey(snapshot.getId(), section.getTitle())"),
                "legacy JSON snapshots must receive a stable logical section key");
        assertTrue(service.contains("String sectionKey = topicSectionIdentity(section);"),
                "feedback and events must share the stable section identity resolver");
        assertTrue(service.contains("publishCheckItem(\"publish_status_allowed\""),
                "publish checks must enforce the same lifecycle status rules as publish");
        assertTrue(service.contains("dto.setDraftRevision(draftRevisionOf(po));"),
                "admin mutation responses must return the next draft revision");
        assertTrue(controller.contains("requireExpectedDraftRevision(req)"),
                "every topic lifecycle mutation must require the expected draft revision");

        int slotColumnsStart = migrationCheck.indexOf("putColumns(columns, \"t_operation_slot\"");
        int slotColumnsEnd = migrationCheck.indexOf("putColumns(columns, \"t_operation_slot_item\"", slotColumnsStart);
        int topicColumnsStart = migrationCheck.indexOf("putColumns(columns, \"t_operation_topic\"");
        int topicColumnsEnd = migrationCheck.indexOf("putColumns(columns, \"t_operation_topic_section\"", topicColumnsStart);
        assertFalse(migrationCheck.substring(slotColumnsStart, slotColumnsEnd).contains("\"draft_revision\""),
                "draft_revision must not be required on t_operation_slot");
        assertTrue(migrationCheck.substring(topicColumnsStart, topicColumnsEnd).contains("\"draft_revision\""),
                "draft_revision readiness must be checked on t_operation_topic");

        assertTrue(migration.contains("draft_revision INT NOT NULL DEFAULT 0")
                        && migration.contains("section_key VARCHAR(64) NOT NULL")
                        && migration.contains("SHA2(CONCAT(topic_id, ':', COALESCE(section_title, '')), 256)"),
                "the migration must install and backfill both concurrency and section identity columns");
        assertEquals(migration.replace("\r\n", "\n"), flyway.replace("\r\n", "\n"),
                "the checked-in Flyway resource must match the canonical migration");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isDirectory(current.resolve("db/migration"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }
}

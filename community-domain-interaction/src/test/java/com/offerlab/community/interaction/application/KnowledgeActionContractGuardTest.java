package com.offerlab.community.interaction.application;

import com.offerlab.community.interaction.infrastructure.persistence.mapper.KnowledgeActionMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskMapper;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationMapper;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeActionContractGuardTest {

    @Test
    void knowledgeActionsMustAggregateAllFrozenV10Sources() throws Exception {
        String service = read("src/main/java/com/offerlab/community/interaction/application/KnowledgeActionService.java");
        String mapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/KnowledgeActionMapper.java");
        String controller = read("src/main/java/com/offerlab/community/interaction/controller/KnowledgeActionController.java");
        String page = read("src/main/java/com/offerlab/community/interaction/api/dto/KnowledgeActionPage.java");
        String types = read("src/main/java/com/offerlab/community/interaction/api/enums/KnowledgeActionType.java");

        assertTrue(controller.contains("@GetMapping(\"/knowledge-actions\")"));
        assertTrue(controller.contains("@GetMapping(\"/knowledge-action-summary\")"));
        assertTrue(count(controller, "@RateLimit") == 2);

        assertTrue(page.contains("List<String> sourceErrors"));
        assertTrue(service.contains("KnowledgeMaintenanceReadFacade"));
        assertTrue(service.contains("maintenanceReadFacade.listActions"));
        assertTrue(service.contains("POST_KNOWLEDGE_ACTION_SOURCE_UNAVAILABLE"));
        assertTrue(service.contains("postFacade.getPostForAuthor"));
        assertTrue(service.contains("postFacade.getPost(row.getPostId(), uid)"));

        assertTrue(mapper.contains("o.is_deleted = 0"));
        assertTrue(mapper.contains("r.source_type = 'POST_OUTCOME'"));
        assertTrue(mapper.contains("resolution = 'PENDING'"));
        assertTrue(mapper.contains("base_version < p.version"));
        assertTrue(mapper.contains("freshness_status = 'AWAITING_AUTHOR_CONFIRMATION'"));
        assertTrue(!mapper.contains("COALESCE(decision"));

        for (String type : new String[] {
                "SUGGESTION_RESPONSE", "STALE_SUGGESTION", "FRESHNESS_CONFIRMATION",
                "REFERENCE_REVIEW", "RELATION_REVIEW", "OUTCOME_REVISIT", "MAINTENANCE_TASK"
        }) {
            assertTrue(types.contains(type));
        }
    }

    @Test
    void zeroCandidateRequestMustNotBeBoundAsSqlLimit() throws Exception {
        assertUnbounded(KnowledgeActionMapper.class, "listSuggestionActions", Long.class, int.class);
        assertUnbounded(KnowledgeActionMapper.class, "listStaleSuggestionActions", Long.class, int.class);
        assertUnbounded(KnowledgeActionMapper.class, "listFreshnessActions", int.class);
        assertUnbounded(KnowledgeActionMapper.class, "listOutcomeRevisitActions", Long.class, int.class);
        assertUnbounded(PostReferenceMapper.class, "listBrokenOwned", Long.class, int.class);
        assertUnbounded(PostKnowledgeRelationMapper.class, "listOwnedActions", Long.class, int.class);
        assertUnbounded(PostKnowledgeRelationMapper.class, "listPendingReviewActions", int.class);
        String maintenanceSql = sql(ContentMaintenanceTaskMapper.class,
                "listKnowledgeActions", Long.class, int.class);
        assertFalse(maintenanceSql.contains("limit #{limit}"));
        assertTrue(maintenanceSql.contains("task_status in ('open', 'claimed', 'submitted')"));
    }

    private static void assertUnbounded(Class<?> mapperType, String methodName,
                                        Class<?>... parameterTypes) throws Exception {
        assertFalse(sql(mapperType, methodName, parameterTypes).contains("limit #{limit}"),
                () -> mapperType.getSimpleName() + "." + methodName
                        + " must keep zero as the current all-candidates request");
    }

    private static String sql(Class<?> mapperType, String methodName,
                              Class<?>... parameterTypes) throws Exception {
        Select select = mapperType.getMethod(methodName, parameterTypes).getAnnotation(Select.class);
        assertNotNull(select, () -> mapperType.getSimpleName() + "." + methodName
                + " must remain an annotated select");
        return String.join("\n", select.value()).toLowerCase(Locale.ROOT);
    }

    private static int count(String source, String token) {
        return source.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }
}

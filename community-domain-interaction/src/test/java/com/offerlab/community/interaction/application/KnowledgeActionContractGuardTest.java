package com.offerlab.community.interaction.application;

import com.offerlab.community.interaction.infrastructure.persistence.mapper.KnowledgeActionMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskMapper;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationMapper;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        assertTrue(service.contains("postFacade.batchGetPostsForAuthor"));
        assertTrue(service.contains("postFacade.batchGetPosts"));
        assertFalse(service.contains("postFacade.getPostForAuthor"));
        assertFalse(service.contains("postFacade.getPost(row.getPostId(), uid)"));
        assertFalse(service.contains("MAX_SOURCE_CANDIDATES"));
        assertFalse(service.contains("SOURCE_TRUNCATED"));

        assertTrue(mapper.contains("o.is_deleted = 0"));
        assertTrue(mapper.contains("r.source_type = 'POST_OUTCOME'"));
        assertTrue(mapper.contains("resolution = 'PENDING'"));
        assertTrue(mapper.contains("base_version < p.version"));
        assertTrue(mapper.contains("freshness_status = 'AWAITING_AUTHOR_CONFIRMATION'"));
        assertTrue(mapper.contains("p.author_id = #{uid}"));
        assertTrue(!mapper.contains("COALESCE(decision"));

        for (String type : new String[] {
                "SUGGESTION_RESPONSE", "STALE_SUGGESTION", "FRESHNESS_CONFIRMATION",
                "REFERENCE_REVIEW", "RELATION_REVIEW", "OUTCOME_REVISIT", "MAINTENANCE_TASK"
        }) {
            assertTrue(types.contains(type));
        }
    }

    @Test
    void directKnowledgeActionQueriesMustBeUidScopedAndSqlBounded() throws Exception {
        assertBounded(KnowledgeActionMapper.class, "listSuggestionActions", Long.class, int.class);
        assertBounded(KnowledgeActionMapper.class, "listStaleSuggestionActions", Long.class, int.class);
        assertBounded(KnowledgeActionMapper.class, "listFreshnessActions", Long.class, int.class);
        assertBounded(KnowledgeActionMapper.class, "listOutcomeRevisitActions", Long.class, int.class);
        assertBounded(PostReferenceMapper.class, "listBrokenOwned", Long.class, int.class);
        assertBounded(PostKnowledgeRelationMapper.class, "listOwnedActions", Long.class, int.class);
        assertBounded(PostKnowledgeRelationMapper.class, "listPendingReviewActions", int.class);
        assertBounded(ContentMaintenanceTaskMapper.class, "listKnowledgeActions", Long.class, int.class);
        assertKeysetBounded(KnowledgeActionMapper.class,
                "listSuggestionActionsAfter",
                Long.class, LocalDateTime.class, Long.class, int.class);
        assertKeysetBounded(KnowledgeActionMapper.class,
                "listStaleSuggestionActionsAfter",
                Long.class, LocalDateTime.class, Long.class, int.class);
        assertKeysetBounded(KnowledgeActionMapper.class,
                "listFreshnessActionsAfter",
                Long.class, LocalDateTime.class, Long.class, int.class);
        assertKeysetBounded(KnowledgeActionMapper.class,
                "listOutcomeRevisitActionsAfter",
                Long.class, String.class, LocalDateTime.class, Long.class, int.class);
        assertKeysetBounded(PostReferenceMapper.class,
                "listBrokenOwnedAfter",
                Long.class, LocalDateTime.class, Long.class, int.class);
        assertKeysetBounded(PostKnowledgeRelationMapper.class,
                "listOwnedActionsAfter",
                Long.class, String.class, LocalDateTime.class, Long.class, int.class);
        assertKeysetBounded(ContentMaintenanceTaskMapper.class,
                "listKnowledgeActionsAfter",
                Long.class, String.class, LocalDateTime.class, Long.class, int.class);

        String freshnessSql = sql(KnowledgeActionMapper.class,
                "listFreshnessActions", Long.class, int.class);
        assertTrue(freshnessSql.contains("p.author_id = #{uid}"));
        assertTrue(freshnessSql.contains("p.is_deleted = 0"));

        String reviewSql = sql(PostKnowledgeRelationMapper.class,
                "listPendingReviewActionsForDomainsAfter",
                Long.class, List.class, String.class,
                LocalDateTime.class, Long.class, int.class);
        assertTrue(reviewSql.contains("source_extension.domain in"));
        assertTrue(reviewSql.contains("r.proposer_uid &lt;&gt; #{uid}"));
        assertTrue(reviewSql.indexOf("source_extension.domain in")
                < reviewSql.indexOf("limit #{limit}"));
    }

    @Test
    void dynamicKnowledgeActionQueriesMustBeWellFormedXml() {
        for (Class<?> mapperType : List.of(
                KnowledgeActionMapper.class,
                PostReferenceMapper.class,
                PostKnowledgeRelationMapper.class,
                ContentMaintenanceTaskMapper.class
        )) {
            for (Method method : mapperType.getDeclaredMethods()) {
                Select select = method.getAnnotation(Select.class);
                if (select == null) {
                    continue;
                }
                String script = String.join("\n", select.value()).trim();
                if (!script.startsWith("<script>")) {
                    continue;
                }
                assertDoesNotThrow(() -> parseXml(script),
                        () -> mapperType.getSimpleName() + "." + method.getName()
                                + " must remain valid MyBatis XML");
            }
        }
    }

    private static void assertBounded(Class<?> mapperType, String methodName,
                                      Class<?>... parameterTypes) throws Exception {
        assertTrue(sql(mapperType, methodName, parameterTypes).contains("limit #{limit}"),
                () -> mapperType.getSimpleName() + "." + methodName
                        + " must enforce the caller-provided SQL limit");
    }

    private static void assertKeysetBounded(Class<?> mapperType, String methodName,
                                            Class<?>... parameterTypes) throws Exception {
        String query = sql(mapperType, methodName, parameterTypes);
        assertTrue(query.contains("limit #{limit}"),
                () -> mapperType.getSimpleName() + "." + methodName
                        + " must enforce the caller-provided SQL limit");
        assertTrue(query.contains("#{cursortime} is null"));
        assertTrue(query.contains("< #{cursorid}") || query.contains("&lt; #{cursorid}"));
    }

    private static String sql(Class<?> mapperType, String methodName,
                              Class<?>... parameterTypes) throws Exception {
        Select select = mapperType.getMethod(methodName, parameterTypes).getAnnotation(Select.class);
        assertNotNull(select, () -> mapperType.getSimpleName() + "." + methodName
                + " must remain an annotated select");
        return String.join("\n", select.value()).toLowerCase(Locale.ROOT);
    }

    private static void parseXml(String script) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        factory.newDocumentBuilder().parse(new InputSource(new StringReader(script)));
    }

    private static int count(String source, String token) {
        return source.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }
}

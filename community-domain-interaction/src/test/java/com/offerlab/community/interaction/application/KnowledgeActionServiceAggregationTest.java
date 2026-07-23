package com.offerlab.community.interaction.application;

import com.offerlab.community.interaction.api.dto.KnowledgeActionItemDTO;
import com.offerlab.community.interaction.api.dto.KnowledgeActionPage;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.KnowledgeActionMapper;
import com.offerlab.community.post.api.KnowledgeMaintenanceReadFacade;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.KnowledgeMaintenanceSourceDTO;
import com.offerlab.community.post.api.dto.PostDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeActionServiceAggregationTest {

    private static final long UID = 7L;
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 7, 21, 12, 0);

    @Test
    void listAggregatesEveryFrozenSourceAndRequestsAllCandidates() {
        Fixture fixture = fixture();

        KnowledgeActionPage<KnowledgeActionItemDTO> page =
                fixture.service.list(UID, null, null, "0", 50);

        assertEquals(9L, page.getTotal());
        assertEquals(9, page.getItems().size());
        assertFalse(page.getHasMore());
        assertNull(page.getNextCursor());
        assertTrue(page.getSourceErrors().isEmpty());

        Set<String> types = page.getItems().stream()
                .map(KnowledgeActionItemDTO::getType)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "SUGGESTION_RESPONSE",
                "STALE_SUGGESTION",
                "FRESHNESS_CONFIRMATION",
                "OUTCOME_REVISIT",
                "REFERENCE_REVIEW",
                "RELATION_REVIEW",
                "MAINTENANCE_TASK"
        ), types);

        Map<String, KnowledgeActionItemDTO> byId = page.getItems().stream()
                .collect(Collectors.toMap(KnowledgeActionItemDTO::getId, item -> item));
        assertEquals("OPEN", byId.get("MAINTENANCE_TASK:301").getStatus());
        assertEquals("CLAIMED", byId.get("MAINTENANCE_TASK:302").getStatus());
        assertEquals("SUBMITTED", byId.get("MAINTENANCE_TASK:303").getStatus());
        assertEquals("/me/maintenance", byId.get("MAINTENANCE_TASK:303").getCanonicalRoute());

        assertEquals(0, fixture.mapper.suggestionLimit);
        assertEquals(0, fixture.mapper.staleSuggestionLimit);
        assertEquals(0, fixture.mapper.freshnessLimit);
        assertEquals(0, fixture.mapper.outcomeRevisitLimit);
        assertEquals(0, fixture.maintenanceFacade.limit);
    }

    @Test
    void pageSizeIsAppliedOnlyAfterAllSourcesAreGloballySorted() {
        Fixture fixture = fixture();

        KnowledgeActionPage<KnowledgeActionItemDTO> first =
                fixture.service.list(UID, null, null, null, 3);

        assertEquals(9L, first.getTotal());
        assertEquals(3, first.getItems().size());
        assertTrue(first.getHasMore());
        assertNotNull(first.getNextCursor());
        assertEquals(List.of(
                        "SUGGESTION_RESPONSE:101",
                        "STALE_SUGGESTION:102",
                        "FRESHNESS_CONFIRMATION:103"),
                first.getItems().stream().map(KnowledgeActionItemDTO::getId).toList());

        KnowledgeActionPage<KnowledgeActionItemDTO> second =
                fixture.service.list(UID, null, null, first.getNextCursor(), 50);

        assertEquals(9L, second.getTotal());
        assertEquals(6, second.getItems().size());
        assertFalse(second.getHasMore());
        Set<String> firstIds = first.getItems().stream()
                .map(KnowledgeActionItemDTO::getId)
                .collect(Collectors.toSet());
        assertTrue(second.getItems().stream()
                .map(KnowledgeActionItemDTO::getId)
                .noneMatch(firstIds::contains));

        assertEquals(0, fixture.mapper.suggestionLimit);
        assertEquals(0, fixture.mapper.staleSuggestionLimit);
        assertEquals(0, fixture.mapper.freshnessLimit);
        assertEquals(0, fixture.mapper.outcomeRevisitLimit);
        assertEquals(0, fixture.maintenanceFacade.limit);
    }

    private static Fixture fixture() {
        CapturingKnowledgeActionMapper mapper = new CapturingKnowledgeActionMapper();
        mapper.suggestions = List.of(row(101L, 1001L, "SUGGESTION_RESPONSE", "PENDING", 9));
        mapper.staleSuggestions = List.of(row(102L, 1002L, "STALE_SUGGESTION", "PENDING", 8));
        mapper.freshness = List.of(row(103L, 1003L, "FRESHNESS_CONFIRMATION",
                "AWAITING_AUTHOR_CONFIRMATION", 7));
        mapper.outcomeRevisits = List.of(row(104L, 1004L, "OUTCOME_REVISIT", "OPEN", 6));

        Map<Long, PostDTO> posts = new LinkedHashMap<>();
        posts.put(1001L, post(1001L, "Suggestion post"));
        posts.put(1002L, post(1002L, "Stale suggestion post"));
        posts.put(1003L, post(1003L, "Freshness post"));
        posts.put(1004L, post(1004L, "Outcome post"));

        CapturingMaintenanceFacade maintenanceFacade = new CapturingMaintenanceFacade(List.of(
                source("REFERENCE:201", "REFERENCE_REVIEW", "BROKEN", "/editor/2001#references", 5),
                source("RELATION_REVIEW:202", "RELATION_REVIEW", "PENDING",
                        "/admin/collaboration?tab=knowledge-relations", 4),
                source("MAINTENANCE_TASK:301", "MAINTENANCE_TASK", "OPEN", "/me/maintenance", 3),
                source("MAINTENANCE_TASK:302", "MAINTENANCE_TASK", "CLAIMED", "/me/maintenance", 2),
                source("MAINTENANCE_TASK:303", "MAINTENANCE_TASK", "SUBMITTED", "/me/maintenance", 1)
        ));
        KnowledgeActionService service =
                new KnowledgeActionService(mapper, postFacade(posts), maintenanceFacade);
        return new Fixture(service, mapper, maintenanceFacade);
    }

    private static KnowledgeActionMapper.ActionRow row(Long id, Long postId, String type,
                                                        String status, int minutes) {
        KnowledgeActionMapper.ActionRow row = new KnowledgeActionMapper.ActionRow();
        row.setId(id);
        row.setPostId(postId);
        row.setActionType(type);
        row.setActionStatus(status);
        row.setUpdatedAt(BASE_TIME.plusMinutes(minutes));
        return row;
    }

    private static KnowledgeMaintenanceSourceDTO source(String key, String type, String status,
                                                         String route, int minutes) {
        return KnowledgeMaintenanceSourceDTO.builder()
                .sourceKey(key)
                .actionType(type)
                .title(key)
                .reason(type + "_REQUIRED")
                .status(status)
                .priority("HIGH")
                .canonicalRoute(route)
                .postId(2000L + minutes)
                .updatedAt(BASE_TIME.plusMinutes(minutes))
                .build();
    }

    private static PostDTO post(Long id, String title) {
        return PostDTO.builder()
                .id(id)
                .authorId(UID)
                .title(title)
                .build();
    }

    private static PostFacade postFacade(Map<Long, PostDTO> posts) {
        return (PostFacade) Proxy.newProxyInstance(
                PostFacade.class.getClassLoader(),
                new Class<?>[] {PostFacade.class},
                (proxy, method, args) -> {
                    if ("getPost".equals(method.getName()) || "getPostForAuthor".equals(method.getName())) {
                        return posts.get((Long) args[0]);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            if (List.class.isAssignableFrom(returnType)) {
                return List.of();
            }
            if (Map.class.isAssignableFrom(returnType)) {
                return Map.of();
            }
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == int.class) {
            return 0;
        }
        return null;
    }

    private record Fixture(KnowledgeActionService service,
                           CapturingKnowledgeActionMapper mapper,
                           CapturingMaintenanceFacade maintenanceFacade) {
    }

    private static final class CapturingKnowledgeActionMapper implements KnowledgeActionMapper {
        private List<ActionRow> suggestions = List.of();
        private List<ActionRow> staleSuggestions = List.of();
        private List<ActionRow> freshness = List.of();
        private List<ActionRow> outcomeRevisits = List.of();
        private int suggestionLimit = -1;
        private int staleSuggestionLimit = -1;
        private int freshnessLimit = -1;
        private int outcomeRevisitLimit = -1;

        @Override
        public List<ActionRow> listSuggestionActions(Long uid, int limit) {
            assertEquals(UID, uid);
            suggestionLimit = limit;
            return suggestions;
        }

        @Override
        public List<ActionRow> listStaleSuggestionActions(Long uid, int limit) {
            assertEquals(UID, uid);
            staleSuggestionLimit = limit;
            return staleSuggestions;
        }

        @Override
        public List<ActionRow> listFreshnessActions(int limit) {
            freshnessLimit = limit;
            return freshness;
        }

        @Override
        public List<ActionRow> listOutcomeRevisitActions(Long uid, int limit) {
            assertEquals(UID, uid);
            outcomeRevisitLimit = limit;
            return outcomeRevisits;
        }
    }

    private static final class CapturingMaintenanceFacade implements KnowledgeMaintenanceReadFacade {
        private final List<KnowledgeMaintenanceSourceDTO> items;
        private int limit = -1;

        private CapturingMaintenanceFacade(List<KnowledgeMaintenanceSourceDTO> items) {
            this.items = items;
        }

        @Override
        public List<KnowledgeMaintenanceSourceDTO> listActions(Long uid, int limit) {
            assertEquals(UID, uid);
            this.limit = limit;
            return items;
        }
    }
}

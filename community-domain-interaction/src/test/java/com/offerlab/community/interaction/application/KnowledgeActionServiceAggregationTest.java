package com.offerlab.community.interaction.application;

import com.offerlab.community.interaction.api.dto.KnowledgeActionItemDTO;
import com.offerlab.community.interaction.api.dto.KnowledgeActionPage;
import com.offerlab.community.interaction.api.dto.KnowledgeActionSummaryDTO;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.KnowledgeActionMapper;
import com.offerlab.community.post.api.KnowledgeMaintenanceReadFacade;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.KnowledgeMaintenanceSourceDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
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
    void listAggregatesEveryFrozenSourceWithBoundedQueriesAndBatchPostReads() {
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

        assertEquals(51, fixture.mapper.suggestionLimit);
        assertEquals(51, fixture.mapper.staleSuggestionLimit);
        assertEquals(51, fixture.mapper.freshnessLimit);
        assertEquals(51, fixture.mapper.outcomeRevisitLimit);
        assertEquals(51, fixture.maintenanceFacade.limit);
        assertEquals(4, fixture.batchCalls.get());
        assertEquals(1, fixture.maxBatchSize.get());
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

        assertEquals(51, fixture.mapper.suggestionLimit);
        assertEquals(51, fixture.mapper.staleSuggestionLimit);
        assertEquals(51, fixture.mapper.freshnessLimit);
        assertEquals(51, fixture.mapper.outcomeRevisitLimit);
        assertEquals(51, fixture.maintenanceFacade.limit);
        assertEquals(5, fixture.batchCalls.get());
    }

    @Test
    void summaryUsesExactSourceCountsWithoutLoadingCandidateRows() {
        Fixture fixture = fixture();

        KnowledgeActionSummaryDTO summary = fixture.service.summary(UID);

        assertEquals(9L, summary.getTotal());
        assertEquals(1L, summary.getCounts().get("SUGGESTION_RESPONSE"));
        assertEquals(1L, summary.getCounts().get("STALE_SUGGESTION"));
        assertEquals(1L, summary.getCounts().get("FRESHNESS_CONFIRMATION"));
        assertEquals(1L, summary.getCounts().get("OUTCOME_REVISIT"));
        assertEquals(1L, summary.getCounts().get("REFERENCE_REVIEW"));
        assertEquals(1L, summary.getCounts().get("RELATION_REVIEW"));
        assertEquals(3L, summary.getCounts().get("MAINTENANCE_TASK"));
        assertFalse(summary.getDegraded());
        assertTrue(summary.getSourceErrors().isEmpty());
        assertEquals(0, fixture.batchCalls.get());
        assertEquals(-1, fixture.mapper.suggestionLimit);
        assertEquals(-1, fixture.maintenanceFacade.limit);
    }

    @Test
    void keysetPaginationReachesRowsBeyondThePreviousTwoHundredItemCap() {
        CapturingKnowledgeActionMapper mapper = new CapturingKnowledgeActionMapper();
        mapper.suggestions = IntStream.rangeClosed(1, 251)
                .mapToObj(index -> {
                    KnowledgeActionMapper.ActionRow row = row(
                            10_000L - index,
                            20_000L + index,
                            "SUGGESTION_RESPONSE",
                            "PENDING",
                            0);
                    row.setUpdatedAt(BASE_TIME.minusMinutes(index));
                    return row;
                })
                .toList();
        Map<Long, PostBriefDTO> posts = mapper.suggestions.stream()
                .collect(Collectors.toMap(
                        KnowledgeActionMapper.ActionRow::getPostId,
                        row -> post(row.getPostId(), "post-" + row.getPostId())
                ));
        AtomicInteger batchCalls = new AtomicInteger();
        AtomicInteger maxBatchSize = new AtomicInteger();
        KnowledgeActionService service = new KnowledgeActionService(
                mapper,
                postFacade(posts, batchCalls, maxBatchSize),
                new CapturingMaintenanceFacade(List.of())
        );

        String cursor = null;
        Map<String, KnowledgeActionItemDTO> allItems = new LinkedHashMap<>();
        int pages = 0;
        do {
            KnowledgeActionPage<KnowledgeActionItemDTO> page =
                    service.list(UID, "SUGGESTION_RESPONSE", null, cursor, 50);
            pages++;
            assertEquals(251L, page.getTotal());
            assertTrue(page.getSourceErrors().isEmpty());
            page.getItems().forEach(item -> allItems.put(item.getId(), item));
            cursor = page.getNextCursor();
            if (!page.getHasMore()) {
                break;
            }
        } while (pages < 10);

        assertEquals(6, pages);
        assertEquals(251, allItems.size());
        assertTrue(allItems.containsKey("SUGGESTION_RESPONSE:9749"));
        assertEquals(51, mapper.suggestionLimit);
        assertEquals(6, batchCalls.get());
        assertEquals(51, maxBatchSize.get());
    }

    private static Fixture fixture() {
        CapturingKnowledgeActionMapper mapper = new CapturingKnowledgeActionMapper();
        mapper.suggestions = List.of(row(101L, 1001L, "SUGGESTION_RESPONSE", "PENDING", 9));
        mapper.staleSuggestions = List.of(row(102L, 1002L, "STALE_SUGGESTION", "PENDING", 8));
        mapper.freshness = List.of(row(103L, 1003L, "FRESHNESS_CONFIRMATION",
                "AWAITING_AUTHOR_CONFIRMATION", 7));
        mapper.outcomeRevisits = List.of(row(104L, 1004L, "OUTCOME_REVISIT", "OPEN", 6));

        Map<Long, PostBriefDTO> posts = new LinkedHashMap<>();
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
        AtomicInteger batchCalls = new AtomicInteger();
        AtomicInteger maxBatchSize = new AtomicInteger();
        KnowledgeActionService service = new KnowledgeActionService(
                mapper,
                postFacade(posts, batchCalls, maxBatchSize),
                maintenanceFacade
        );
        return new Fixture(service, mapper, maintenanceFacade, batchCalls, maxBatchSize);
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

    private static PostBriefDTO post(Long id, String title) {
        return PostBriefDTO.builder()
                .id(id)
                .authorId(UID)
                .title(title)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static PostFacade postFacade(Map<Long, PostBriefDTO> posts,
                                         AtomicInteger batchCalls,
                                         AtomicInteger maxBatchSize) {
        return (PostFacade) Proxy.newProxyInstance(
                PostFacade.class.getClassLoader(),
                new Class<?>[] {PostFacade.class},
                (proxy, method, args) -> {
                    if ("batchGetPosts".equals(method.getName())
                            || "batchGetPostsForAuthor".equals(method.getName())) {
                        Collection<Long> ids = (Collection<Long>) args[0];
                        batchCalls.incrementAndGet();
                        maxBatchSize.accumulateAndGet(ids.size(), Math::max);
                        Map<Long, PostBriefDTO> selected = new LinkedHashMap<>();
                        for (Long id : ids) {
                            if (posts.containsKey(id)) {
                                selected.put(id, posts.get(id));
                            }
                        }
                        return selected;
                    }
                    if ("getPost".equals(method.getName()) || "getPostForAuthor".equals(method.getName())) {
                        throw new AssertionError("knowledge actions must not issue per-post reads");
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
                           CapturingMaintenanceFacade maintenanceFacade,
                           AtomicInteger batchCalls,
                           AtomicInteger maxBatchSize) {
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
        public List<ActionRow> listSuggestionActionsAfter(
                Long uid, LocalDateTime cursorTime, Long cursorId, int limit) {
            assertEquals(UID, uid);
            suggestionLimit = limit;
            return after(suggestions, cursorTime, cursorId, limit);
        }

        @Override
        public long countSuggestionActions(Long uid) {
            assertEquals(UID, uid);
            return suggestions.size();
        }

        @Override
        public List<ActionRow> listStaleSuggestionActions(Long uid, int limit) {
            assertEquals(UID, uid);
            staleSuggestionLimit = limit;
            return staleSuggestions;
        }

        @Override
        public List<ActionRow> listStaleSuggestionActionsAfter(
                Long uid, LocalDateTime cursorTime, Long cursorId, int limit) {
            assertEquals(UID, uid);
            staleSuggestionLimit = limit;
            return after(staleSuggestions, cursorTime, cursorId, limit);
        }

        @Override
        public long countStaleSuggestionActions(Long uid) {
            assertEquals(UID, uid);
            return staleSuggestions.size();
        }

        @Override
        public List<ActionRow> listFreshnessActions(Long uid, int limit) {
            assertEquals(UID, uid);
            freshnessLimit = limit;
            return freshness;
        }

        @Override
        public List<ActionRow> listFreshnessActionsAfter(
                Long uid, LocalDateTime cursorTime, Long cursorId, int limit) {
            assertEquals(UID, uid);
            freshnessLimit = limit;
            return after(freshness, cursorTime, cursorId, limit);
        }

        @Override
        public long countFreshnessActions(Long uid) {
            assertEquals(UID, uid);
            return freshness.size();
        }

        @Override
        public List<ActionRow> listOutcomeRevisitActions(Long uid, int limit) {
            assertEquals(UID, uid);
            outcomeRevisitLimit = limit;
            return outcomeRevisits;
        }

        @Override
        public List<ActionRow> listOutcomeRevisitActionsAfter(
                Long uid, String status, LocalDateTime cursorTime, Long cursorId, int limit) {
            assertEquals(UID, uid);
            outcomeRevisitLimit = limit;
            return after(outcomeRevisits, cursorTime, cursorId, limit).stream()
                    .filter(row -> status == null || status.equals(row.getActionStatus()))
                    .toList();
        }

        @Override
        public long countOutcomeRevisitActions(Long uid, String status) {
            assertEquals(UID, uid);
            return outcomeRevisits.stream()
                    .filter(row -> status == null || status.equals(row.getActionStatus()))
                    .count();
        }

        private static List<ActionRow> after(List<ActionRow> rows,
                                             LocalDateTime cursorTime,
                                             Long cursorId,
                                             int limit) {
            return rows.stream()
                    .filter(row -> {
                        if (cursorTime == null) {
                            return true;
                        }
                        int timeCompare = row.getUpdatedAt().compareTo(cursorTime);
                        return timeCompare < 0
                                || (timeCompare == 0 && row.getId() < cursorId);
                    })
                    .limit(limit)
                    .toList();
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

        @Override
        public List<KnowledgeMaintenanceSourceDTO> listActions(
                Long uid, String actionType, String status,
                LocalDateTime cursorTime, Integer cursorSourceOrder,
                Long cursorSourceId, int limit) {
            assertEquals(UID, uid);
            this.limit = limit;
            return items.stream()
                    .filter(item -> actionType == null || actionType.equals(item.getActionType()))
                    .filter(item -> status == null || status.equals(item.getStatus()))
                    .filter(item -> afterCursor(
                            item, cursorTime, cursorSourceOrder, cursorSourceId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countActions(Long uid, String actionType, String status) {
            assertEquals(UID, uid);
            return items.stream()
                    .filter(item -> actionType == null || actionType.equals(item.getActionType()))
                    .filter(item -> status == null || status.equals(item.getStatus()))
                    .count();
        }

        @Override
        public Map<String, Long> countActionsByType(Long uid) {
            assertEquals(UID, uid);
            return items.stream()
                    .collect(Collectors.groupingBy(
                            KnowledgeMaintenanceSourceDTO::getActionType,
                            LinkedHashMap::new,
                            Collectors.counting()));
        }

        private static boolean afterCursor(KnowledgeMaintenanceSourceDTO item,
                                           LocalDateTime cursorTime,
                                           Integer cursorSourceOrder,
                                           Long cursorSourceId) {
            if (cursorTime == null) {
                return true;
            }
            int timeCompare = item.getUpdatedAt().compareTo(cursorTime);
            if (timeCompare != 0) {
                return timeCompare < 0;
            }
            SourceIdentity identity = sourceIdentity(item.getSourceKey());
            if (identity.order != cursorSourceOrder) {
                return identity.order > cursorSourceOrder;
            }
            return identity.id < cursorSourceId;
        }

        private static SourceIdentity sourceIdentity(String sourceKey) {
            int separator = sourceKey.lastIndexOf(':');
            String source = sourceKey.substring(0, separator);
            long id = Long.parseLong(sourceKey.substring(separator + 1));
            int order = switch (source) {
                case "REFERENCE" -> SOURCE_ORDER_REFERENCE;
                case "RELATION_PROPOSAL" -> SOURCE_ORDER_RELATION_PROPOSAL;
                case "RELATION_REVIEW" -> SOURCE_ORDER_RELATION_REVIEW;
                case "MAINTENANCE_TASK" -> SOURCE_ORDER_MAINTENANCE_TASK;
                default -> throw new IllegalArgumentException(source);
            };
            return new SourceIdentity(order, id);
        }

        private record SourceIdentity(int order, long id) {
        }
    }
}

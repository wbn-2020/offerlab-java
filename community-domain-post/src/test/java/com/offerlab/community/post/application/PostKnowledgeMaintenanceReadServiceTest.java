package com.offerlab.community.post.application;

import com.offerlab.community.post.api.KnowledgeMaintenanceReadFacade;
import com.offerlab.community.post.api.dto.KnowledgeMaintenanceSourceDTO;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskRow;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationMapper;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationRow;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceMapper;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceRows.ReferenceRow;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostKnowledgeMaintenanceReadServiceTest {

    private static final long UID = 7L;
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 7, 21, 12, 0);

    @Test
    void boundedQueriesUseOneBatchPostLookupAcrossAllSources() {
        ReferenceRow reference = reference(201L, 1001L, 8);
        PostKnowledgeRelationRow ownedRelation = relation(202L, 1002L, UID, "REJECTED", 7);
        PostKnowledgeRelationRow pendingReview = relation(203L, 1003L, 99L, "PENDING", 6);
        List<ContentMaintenanceTaskRow> tasks = List.of(
                task(301L, "OPEN", 5),
                task(302L, "CLAIMED", 4),
                task(303L, "SUBMITTED", 3)
        );

        AtomicInteger referenceLimit = new AtomicInteger(-1);
        AtomicInteger ownedRelationLimit = new AtomicInteger(-1);
        AtomicInteger pendingReviewLimit = new AtomicInteger(-1);
        AtomicInteger taskLimit = new AtomicInteger(-1);
        AtomicInteger postBatchCalls = new AtomicInteger();
        List<Integer> capturedDomains = new ArrayList<>();

        PostReferenceMapper referenceMapper = proxy(PostReferenceMapper.class, (method, args) -> {
            if ("listBrokenOwnedAfter".equals(method)) {
                assertEquals(UID, args[0]);
                referenceLimit.set((Integer) args[3]);
                return List.of(reference);
            }
            return null;
        });
        PostKnowledgeRelationMapper relationMapper = proxy(PostKnowledgeRelationMapper.class, (method, args) -> {
            if ("listOwnedActionsAfter".equals(method)) {
                assertEquals(UID, args[0]);
                ownedRelationLimit.set((Integer) args[4]);
                return List.of(ownedRelation);
            }
            if ("listPendingReviewActionsForDomainsAfter".equals(method)) {
                assertEquals(UID, args[0]);
                capturedDomains.addAll(castList(args[1]));
                pendingReviewLimit.set((Integer) args[5]);
                return List.of(pendingReview);
            }
            return null;
        });
        ContentMaintenanceTaskMapper taskMapper = proxy(ContentMaintenanceTaskMapper.class, (method, args) -> {
            if ("listKnowledgeActionsAfter".equals(method)) {
                assertEquals(UID, args[0]);
                taskLimit.set((Integer) args[4]);
                return tasks;
            }
            return null;
        });

        Map<Long, Post> posts = new LinkedHashMap<>();
        posts.put(1001L, post(1001L, UID, "Reference post", Post.DOMAIN_TECH));
        posts.put(1002L, post(1002L, UID, "Owned relation post", Post.DOMAIN_TECH));
        posts.put(1003L, post(1003L, 99L, "Moderation relation post", Post.DOMAIN_CAREER));

        PostKnowledgeMaintenanceReadService service = new PostKnowledgeMaintenanceReadService(
                referenceMapper,
                relationMapper,
                taskMapper,
                postRepository(posts, postBatchCalls),
                allowingModeratorService()
        );

        List<KnowledgeMaintenanceSourceDTO> items = service.listActions(UID, 25);

        assertEquals(6, items.size());
        assertEquals(List.of(
                        "REFERENCE:201",
                        "RELATION_PROPOSAL:202",
                        "RELATION_REVIEW:203",
                        "MAINTENANCE_TASK:301",
                        "MAINTENANCE_TASK:302",
                        "MAINTENANCE_TASK:303"),
                items.stream().map(KnowledgeMaintenanceSourceDTO::getSourceKey).toList());

        Map<String, KnowledgeMaintenanceSourceDTO> byKey = items.stream()
                .collect(Collectors.toMap(KnowledgeMaintenanceSourceDTO::getSourceKey, item -> item));
        assertEquals("REFERENCE_REVIEW", byKey.get("REFERENCE:201").getActionType());
        assertEquals("RELATION_REVIEW", byKey.get("RELATION_PROPOSAL:202").getActionType());
        assertEquals("RELATION_REVIEW", byKey.get("RELATION_REVIEW:203").getActionType());
        assertEquals("OPEN", byKey.get("MAINTENANCE_TASK:301").getStatus());
        assertEquals("CLAIMED", byKey.get("MAINTENANCE_TASK:302").getStatus());
        assertEquals("SUBMITTED", byKey.get("MAINTENANCE_TASK:303").getStatus());
        assertTrue(items.stream()
                .filter(item -> item.getSourceKey().startsWith("MAINTENANCE_TASK:"))
                .allMatch(item -> "/me/maintenance".equals(item.getCanonicalRoute())));

        assertEquals(25, referenceLimit.get());
        assertEquals(25, ownedRelationLimit.get());
        assertEquals(25, pendingReviewLimit.get());
        assertEquals(25, taskLimit.get());
        assertEquals(1, postBatchCalls.get());
        assertEquals(List.of(Post.DOMAIN_TECH, Post.DOMAIN_CAREER), capturedDomains);
    }

    @Test
    void maintenanceTaskKeysetCanReachRowsBeyondTwoHundred() {
        List<ContentMaintenanceTaskRow> tasks = IntStream.rangeClosed(1, 251)
                .mapToObj(index -> task(
                        10_000L - index,
                        "OPEN",
                        -index))
                .toList();
        ContentMaintenanceTaskMapper taskMapper = proxy(ContentMaintenanceTaskMapper.class, (method, args) -> {
            if (!"listKnowledgeActionsAfter".equals(method)) {
                return null;
            }
            LocalDateTime cursorTime = (LocalDateTime) args[2];
            Long cursorId = (Long) args[3];
            int limit = (Integer) args[4];
            return tasks.stream()
                    .filter(row -> after(row.getUpdateTime(), row.getId(), cursorTime, cursorId))
                    .limit(limit)
                    .toList();
        });
        PostKnowledgeMaintenanceReadService service = new PostKnowledgeMaintenanceReadService(
                proxy(PostReferenceMapper.class, (method, args) -> List.of()),
                proxy(PostKnowledgeRelationMapper.class, (method, args) -> List.of()),
                taskMapper,
                postRepository(Map.of(), new AtomicInteger()),
                moderatorService(List.of())
        );

        LocalDateTime cursorTime = null;
        Integer cursorOrder = null;
        Long cursorId = null;
        Map<String, KnowledgeMaintenanceSourceDTO> allItems = new LinkedHashMap<>();
        int pages = 0;
        while (pages < 10) {
            List<KnowledgeMaintenanceSourceDTO> candidates = service.listActions(
                    UID,
                    "MAINTENANCE_TASK",
                    null,
                    cursorTime,
                    cursorOrder,
                    cursorId,
                    51);
            pages++;
            boolean hasMore = candidates.size() > 50;
            List<KnowledgeMaintenanceSourceDTO> page = hasMore
                    ? candidates.subList(0, 50)
                    : candidates;
            page.forEach(item -> allItems.put(item.getSourceKey(), item));
            if (!hasMore) {
                break;
            }
            KnowledgeMaintenanceSourceDTO last = page.get(page.size() - 1);
            cursorTime = last.getUpdatedAt();
            cursorOrder = KnowledgeMaintenanceReadFacade.SOURCE_ORDER_MAINTENANCE_TASK;
            cursorId = Long.parseLong(last.getSourceKey().substring(
                    last.getSourceKey().lastIndexOf(':') + 1));
        }

        assertEquals(6, pages);
        assertEquals(251, allItems.size());
        assertTrue(allItems.containsKey("MAINTENANCE_TASK:9749"));
    }

    @Test
    void relationReviewPermissionPredicateIsAppliedBeforeLimit() throws Exception {
        Select select = PostKnowledgeRelationMapper.class.getMethod(
                        "listPendingReviewActionsForDomainsAfter",
                        Long.class,
                        List.class,
                        String.class,
                        LocalDateTime.class,
                        Long.class,
                        int.class)
                .getAnnotation(Select.class);
        String sql = String.join("\n", select.value()).toLowerCase();

        assertTrue(sql.contains("source_extension.domain in"));
        assertTrue(sql.contains("r.proposer_uid &lt;&gt; #{uid}"));
        assertTrue(sql.indexOf("source_extension.domain in") < sql.indexOf("limit #{limit}"));
        assertFalse(sql.contains("order by case risk_level"));

        Configuration configuration = new Configuration();
        SqlSource sqlSource = configuration.getLanguageDriver(null).createSqlSource(
                configuration, String.join("\n", select.value()), Map.class);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("uid", UID);
        parameters.put("domains", List.of(Post.DOMAIN_TECH, Post.DOMAIN_CAREER));
        parameters.put("status", null);
        parameters.put("cursorTime", null);
        parameters.put("cursorId", null);
        parameters.put("limit", 26);
        BoundSql boundSql = sqlSource.getBoundSql(parameters);
        String rendered = boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();

        assertTrue(rendered.contains("source_extension.domain in ( ? , ? )"));
        assertTrue(rendered.contains("r.proposer_uid <> ?"));
        assertTrue(rendered.endsWith("limit ?"));
    }

    private static ReferenceRow reference(Long id, Long postId, int minutes) {
        ReferenceRow row = new ReferenceRow();
        row.setId(id);
        row.setPostId(postId);
        row.setOwnerUid(UID);
        row.setTitle("Broken source");
        row.setReferenceStatus("BROKEN");
        row.setUpdateTime(BASE_TIME.plusMinutes(minutes));
        return row;
    }

    private static PostKnowledgeRelationRow relation(Long id, Long sourcePostId, Long proposerUid,
                                                       String status, int minutes) {
        PostKnowledgeRelationRow row = new PostKnowledgeRelationRow();
        row.setId(id);
        row.setSourcePostId(sourcePostId);
        row.setTargetPostId(sourcePostId + 100);
        row.setRelationType("SUPERSEDES");
        row.setProposerUid(proposerUid);
        row.setReviewStatus(status);
        row.setRiskLevel("HIGH");
        row.setUpdateTime(BASE_TIME.plusMinutes(minutes));
        return row;
    }

    private static ContentMaintenanceTaskRow task(Long id, String status, int minutes) {
        ContentMaintenanceTaskRow row = new ContentMaintenanceTaskRow();
        row.setId(id);
        row.setAssigneeUid(UID);
        row.setSourcePostId(2000L + id);
        row.setTitle(status + " maintenance task");
        row.setStatus(status);
        row.setUpdateTime(BASE_TIME.plusMinutes(minutes));
        return row;
    }

    private static Post post(Long id, Long authorId, String title, Integer domain) {
        return Post.builder()
                .id(id)
                .authorId(authorId)
                .title(title)
                .domain(domain)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static PostRepository postRepository(Map<Long, Post> posts, AtomicInteger batchCalls) {
        return proxy(PostRepository.class, (method, args) -> {
            if ("batchFindByIds".equals(method)) {
                batchCalls.incrementAndGet();
                Map<Long, Post> selected = new LinkedHashMap<>();
                for (Long id : (Collection<Long>) args[0]) {
                    if (posts.containsKey(id)) {
                        selected.put(id, posts.get(id));
                    }
                }
                return selected;
            }
            if ("findById".equals(method)) {
                throw new AssertionError("knowledge maintenance reads must not issue per-post queries");
            }
            return null;
        });
    }

    private static DomainModeratorService allowingModeratorService() {
        return moderatorService(List.of(Post.DOMAIN_TECH, Post.DOMAIN_CAREER));
    }

    private static DomainModeratorService moderatorService(List<Integer> domains) {
        return new DomainModeratorService(null, null, null, null, null) {
            @Override
            public List<Integer> listModeratableDomains(Long uid) {
                return UID == uid ? domains : List.of();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> castList(Object value) {
        return (List<Integer>) value;
    }

    private static boolean after(LocalDateTime updatedAt, Long id,
                                 LocalDateTime cursorTime, Long cursorId) {
        if (cursorTime == null) {
            return true;
        }
        int timeCompare = updatedAt.compareTo(cursorTime);
        return timeCompare < 0 || (timeCompare == 0 && id < cursorId);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> type.getSimpleName() + "TestProxy";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    Object value = invocation.invoke(method.getName(), args == null ? new Object[0] : args);
                    if (value != null || !method.getReturnType().isPrimitive()) {
                        return value;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    return 0;
                });
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args);
    }
}

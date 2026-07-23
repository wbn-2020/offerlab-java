package com.offerlab.community.post.application;

import com.offerlab.community.post.api.dto.KnowledgeMaintenanceSourceDTO;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskRow;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationMapper;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationRow;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceMapper;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceRows.ReferenceRow;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostKnowledgeMaintenanceReadServiceTest {

    private static final long UID = 7L;
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 7, 21, 12, 0);

    @Test
    void zeroRequestsAllReferenceRelationAndActiveMaintenanceTaskCandidates() {
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

        PostReferenceMapper referenceMapper = proxy(PostReferenceMapper.class, (method, args) -> {
            if ("listBrokenOwned".equals(method)) {
                assertEquals(UID, args[0]);
                referenceLimit.set((Integer) args[1]);
                return List.of(reference);
            }
            return null;
        });
        PostKnowledgeRelationMapper relationMapper = proxy(PostKnowledgeRelationMapper.class, (method, args) -> {
            if ("listOwnedActions".equals(method)) {
                assertEquals(UID, args[0]);
                ownedRelationLimit.set((Integer) args[1]);
                return List.of(ownedRelation);
            }
            if ("listPendingReviewActions".equals(method)) {
                pendingReviewLimit.set((Integer) args[0]);
                return List.of(pendingReview);
            }
            return null;
        });
        ContentMaintenanceTaskMapper taskMapper = proxy(ContentMaintenanceTaskMapper.class, (method, args) -> {
            if ("listKnowledgeActions".equals(method)) {
                assertEquals(UID, args[0]);
                taskLimit.set((Integer) args[1]);
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
                postRepository(posts),
                allowingModeratorService()
        );

        List<KnowledgeMaintenanceSourceDTO> items = service.listActions(UID, 0);

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

        assertEquals(0, referenceLimit.get());
        assertEquals(0, ownedRelationLimit.get());
        assertEquals(0, pendingReviewLimit.get());
        assertEquals(0, taskLimit.get());
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

    private static PostRepository postRepository(Map<Long, Post> posts) {
        return proxy(PostRepository.class, (method, args) -> {
            if ("findById".equals(method)) {
                return Optional.ofNullable(posts.get((Long) args[0]));
            }
            return null;
        });
    }

    private static DomainModeratorService allowingModeratorService() {
        return new DomainModeratorService(null, null, null, null, null) {
            @Override
            public boolean canModerateDomain(Long uid, Integer domain) {
                return UID == uid && domain != null;
            }
        };
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

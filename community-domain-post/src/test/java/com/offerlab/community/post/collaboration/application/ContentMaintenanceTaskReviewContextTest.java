package com.offerlab.community.post.collaboration.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.CommunityRoleAccessService;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryFacade;
import com.offerlab.community.post.api.dto.PublicPostUpdateDTO;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskReviewContextDTO;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskRow;
import com.offerlab.community.post.domain.model.Post;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentMaintenanceTaskReviewContextTest {

    @Test
    void assigneeReadsSamePostEvidenceAfterTaskCreationWithThreeItemLimit() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 1, 10, 0);
        State state = new State(submittedTask("POST", 901L, 901L, createdAt));
        state.posts = List.of(publicPost(901L, "Updated guidance"));
        state.updates = List.of(
                update(6, createdAt.plusHours(4)),
                update(5, createdAt.plusHours(3)),
                update(4, createdAt),
                update(3, createdAt.plusHours(1)),
                update(2, createdAt.minusSeconds(1)));

        ContentMaintenanceTaskReviewContextDTO result = service(state, Set.of()).reviewContext(101L, 22L);

        assertEquals("SAME_POST_UPDATED", result.getEvidence().getState());
        assertEquals(3, result.getEvidence().getUpdates().size());
        assertEquals(6, result.getEvidence().getUpdates().get(0).getResultVersion());
        assertEquals("/post/901", result.getSource().getPostHref());
        assertEquals("AVAILABLE", result.getDelivery().getAvailability());
        assertFalse(result.getDegraded());
        assertEquals(1, state.getPostCalls);
        assertEquals(1, state.listPublicUpdatesCalls);
    }

    @Test
    void creatorAndDomainModeratorCanReadButUnrelatedUserIsForbidden() {
        State creatorState = new State(submittedTask("POST", 901L, 902L, LocalDateTime.now()));
        creatorState.posts = List.of(publicPost(901L, "Source"), publicPost(902L, "Delivery"));
        assertEquals("SEPARATE_PUBLIC_DELIVERY",
                service(creatorState, Set.of()).reviewContext(101L, 77L).getEvidence().getState());

        State moderatorState = new State(submittedTask("POST", 901L, 902L, LocalDateTime.now()));
        moderatorState.posts = List.of(publicPost(901L, "Source"), publicPost(902L, "Delivery"));
        assertEquals("SEPARATE_PUBLIC_DELIVERY",
                service(moderatorState, Set.of(1)).reviewContext(101L, 88L).getEvidence().getState());

        State forbiddenState = new State(submittedTask("POST", 901L, 902L, LocalDateTime.now()));
        BizException error = assertThrows(BizException.class,
                () -> service(forbiddenState, Set.of()).reviewContext(101L, 99L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), error.getCode());
        assertEquals(0, forbiddenState.getPostCalls);
    }

    @Test
    void submittedSeriesRetainsSourceContextWithoutReadingRevisionEvidence() {
        State state = new State(submittedTask("SERIES", 901L, null, LocalDateTime.now()));
        state.posts = List.of(publicPost(901L, "Source"));

        ContentMaintenanceTaskReviewContextDTO result = service(state, Set.of()).reviewContext(101L, 22L);

        assertEquals("NON_POST_DELIVERY", result.getEvidence().getState());
        assertEquals("/post/901", result.getSource().getPostHref());
        assertEquals("NOT_LINKED", result.getDelivery().getAvailability());
        assertTrue(result.getEvidence().getUpdates().isEmpty());
        assertFalse(result.getDegraded());
        assertEquals(1, state.getPostCalls);
        assertEquals(0, state.listPublicUpdatesCalls);
    }

    @Test
    void notSubmittedEvidenceHasPriorityWhenSourceIsUnavailable() {
        ContentMaintenanceTaskRow task = submittedTask("POST", 901L, 901L, LocalDateTime.now());
        task.setStatus("CLAIMED");
        State state = new State(task);

        ContentMaintenanceTaskReviewContextDTO result = service(state, Set.of()).reviewContext(101L, 22L);

        assertEquals("NOT_SUBMITTED", result.getEvidence().getState());
        assertEquals("UNAVAILABLE", result.getSource().getAvailability());
        assertFalse(result.getDegraded());
        assertEquals(1, state.getPostCalls);
        assertEquals(0, state.listPublicUpdatesCalls);
    }

    @Test
    void nonPostDeliveryEvidenceHasPriorityWhenSourceIsUnavailable() {
        State state = new State(submittedTask("SERIES", 901L, null, LocalDateTime.now()));

        ContentMaintenanceTaskReviewContextDTO result = service(state, Set.of()).reviewContext(101L, 22L);

        assertEquals("NON_POST_DELIVERY", result.getEvidence().getState());
        assertEquals("UNAVAILABLE", result.getSource().getAvailability());
        assertEquals("NOT_LINKED", result.getDelivery().getAvailability());
        assertFalse(result.getDegraded());
        assertEquals(1, state.getPostCalls);
        assertEquals(0, state.listPublicUpdatesCalls);
    }

    @Test
    void unavailableDeliveryAndUpdateFailureFailClosedWithoutExceptionDetails() {
        State deliveryState = new State(submittedTask("POST", 901L, 902L, LocalDateTime.now()));
        deliveryState.posts = Arrays.asList(publicPost(901L, "Source"), null);

        ContentMaintenanceTaskReviewContextDTO delivery =
                service(deliveryState, Set.of()).reviewContext(101L, 22L);

        assertEquals("DELIVERY_UNAVAILABLE", delivery.getEvidence().getState());
        assertEquals("UNAVAILABLE", delivery.getDelivery().getAvailability());
        assertEquals("DELIVERY_UNAVAILABLE", delivery.getFallbackReason());
        assertTrue(delivery.getDegraded());

        State evidenceState = new State(submittedTask("POST", 901L, 901L, LocalDateTime.now()));
        evidenceState.posts = List.of(publicPost(901L, "Source"));
        evidenceState.updateFailure = new IllegalStateException("database host and credentials");

        ContentMaintenanceTaskReviewContextDTO evidence =
                service(evidenceState, Set.of()).reviewContext(101L, 22L);

        assertEquals("EVIDENCE_UNAVAILABLE", evidence.getEvidence().getState());
        assertEquals("EVIDENCE_UNAVAILABLE", evidence.getFallbackReason());
        assertFalse(evidence.toString().contains("database host"));
        assertTrue(evidence.getEvidence().getUpdates().isEmpty());
    }

    private static ContentMaintenanceTaskService service(State state, Set<Integer> moderatedDomains) {
        return new ContentMaintenanceTaskService(
                taskMapper(state),
                new SnowflakeIdGenerator(),
                new ModeratorStub(moderatedDomains),
                new RoleAccessStub(),
                new AdminAuditService(null, null, null),
                postFacade(state),
                collaborationMapper(),
                revisionQuery()
        );
    }

    private static PostContentRevisionQueryFacade revisionQuery() {
        return (PostContentRevisionQueryFacade) Proxy.newProxyInstance(
                PostContentRevisionQueryFacade.class.getClassLoader(),
                new Class<?>[]{PostContentRevisionQueryFacade.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private static ContentMaintenanceTaskMapper taskMapper(State state) {
        return (ContentMaintenanceTaskMapper) Proxy.newProxyInstance(
                ContentMaintenanceTaskMapper.class.getClassLoader(),
                new Class<?>[]{ContentMaintenanceTaskMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> 1;
                    case "attemptTableExists" -> 1;
                    case "selectById" -> state.task;
                    case "toString" -> "ContentMaintenanceTaskMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static PostFacade postFacade(State state) {
        return (PostFacade) Proxy.newProxyInstance(
                PostFacade.class.getClassLoader(),
                new Class<?>[]{PostFacade.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPost" -> {
                        state.getPostCalls++;
                        PostDTO post = state.posts.get(state.postIndex++);
                        yield post;
                    }
                    case "listPublicUpdates" -> {
                        state.listPublicUpdatesCalls++;
                        if (state.updateFailure != null) {
                            throw state.updateFailure;
                        }
                        yield state.updates;
                    }
                    case "toString" -> "PostFacadeStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static CollaborationMapper collaborationMapper() {
        return (CollaborationMapper) Proxy.newProxyInstance(
                CollaborationMapper.class.getClassLoader(),
                new Class<?>[]{CollaborationMapper.class},
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return "CollaborationMapperStub";
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private static ContentMaintenanceTaskRow submittedTask(
            String deliveryType,
            Long sourcePostId,
            Long deliveryPostId,
            LocalDateTime createTime) {
        ContentMaintenanceTaskRow row = new ContentMaintenanceTaskRow();
        row.setId(101L);
        row.setDomain(1);
        row.setSourceType("CHANNEL_HEALTH");
        row.setSourcePostId(sourcePostId);
        row.setCreatedByUid(77L);
        row.setAssigneeUid(22L);
        row.setTitle("Refresh channel guidance");
        row.setDetail("Update stale public guidance.");
        row.setStatus("SUBMITTED");
        row.setDeliveryType(deliveryType);
        row.setDeliveryRefId(501L);
        row.setDeliveryPostId(deliveryPostId);
        row.setCreateTime(createTime);
        return row;
    }

    private static PostDTO publicPost(Long id, String title) {
        return PostDTO.builder()
                .id(id)
                .domain(1)
                .postType(Post.TYPE_TECH_ARTICLE)
                .title(title)
                .postStatus(Post.STATUS_PUBLISHED)
                .visibility(Post.VIS_PUBLIC)
                .build();
    }

    private static PublicPostUpdateDTO update(int version, LocalDateTime createTime) {
        return PublicPostUpdateDTO.builder()
                .resultVersion(version)
                .publicUpdateSummary("Public update " + version)
                .impactScope("CONTENT")
                .createTime(createTime)
                .build();
    }

    private static final class State {
        private final ContentMaintenanceTaskRow task;
        private List<PostDTO> posts = List.of();
        private List<PublicPostUpdateDTO> updates = List.of();
        private RuntimeException updateFailure;
        private int postIndex;
        private int getPostCalls;
        private int listPublicUpdatesCalls;

        private State(ContentMaintenanceTaskRow task) {
            this.task = task;
        }
    }

    private static final class ModeratorStub extends DomainModeratorService {
        private final Set<Integer> moderatedDomains;

        private ModeratorStub(Set<Integer> moderatedDomains) {
            super(null, null, null, null, null);
            this.moderatedDomains = moderatedDomains;
        }

        @Override
        public boolean canModerateDomain(Long uid, Integer domain) {
            return moderatedDomains.contains(domain);
        }
    }

    private static final class RoleAccessStub extends CommunityRoleAccessService {

        private RoleAccessStub() {
            super(null);
        }

        @Override
        public boolean hasActiveGrant(Long uid, String roleCode, String domainCode) {
            return false;
        }
    }
}

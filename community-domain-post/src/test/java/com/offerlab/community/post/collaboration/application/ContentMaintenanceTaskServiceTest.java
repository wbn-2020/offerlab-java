package com.offerlab.community.post.collaboration.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskReviewCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskSubmitCmd;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationRows.SeriesRow;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskRow;
import com.offerlab.community.post.domain.model.Post;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentMaintenanceTaskServiceTest {

    @Test
    void assigneeCanClaimAndSubmitOwnedPublicPost() {
        MapperState state = new MapperState();
        state.locked.add(row("OPEN", 22L, 1));
        state.locked.add(row("CLAIMED", 22L, 1));
        state.locked.add(row("CLAIMED", 22L, 1));
        state.locked.add(row("SUBMITTED", 22L, 1));
        state.claimResult = 1;
        state.submitResult = 1;
        ContentMaintenanceTaskService service = service(state, Set.of(), publicPost(901L, 22L, 1));

        ContentMaintenanceTaskDTO claimed = service.claim(101L, 22L);
        ContentMaintenanceTaskDTO submitted = service.submit(101L, submit("POST", 901L), 22L);

        assertEquals("CLAIMED", claimed.getStatus());
        assertTrue(claimed.getCanSubmit());
        assertEquals("SUBMITTED", submitted.getStatus());
        assertFalse(submitted.getCanSubmit());
        assertEquals(1, state.claimCalls);
        assertEquals(1, state.submitCalls);
    }

    @Test
    void nonAssigneeCannotClaimTask() {
        MapperState state = new MapperState();
        state.locked.add(row("OPEN", 22L, 1));
        ContentMaintenanceTaskService service = service(state, Set.of(), null);

        BizException error = assertThrows(BizException.class, () -> service.claim(101L, 23L));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), error.getCode());
        assertEquals(0, state.claimCalls);
    }

    @Test
    void submissionRejectsDeliveryFromAnotherDomain() {
        MapperState state = new MapperState();
        state.locked.add(row("CLAIMED", 22L, 1));
        ContentMaintenanceTaskService service = service(state, Set.of(), publicPost(901L, 22L, 2));

        BizException error = assertThrows(BizException.class,
                () -> service.submit(101L, submit("POST", 901L), 22L));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), error.getCode());
        assertEquals(0, state.submitCalls);
    }

    @Test
    void assigneeCanSubmitOwnedPublicSeriesInTaskDomain() {
        MapperState state = new MapperState();
        state.locked.add(row("CLAIMED", 22L, 1));
        state.locked.add(row("SUBMITTED", 22L, 1));
        state.submitResult = 1;
        state.series = series(501L, 22L, 1, 2);
        state.seriesContributor = 1;
        ContentMaintenanceTaskService service = service(state, Set.of(), null);
        ContentMaintenanceTaskSubmitCmd cmd = submit("SERIES", 501L);
        cmd.setDeliveryPostId(null);

        ContentMaintenanceTaskDTO submitted = service.submit(101L, cmd, 22L);

        assertEquals("SUBMITTED", submitted.getStatus());
        assertEquals(1, state.submitCalls);
    }

    @Test
    void moderatorCanApproveSubmittedTask() {
        MapperState state = new MapperState();
        state.locked.add(row("SUBMITTED", 22L, 3));
        state.locked.add(row("COMPLETED", 22L, 3));
        state.approveResult = 1;
        ContentMaintenanceTaskService service = service(state, Set.of(3), null);

        ContentMaintenanceTaskDTO result = service.review(101L, review("APPROVED"), 77L);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(1, state.approveCalls);
        assertEquals(1, state.auditCalls);
    }

    @Test
    void domainModeratorQueueIsScopedWithoutExplicitDomainFilter() {
        MapperState state = new MapperState();
        state.queueRows.add(row("OPEN", 22L, 3));
        ContentMaintenanceTaskService service = service(state, Set.of(3), null);

        List<ContentMaintenanceTaskDTO> items = service.listQueue(null, null, 77L, 0, 20).getItems();

        assertEquals(1, items.size());
        assertEquals(3, state.requestedDomains.get(0));
        assertFalse(state.requestedDomains.contains(1));
    }

    private static ContentMaintenanceTaskService service(MapperState state, Set<Integer> moderatedDomains,
                                                         PostDTO post) {
        return new ContentMaintenanceTaskService(
                taskMapper(state),
                new SnowflakeIdGenerator(),
                new ModeratorStub(moderatedDomains),
                new AuditStub(state),
                postFacade(post),
                collaborationMapper(state)
        );
    }

    private static ContentMaintenanceTaskSubmitCmd submit(String type, Long id) {
        ContentMaintenanceTaskSubmitCmd cmd = new ContentMaintenanceTaskSubmitCmd();
        cmd.setDeliveryType(type);
        cmd.setDeliveryRefId(id);
        cmd.setDeliveryPostId(id);
        cmd.setNote("updated public guidance");
        return cmd;
    }

    private static ContentMaintenanceTaskReviewCmd review(String decision) {
        ContentMaintenanceTaskReviewCmd cmd = new ContentMaintenanceTaskReviewCmd();
        cmd.setDecision(decision);
        cmd.setNote("reviewed against acceptance criteria");
        return cmd;
    }

    private static ContentMaintenanceTaskRow row(String status, Long assigneeUid, int domain) {
        ContentMaintenanceTaskRow row = new ContentMaintenanceTaskRow();
        row.setId(101L);
        row.setDomain(domain);
        row.setSourceType("CHANNEL_HEALTH");
        row.setCreatedByUid(77L);
        row.setAssigneeUid(assigneeUid);
        row.setTitle("Refresh channel guidance");
        row.setDetail("Update the public guidance and resolve stale information.");
        row.setStatus(status);
        return row;
    }

    private static PostDTO publicPost(Long id, Long authorUid, int domain) {
        return PostDTO.builder()
                .id(id)
                .authorId(authorUid)
                .domain(domain)
                .postStatus(Post.STATUS_PUBLISHED)
                .visibility(Post.VIS_PUBLIC)
                .postType(Post.TYPE_TECH_ARTICLE)
                .build();
    }

    private static SeriesRow series(Long id, Long ownerUid, int domain, int postCount) {
        SeriesRow row = new SeriesRow();
        row.setId(id);
        row.setOwnerUid(ownerUid);
        row.setDomain(domain);
        row.setPostCount(postCount);
        row.setStatus("OPEN");
        return row;
    }

    private static ContentMaintenanceTaskMapper taskMapper(MapperState state) {
        return (ContentMaintenanceTaskMapper) Proxy.newProxyInstance(
                ContentMaintenanceTaskMapper.class.getClassLoader(),
                new Class<?>[]{ContentMaintenanceTaskMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> 1;
                    case "lockById" -> state.locked.removeFirst();
                    case "claim" -> {
                        state.claimCalls++;
                        yield state.claimResult;
                    }
                    case "submit" -> {
                        state.submitCalls++;
                        yield state.submitResult;
                    }
                    case "approve" -> {
                        state.approveCalls++;
                        yield state.approveResult;
                    }
                    case "listQueue" -> {
                        state.requestedDomains.add((Integer) args[0]);
                        yield state.queueRows;
                    }
                    case "toString" -> "ContentMaintenanceTaskMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static PostFacade postFacade(PostDTO post) {
        return (PostFacade) Proxy.newProxyInstance(
                PostFacade.class.getClassLoader(),
                new Class<?>[]{PostFacade.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPostForAuthor", "getPost" -> post;
                    case "toString" -> "PostFacadeStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static CollaborationMapper collaborationMapper(MapperState state) {
        return (CollaborationMapper) Proxy.newProxyInstance(
                CollaborationMapper.class.getClassLoader(),
                new Class<?>[]{CollaborationMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectSeries" -> state.series;
                    case "seriesHasContributor" -> state.seriesContributor;
                    case "toString" -> "CollaborationMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static final class MapperState {
        private final Deque<ContentMaintenanceTaskRow> locked = new ArrayDeque<>();
        private final List<ContentMaintenanceTaskRow> queueRows = new ArrayList<>();
        private final List<Integer> requestedDomains = new ArrayList<>();
        private int claimResult;
        private int submitResult;
        private int approveResult;
        private int claimCalls;
        private int submitCalls;
        private int approveCalls;
        private int auditCalls;
        private SeriesRow series;
        private int seriesContributor;
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

    private static final class AuditStub extends AdminAuditService {
        private final MapperState state;

        private AuditStub(MapperState state) {
            super(null, null, null);
            this.state = state;
        }

        @Override
        public void recordRequired(Long operatorUid, String action, String resourceType, Object resourceId,
                                   Object before, Object after, String remark) {
            state.auditCalls++;
        }
    }
}

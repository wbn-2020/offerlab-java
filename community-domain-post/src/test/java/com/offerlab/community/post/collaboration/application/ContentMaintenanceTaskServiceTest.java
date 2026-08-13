package com.offerlab.community.post.collaboration.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.CommunityRoleAccessService;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.quality.PostContentRevisionQuery;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryFacade;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryResult;
import com.offerlab.community.post.api.quality.PostContentRevisionSnapshot;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceCandidateDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskCreateCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskReassignCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskReviewCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskSubmitCmd;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationRows.SeriesRow;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskAttemptRow;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskRow;
import com.offerlab.community.post.domain.model.Post;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
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
    void moderatorCanExplicitlyReassignOpenAndClaimedTasksWithoutChangingState() {
        for (String status : List.of("OPEN", "CLAIMED")) {
            MapperState state = new MapperState();
            state.locked.add(row(status, 22L, 1));
            state.locked.add(row(status, 33L, 1));
            state.reassignResult = 1;
            ContentMaintenanceTaskService service = service(
                    state, Set.of(1), Set.of(1), Set.of(22L, 33L), null);

            ContentMaintenanceTaskDTO result =
                    service.reassign(101L, reassign(33L), 77L);

            assertEquals(status, result.getStatus());
            assertEquals(33L, result.getAssigneeUid());
            assertTrue(result.getCanReassign());
            assertEquals(1, state.reassignCalls);
            assertEquals(1, state.auditCalls);
            assertEquals(33L, state.replacementUid);
        }
    }

    @Test
    void submittedTaskCannotBeReassigned() {
        MapperState state = new MapperState();
        state.locked.add(row("SUBMITTED", 22L, 1));
        ContentMaintenanceTaskService service = service(
                state, Set.of(1), Set.of(1), Set.of(22L, 33L), null);

        BizException error = assertThrows(BizException.class,
                () -> service.reassign(101L, reassign(33L), 77L));

        assertEquals(ErrorCode.INVALID_STATUS.getCode(), error.getCode());
        assertEquals(0, state.reassignCalls);
        assertEquals(0, state.auditCalls);
    }

    @Test
    void replacementMustHaveCurrentDomainMaintenanceRole() {
        MapperState state = new MapperState();
        state.locked.add(row("OPEN", 22L, 1));
        ContentMaintenanceTaskService service = service(
                state, Set.of(1), Set.of(1), Set.of(22L), null);

        BizException error = assertThrows(BizException.class,
                () -> service.reassign(101L, reassign(33L), 77L));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), error.getCode());
        assertEquals(0, state.reassignCalls);
        assertEquals(0, state.auditCalls);
    }

    @Test
    void nonModeratorCannotReassignMaintenanceTask() {
        MapperState state = new MapperState();
        state.locked.add(row("OPEN", 22L, 1));
        ContentMaintenanceTaskService service = service(
                state, Set.of(), Set.of(1), Set.of(22L, 33L), null);

        BizException error = assertThrows(BizException.class,
                () -> service.reassign(101L, reassign(33L), 77L));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), error.getCode());
        assertEquals(0, state.reassignCalls);
    }

    @Test
    void assigneeWithoutActiveMaintenanceRoleCannotClaimOrSubmit() {
        MapperState claimState = new MapperState();
        claimState.locked.add(row("OPEN", 22L, 1));
        ContentMaintenanceTaskService claimService =
                service(claimState, Set.of(), Set.of(), null);

        BizException claimError = assertThrows(BizException.class,
                () -> claimService.claim(101L, 22L));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), claimError.getCode());
        assertEquals(0, claimState.claimCalls);

        MapperState submitState = new MapperState();
        submitState.locked.add(row("CLAIMED", 22L, 1));
        ContentMaintenanceTaskService submitService =
                service(submitState, Set.of(), Set.of(), publicPost(901L, 22L, 1));

        BizException submitError = assertThrows(BizException.class,
                () -> submitService.submit(101L, submit("POST", 901L), 22L));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), submitError.getCode());
        assertEquals(0, submitState.submitCalls);
    }

    @Test
    void moderatorCannotAssignTaskToUserWithoutActiveMaintenanceRole() {
        MapperState state = new MapperState();
        ContentMaintenanceTaskService service =
                service(state, Set.of(1), Set.of(), publicPost(901L, 77L, 1));
        ContentMaintenanceTaskCreateCmd cmd = new ContentMaintenanceTaskCreateCmd();
        cmd.setDomain(1);
        cmd.setSourceType("MANUAL");
        cmd.setSourcePostId(901L);
        cmd.setAssigneeUid(22L);
        cmd.setTitle("Refresh public guidance");
        cmd.setDetail("Update the public guidance after the role is granted.");

        BizException error = assertThrows(BizException.class,
                () -> service.create(cmd, 77L));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), error.getCode());
        assertEquals(0, state.insertCalls);
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

    @Test
    void maintenanceCandidatesRequireActiveRoleAndHideInternalDetail() {
        MapperState state = new MapperState();
        ContentMaintenanceTaskRow candidate = row("OPEN", 22L, 1);
        candidate.setSourcePostType(Post.TYPE_TECH_ARTICLE);
        state.candidateRows.add(candidate);
        ContentMaintenanceTaskService service = service(state, Set.of(), null);

        List<ContentMaintenanceCandidateDTO> items =
                service.listCandidates(22L, 1, "CHANNEL_HEALTH", Post.TYPE_TECH_ARTICLE, 0, 20).getItems();

        assertEquals(1, items.size());
        assertEquals("ASSIGNED_TO_ME", items.get(0).getAssignmentStatus());
        assertTrue(items.get(0).getCanClaim());
        assertFalse(items.get(0).toString().contains("updated public guidance"));
    }

    @Test
    void versionedChannelHealthTaskRequiresMatchingCurrentPublicRevision() {
        MapperState state = new MapperState();
        state.locked.add(row("OPEN", 22L, 1));
        state.revisionResult = revisionResult(901L, 7);
        ContentMaintenanceTaskService service = service(
                state, Set.of(1), Set.of(1), Set.of(22L), publicPost(901L, 44L, 1));

        ContentMaintenanceTaskDTO result = service.create(versionedChannelHealthTask(901L, 7L), 77L);

        assertEquals("OPEN", result.getStatus());
        assertEquals(1, state.insertCalls);
        assertEquals(1, state.revisionQueryCalls);
        assertEquals(List.of(901L), state.revisionQuery.postIds());
        assertEquals(List.of(1), state.revisionQuery.authorizedChannelCodes());
        assertEquals(77L, state.revisionQuery.subjectUid());
    }

    @Test
    void versionedChannelHealthTaskRejectsStaleRevisionWithoutCreatingTask() {
        MapperState state = new MapperState();
        state.revisionResult = revisionResult(901L, 8);
        ContentMaintenanceTaskService service = service(
                state, Set.of(1), Set.of(1), Set.of(22L), publicPost(901L, 44L, 1));

        BizException error = assertThrows(BizException.class,
                () -> service.create(versionedChannelHealthTask(901L, 7L), 77L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), error.getCode());
        assertEquals(0, state.insertCalls);
    }

    @Test
    void duplicateVersionedChannelHealthTaskMapsUniqueConflictToBusinessError() {
        MapperState state = new MapperState();
        state.revisionResult = revisionResult(901L, 7);
        state.duplicateInsert = true;
        ContentMaintenanceTaskService service = service(
                state, Set.of(1), Set.of(1), Set.of(22L), publicPost(901L, 44L, 1));

        BizException error = assertThrows(BizException.class,
                () -> service.create(versionedChannelHealthTask(901L, 7L), 77L));

        assertEquals(ErrorCode.DUPLICATE_OPERATION.getCode(), error.getCode());
        assertEquals(1, state.insertCalls);
        assertEquals(0, state.auditCalls);
    }

    @Test
    void channelHealthTaskRejectsPartialVersionAssociation() {
        MapperState state = new MapperState();
        ContentMaintenanceTaskService service = service(
                state, Set.of(1), Set.of(1), Set.of(22L), publicPost(901L, 44L, 1));
        ContentMaintenanceTaskCreateCmd cmd = versionedChannelHealthTask(901L, 7L);
        cmd.setSourceRefId(null);

        BizException error = assertThrows(BizException.class, () -> service.create(cmd, 77L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), error.getCode());
        assertEquals(0, state.revisionQueryCalls);
        assertEquals(0, state.insertCalls);
    }

    @Test
    void legacyChannelHealthTaskWithoutAssociationRemainsSupported() {
        MapperState state = new MapperState();
        state.locked.add(row("OPEN", 22L, 1));
        ContentMaintenanceTaskService service = service(state, Set.of(1), null);
        ContentMaintenanceTaskCreateCmd cmd = versionedChannelHealthTask(null, null);

        ContentMaintenanceTaskDTO result = service.create(cmd, 77L);

        assertEquals("OPEN", result.getStatus());
        assertEquals(0, state.revisionQueryCalls);
        assertEquals(1, state.insertCalls);
    }

    @Test
    void assigneeAuthorizationStillPrecedesVersionedChannelHealthRevisionQuery() {
        MapperState state = new MapperState();
        ContentMaintenanceTaskService service = service(
                state, Set.of(1), Set.of(), Set.of(22L), publicPost(901L, 44L, 1));

        BizException error = assertThrows(BizException.class,
                () -> service.create(versionedChannelHealthTask(901L, 7L), 77L));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), error.getCode());
        assertEquals(0, state.revisionQueryCalls);
        assertEquals(0, state.insertCalls);
    }

    private static ContentMaintenanceTaskService service(MapperState state, Set<Integer> moderatedDomains,
                                                          PostDTO post) {
        return service(state, moderatedDomains, Set.of(1), post);
    }

    private static ContentMaintenanceTaskService service(
            MapperState state,
            Set<Integer> moderatedDomains,
            Set<Integer> maintenanceDomains,
            PostDTO post) {
        return service(state, moderatedDomains, maintenanceDomains, Set.of(22L), post);
    }

    private static ContentMaintenanceTaskService service(
            MapperState state,
            Set<Integer> moderatedDomains,
            Set<Integer> maintenanceDomains,
            Set<Long> maintenanceUids,
            PostDTO post) {
        return new ContentMaintenanceTaskService(
                taskMapper(state),
                new SnowflakeIdGenerator(),
                new ModeratorStub(moderatedDomains),
                new RoleAccessStub(maintenanceDomains, maintenanceUids),
                new AuditStub(state),
                postFacade(post),
                collaborationMapper(state),
                revisionQuery(state)
        );
    }

    private static ContentMaintenanceTaskCreateCmd versionedChannelHealthTask(
            Long sourcePostId,
            Long sourceRefId) {
        ContentMaintenanceTaskCreateCmd cmd = new ContentMaintenanceTaskCreateCmd();
        cmd.setDomain(1);
        cmd.setSourceType("CHANNEL_HEALTH");
        cmd.setSourcePostId(sourcePostId);
        cmd.setSourceRefId(sourceRefId);
        cmd.setAssigneeUid(22L);
        cmd.setTitle("Review current channel guidance");
        cmd.setDetail("Review the current public revision against the maintenance criteria.");
        return cmd;
    }

    private static PostContentRevisionQueryResult revisionResult(Long postId, int version) {
        return PostContentRevisionQueryResult.available(List.of(new PostContentRevisionSnapshot(
                postId,
                PostContentRevisionSnapshot.Status.FOUND,
                LocalDateTime.of(1970, 1, 1, 0, 0),
                true,
                "test-token",
                version,
                LocalDateTime.of(2026, 8, 4, 0, 0))));
    }

    private static ContentMaintenanceTaskSubmitCmd submit(String type, Long id) {
        ContentMaintenanceTaskSubmitCmd cmd = new ContentMaintenanceTaskSubmitCmd();
        cmd.setDeliveryType(type);
        cmd.setDeliveryRefId(id);
        cmd.setDeliveryPostId(id);
        cmd.setNote("updated public guidance");
        return cmd;
    }

    private static ContentMaintenanceTaskReassignCmd reassign(Long replacementUid) {
        ContentMaintenanceTaskReassignCmd cmd = new ContentMaintenanceTaskReassignCmd();
        cmd.setReplacementUid(replacementUid);
        cmd.setReason("replace assignee after role access changed");
        return cmd;
    }

    private static ContentMaintenanceTaskReviewCmd review(String decision) {
        ContentMaintenanceTaskReviewCmd cmd = new ContentMaintenanceTaskReviewCmd();
        cmd.setDecision(decision);
        cmd.setReasonCode("APPROVED".equals(decision) ? "QUALITY_VERIFIED" : "CONTENT_INCOMPLETE");
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
                    case "attemptTableExists" -> 1;
                    case "userExists" -> 1;
                    case "dispatchBatchExists" -> 1;
                    case "insert" -> {
                        state.insertCalls++;
                        if (state.duplicateInsert) {
                            throw new DuplicateKeyException("uk_maintenance_source");
                        }
                        yield 1;
                    }
                    case "lockById" -> state.locked.removeFirst();
                    case "claim" -> {
                        state.claimCalls++;
                        yield state.claimResult;
                    }
                    case "reassign" -> {
                        state.reassignCalls++;
                        state.replacementUid = (Long) args[1];
                        yield state.reassignResult;
                    }
                    case "submit" -> {
                        state.submitCalls++;
                        yield state.submitResult;
                    }
                    case "insertAttempt" -> {
                        state.attemptInsertCalls++;
                        ContentMaintenanceTaskAttemptRow attempt = new ContentMaintenanceTaskAttemptRow();
                        attempt.setId((Long) args[0]);
                        attempt.setTaskId((Long) args[1]);
                        attempt.setAttemptNo((Integer) args[2]);
                        attempt.setDeliveryType((String) args[3]);
                        attempt.setDeliveryRefId((Long) args[4]);
                        attempt.setDeliveryPostId((Long) args[5]);
                        attempt.setNote((String) args[6]);
                        attempt.setSubmittedByUid((Long) args[7]);
                        state.attempt = attempt;
                        yield 1;
                    }
                    case "lockAttempt" -> state.attempt;
                    case "setCurrentAttemptNo" -> {
                        state.currentAttemptNoCalls++;
                        yield 1;
                    }
                    case "decideAttempt" -> {
                        state.attemptDecisionCalls++;
                        if (state.attempt != null) {
                            state.attempt.setDecision((String) args[2]);
                            state.attempt.setReasonCode((String) args[3]);
                            state.attempt.setReviewNote((String) args[5]);
                        }
                        yield 1;
                    }
                    case "approve" -> {
                        state.approveCalls++;
                        yield state.approveResult;
                    }
                    case "listQueue" -> {
                        state.requestedDomains.add((Integer) args[0]);
                        yield state.queueRows;
                    }
                    case "listCandidates" -> state.candidateRows;
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

    private static PostContentRevisionQueryFacade revisionQuery(MapperState state) {
        return (PostContentRevisionQueryFacade) Proxy.newProxyInstance(
                PostContentRevisionQueryFacade.class.getClassLoader(),
                new Class<?>[]{PostContentRevisionQueryFacade.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "query" -> {
                        state.revisionQueryCalls++;
                        state.revisionQuery = (PostContentRevisionQuery) args[0];
                        yield state.revisionResult;
                    }
                    case "toString" -> "PostContentRevisionQueryFacadeStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static final class MapperState {
        private final Deque<ContentMaintenanceTaskRow> locked = new ArrayDeque<>();
        private final List<ContentMaintenanceTaskRow> queueRows = new ArrayList<>();
        private final List<ContentMaintenanceTaskRow> candidateRows = new ArrayList<>();
        private final List<Integer> requestedDomains = new ArrayList<>();
        private int claimResult;
        private int submitResult;
        private int approveResult;
        private int reassignResult;
        private int claimCalls;
        private int reassignCalls;
        private int submitCalls;
        private int approveCalls;
        private int attemptInsertCalls;
        private int currentAttemptNoCalls;
        private int attemptDecisionCalls;
        private int auditCalls;
        private int insertCalls;
        private int revisionQueryCalls;
        private boolean duplicateInsert;
        private Long replacementUid;
        private SeriesRow series;
        private ContentMaintenanceTaskAttemptRow attempt;
        private int seriesContributor;
        private PostContentRevisionQuery revisionQuery;
        private PostContentRevisionQueryResult revisionResult;
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
        private final Set<Integer> maintenanceDomains;
        private final Set<Long> maintenanceUids;

        private RoleAccessStub(Set<Integer> maintenanceDomains, Set<Long> maintenanceUids) {
            super(null);
            this.maintenanceDomains = maintenanceDomains;
            this.maintenanceUids = maintenanceUids;
        }

        @Override
        public boolean hasActiveGrant(Long uid, String roleCode, String domainCode) {
            return uid != null && maintenanceUids.contains(uid)
                    && "CHANNEL_RESOURCE_MAINTAINER".equals(roleCode)
                    && maintenanceDomains.contains(switch (domainCode) {
                        case "TECH" -> 1;
                        case "CAREER" -> 2;
                        case "READING" -> 3;
                        case "LIFESTYLE" -> 4;
                        case "INVESTMENT" -> 5;
                        default -> 0;
                    });
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

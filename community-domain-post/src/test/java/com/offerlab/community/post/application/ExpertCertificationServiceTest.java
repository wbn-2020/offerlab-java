package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.dto.ExpertCertificationApplicationDTO;
import com.offerlab.community.post.api.dto.ExpertCertificationApplyCmd;
import com.offerlab.community.post.api.dto.ExpertCertificationEligibilityDTO;
import com.offerlab.community.post.api.dto.ExpertCertificationReviewCmd;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.ExpertCertificationMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ExpertCertificationApplicationPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpertCertificationServiceTest {

    @Test
    void investmentEligibilityRequiresExplicitRiskAcknowledgementBeforeSubmit() {
        ExpertCertificationMapperState mapperState = new ExpertCertificationMapperState(1);
        ExpertCertificationService service = newService(mapperState, eligibleInvestmentPosts(7L));

        ExpertCertificationEligibilityDTO eligibility = service.getEligibility(7L, Post.DOMAIN_INVESTMENT);

        assertTrue(eligibility.getEligible(), "three recent public domain posts should pass the pilot gate");
        assertTrue(eligibility.getRiskAcknowledgementRequired(), "investment applications must force risk acknowledgement");
        assertNotNull(eligibility.getRiskWarning());

        BizException ex = assertThrows(BizException.class, () -> service.submit(applyCmd(false), 7L));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());

        ExpertCertificationApplicationDTO created = service.submit(applyCmd(true), 7L);

        assertEquals(ExpertCertificationService.STATUS_SUBMITTED, created.getStatus());
        assertFalse(created.getAutoCertified(), "pilot review must never auto-certify a user");
        assertTrue(Boolean.TRUE.equals(created.getRiskAcknowledged()));
        assertEquals(1, mapperState.applicationsById.size());
    }

    @Test
    void reviewRequiresDomainModeratorAndWritesManualDecisionOnly() {
        ExpertCertificationMapperState mapperState = new ExpertCertificationMapperState(1);
        DomainModeratorStub moderatorStub = new DomainModeratorStub();
        moderatorStub.allowedDomains.put(domainKey(88L, Post.DOMAIN_CAREER), true);
        AdminAuditStub auditStub = new AdminAuditStub();
        ExpertCertificationService service = newService(
                mapperState,
                eligibleCareerPosts(9L),
                moderatorStub,
                auditStub,
                new MigrationCheckStub(true));

        ExpertCertificationApplicationDTO created = service.submit(careerApplyCmd(), 9L);
        ExpertCertificationReviewCmd reviewCmd = new ExpertCertificationReviewCmd();
        reviewCmd.setApproved(true);
        reviewCmd.setNote("manual pilot approval");

        ExpertCertificationApplicationDTO reviewed = service.review(created.getId(), reviewCmd, 88L);

        assertEquals(ExpertCertificationService.STATUS_APPROVED, reviewed.getStatus());
        assertEquals(88L, reviewed.getReviewerUid());
        assertFalse(reviewed.getAutoCertified(), "approval should remain advisory until a separate certification flow exists");
        assertEquals(List.of("88:2"), moderatorStub.requireModerateCalls);
        assertEquals(1, auditStub.records.size());
        AdminAuditRecord audit = auditStub.records.get(0);
        assertEquals(88L, audit.operatorUid());
        assertEquals("EXPERT_CERT_APPLICATION_REVIEW", audit.action());
        assertEquals("EXPERT_CERT_APPLICATION", audit.resourceType());
        assertEquals(created.getId(), audit.resourceId());
        assertEquals("manual pilot approval", audit.remark());
    }

    @Test
    void ownerCanRevokeSubmittedApplicationWithoutCreatingNewCertificationState() {
        ExpertCertificationMapperState mapperState = new ExpertCertificationMapperState(1);
        ExpertCertificationService service = newService(mapperState, eligibleCareerPosts(12L));

        ExpertCertificationApplicationDTO created = service.submit(careerApplyCmd(), 12L);
        ExpertCertificationApplicationDTO revoked = service.revoke(created.getId(), "withdrawn by applicant", 12L);

        assertEquals(ExpertCertificationService.STATUS_REVOKED, revoked.getStatus());
        assertEquals(12L, revoked.getRevokedBy());
        assertFalse(revoked.getAutoCertified());
    }

    @Test
    void submitFailsWhenNamedLockCannotBeAcquired() {
        ExpertCertificationMapperState mapperState = new ExpertCertificationMapperState(1);
        mapperState.nextLockResult = 0;
        ExpertCertificationService service = newService(mapperState, eligibleCareerPosts(15L));

        BizException ex = assertThrows(BizException.class, () -> service.submit(careerApplyCmd(), 15L));

        assertEquals(ErrorCode.DEPENDENCY_ERROR.getCode(), ex.getCode());
        assertTrue(mapperState.acquiredLocks.contains("offerlab:expert-cert:15:2"));
        assertTrue(mapperState.releasedLocks.isEmpty());
    }

    @Test
    void listMineReturnsEmptyWhenReadinessIsBlocked() {
        ExpertCertificationMapperState mapperState = new ExpertCertificationMapperState(1);
        ExpertCertificationService service = newService(
                mapperState,
                eligibleCareerPosts(22L),
                new DomainModeratorStub(),
                new AdminAuditStub(),
                new MigrationCheckStub(false));

        assertTrue(service.listMine(22L, Post.DOMAIN_CAREER).isEmpty());
    }

    private static ExpertCertificationService newService(ExpertCertificationMapperState mapperState,
                                                         Map<Long, List<PostPO>> postsByAuthorDomain) {
        return newService(mapperState, postsByAuthorDomain, new DomainModeratorStub(), new AdminAuditStub(), new MigrationCheckStub(true));
    }

    private static ExpertCertificationService newService(ExpertCertificationMapperState mapperState,
                                                         Map<Long, List<PostPO>> postsByAuthorDomain,
                                                         DomainModeratorService domainModeratorService,
                                                         AdminAuditService adminAuditService,
                                                         MigrationCheckService migrationCheckService) {
        ExpertCertificationService service = new ExpertCertificationService(
                mapper(mapperState),
                postMapper(postsByAuthorDomain),
                new SnowflakeIdGenerator(),
                domainModeratorService,
                adminAuditService,
                migrationCheckService
        );
        return service;
    }

    private static ExpertCertificationMapper mapper(ExpertCertificationMapperState state) {
        return (ExpertCertificationMapper) Proxy.newProxyInstance(
                ExpertCertificationMapper.class.getClassLoader(),
                new Class<?>[]{ExpertCertificationMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> state.tableExists;
                    case "selectActiveByApplicantAndDomain" -> state.selectActiveByApplicantAndDomain((Long) args[0], (Integer) args[1]);
                    case "selectMine" -> state.selectMine((Long) args[0], (Integer) args[1], (Integer) args[2]);
                    case "selectReviewQueue" -> state.selectReviewQueue((Integer) args[0], (Integer) args[1], (Integer) args[2]);
                    case "selectById" -> state.applicationsById.get((Long) args[0]);
                    case "acquireNamedLock" -> {
                        String lockName = (String) args[0];
                        state.acquiredLocks.add(lockName);
                        yield state.nextLockResult;
                    }
                    case "releaseNamedLock" -> {
                        String lockName = (String) args[0];
                        state.releasedLocks.add(lockName);
                        yield state.nextReleaseResult;
                    }
                    case "insert" -> {
                        ExpertCertificationApplicationPO po = (ExpertCertificationApplicationPO) args[0];
                        state.applicationsById.put(po.getId(), clone(po));
                        yield 1;
                    }
                    case "updateById" -> {
                        ExpertCertificationApplicationPO po = (ExpertCertificationApplicationPO) args[0];
                        state.applicationsById.put(po.getId(), clone(po));
                        yield 1;
                    }
                    case "toString" -> "ExpertCertificationMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static PostMapper postMapper(Map<Long, List<PostPO>> postsByAuthorDomain) {
        return (PostMapper) Proxy.newProxyInstance(
                PostMapper.class.getClassLoader(),
                new Class<?>[]{PostMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectPublicPosts" -> {
                        Long authorId = (Long) args[0];
                        Integer domain = (Integer) args[4];
                        yield postsByAuthorDomain.getOrDefault(authorKey(authorId, domain), List.of());
                    }
                    case "toString" -> "PostMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static ExpertCertificationApplyCmd applyCmd(boolean riskAcknowledged) {
        ExpertCertificationApplyCmd cmd = new ExpertCertificationApplyCmd();
        cmd.setDomain(Post.DOMAIN_INVESTMENT);
        cmd.setEvidenceSummary("three public investment education posts");
        cmd.setEvidenceLinks(List.of("https://example.test/post/1", "https://example.test/post/2"));
        cmd.setRiskAcknowledged(riskAcknowledged);
        return cmd;
    }

    private static ExpertCertificationApplyCmd careerApplyCmd() {
        ExpertCertificationApplyCmd cmd = new ExpertCertificationApplyCmd();
        cmd.setDomain(Post.DOMAIN_CAREER);
        cmd.setEvidenceSummary("career coaching pilot");
        cmd.setEvidenceLinks(List.of("https://example.test/post/9"));
        cmd.setRiskAcknowledged(false);
        return cmd;
    }

    private static Map<Long, List<PostPO>> eligibleInvestmentPosts(Long uid) {
        Map<Long, List<PostPO>> result = new LinkedHashMap<>();
        result.put(authorKey(uid, Post.DOMAIN_INVESTMENT), List.of(
                publicPost(1001L, uid, 5),
                publicPost(1002L, uid, 15),
                publicPost(1003L, uid, 45)
        ));
        return result;
    }

    private static Map<Long, List<PostPO>> eligibleCareerPosts(Long uid) {
        Map<Long, List<PostPO>> result = new LinkedHashMap<>();
        result.put(authorKey(uid, Post.DOMAIN_CAREER), List.of(
                publicPost(2001L, uid, 3),
                publicPost(2002L, uid, 18),
                publicPost(2003L, uid, 44)
        ));
        return result;
    }

    private static PostPO publicPost(Long postId, Long authorId, int daysAgo) {
        PostPO post = new PostPO();
        post.setId(postId);
        post.setAuthorId(authorId);
        post.setPostStatus(Post.STATUS_PUBLISHED);
        post.setVisibility(Post.VIS_PUBLIC);
        post.setCreateTime(LocalDateTime.now().minusDays(daysAgo));
        post.setUpdateTime(post.getCreateTime());
        return post;
    }

    private static Long authorKey(Long authorId, Integer domain) {
        long left = authorId == null ? 0L : authorId;
        long right = domain == null ? 0L : domain;
        return left * 100L + right;
    }

    private static ExpertCertificationApplicationPO clone(ExpertCertificationApplicationPO source) {
        ExpertCertificationApplicationPO copy = new ExpertCertificationApplicationPO();
        copy.setId(source.getId());
        copy.setApplicantUid(source.getApplicantUid());
        copy.setDomain(source.getDomain());
        copy.setStatus(source.getStatus());
        copy.setEvidenceSummary(source.getEvidenceSummary());
        copy.setEvidenceLinksJson(source.getEvidenceLinksJson());
        copy.setEligibilityPassed(source.getEligibilityPassed());
        copy.setEligibilitySummary(source.getEligibilitySummary());
        copy.setEligibilitySnapshotJson(source.getEligibilitySnapshotJson());
        copy.setRiskAcknowledged(source.getRiskAcknowledged());
        copy.setRiskWarning(source.getRiskWarning());
        copy.setReviewerUid(source.getReviewerUid());
        copy.setReviewNote(source.getReviewNote());
        copy.setReviewTime(source.getReviewTime());
        copy.setRevokedBy(source.getRevokedBy());
        copy.setRevokeNote(source.getRevokeNote());
        copy.setRevokedTime(source.getRevokedTime());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setIsDeleted(source.getIsDeleted());
        return copy;
    }

    private static final class ExpertCertificationMapperState {
        private final int tableExists;
        private final Map<Long, ExpertCertificationApplicationPO> applicationsById = new LinkedHashMap<>();
        private final List<String> acquiredLocks = new ArrayList<>();
        private final List<String> releasedLocks = new ArrayList<>();
        private Integer nextLockResult = 1;
        private Integer nextReleaseResult = 1;

        private ExpertCertificationMapperState(int tableExists) {
            this.tableExists = tableExists;
        }

        private ExpertCertificationApplicationPO selectActiveByApplicantAndDomain(Long applicantUid, Integer domain) {
            return applicationsById.values().stream()
                    .filter(item -> Objects.equals(item.getApplicantUid(), applicantUid))
                    .filter(item -> Objects.equals(item.getDomain(), domain))
                    .filter(item -> Objects.equals(item.getIsDeleted(), 0) || item.getIsDeleted() == null)
                    .filter(item -> Objects.equals(item.getStatus(), ExpertCertificationService.STATUS_SUBMITTED)
                            || Objects.equals(item.getStatus(), ExpertCertificationService.STATUS_APPROVED))
                    .findFirst()
                    .map(ExpertCertificationServiceTest::clone)
                    .orElse(null);
        }

        private List<ExpertCertificationApplicationPO> selectMine(Long applicantUid, Integer domain, Integer limit) {
            return applicationsById.values().stream()
                    .filter(item -> Objects.equals(item.getApplicantUid(), applicantUid))
                    .filter(item -> domain == null || Objects.equals(item.getDomain(), domain))
                    .limit(limit == null ? 20 : limit)
                    .map(ExpertCertificationServiceTest::clone)
                    .toList();
        }

        private List<ExpertCertificationApplicationPO> selectReviewQueue(Integer domain, Integer status, Integer limit) {
            return applicationsById.values().stream()
                    .filter(item -> domain == null || Objects.equals(item.getDomain(), domain))
                    .filter(item -> status == null || Objects.equals(item.getStatus(), status))
                    .limit(limit == null ? 20 : limit)
                    .map(ExpertCertificationServiceTest::clone)
                    .toList();
        }
    }

    private static final class MigrationCheckStub extends MigrationCheckService {
        private final boolean expertCertificationReady;

        private MigrationCheckStub(boolean expertCertificationReady) {
            super(null);
            this.expertCertificationReady = expertCertificationReady;
        }

        @Override
        public boolean expertCertificationReady() {
            return expertCertificationReady;
        }
    }

    private static final class DomainModeratorStub extends DomainModeratorService {
        private final Map<String, Boolean> allowedDomains = new LinkedHashMap<>();
        private final List<String> requireModerateCalls = new ArrayList<>();

        private DomainModeratorStub() {
            super(null, null, null, null);
        }

        @Override
        public boolean canModerateDomain(Long uid, Integer domain) {
            return allowedDomains.getOrDefault(domainKey(uid, domain), false);
        }

        @Override
        public void requireModerateDomain(Long uid, Integer domain) {
            requireModerateCalls.add(domainKey(uid, domain));
            if (!canModerateDomain(uid, domain)) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
        }
    }

    private static final class AdminAuditStub extends AdminAuditService {
        private final List<AdminAuditRecord> records = new ArrayList<>();

        private AdminAuditStub() {
            super(null, null, null);
        }

        @Override
        public void recordRequired(Long operatorUid, String action, String resourceType, Object resourceId,
                                   Object before, Object after, String remark) {
            records.add(new AdminAuditRecord(operatorUid, action, resourceType, resourceId, before, after, remark));
        }
    }

    private record AdminAuditRecord(Long operatorUid, String action, String resourceType,
                                    Object resourceId, Object before, Object after, String remark) {
    }

    private static String domainKey(Long uid, Integer domain) {
        return uid + ":" + domain;
    }
}

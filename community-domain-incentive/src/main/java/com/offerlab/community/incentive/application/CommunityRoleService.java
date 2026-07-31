package com.offerlab.community.incentive.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleActionCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleApplicationCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleApplicationDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleDefinitionCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleDefinitionDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleEvidenceDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleEvidenceItemDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleEligibilityDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleGrantDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleGrantHistoryDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleMetricCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleReviewContextDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleWorkspaceCardDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleWorkspaceDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleWorkspaceV8DTO;
import com.offerlab.community.incentive.api.IncentiveDtos.ReviewCmd;
import com.offerlab.community.incentive.domain.IncentiveTypes;
import com.offerlab.community.incentive.infrastructure.IncentiveMapper;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RoleApplicationPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RoleDefinitionPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RoleGrantPO;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.CommunityRoleAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityRoleService {
    private static final Set<String> APPLICATION_STATUSES = Set.of("SUBMITTED", "APPROVED", "REJECTED");
    private static final Set<String> PROHIBITED_ROLE_FRAGMENTS =
            Set.of("ADMIN", "MODERATOR", "EXPERT", "CERTIFIED", "CERTIFICATION", "REVIEWER", "AUDITOR");
    private static final Set<String> PROHIBITED_ROLE_TERMS =
            Set.of("admin", "moderator", "expert", "certified", "certification", "reviewer", "auditor",
                    "管理员", "版主", "专家", "认证", "审核员", "审计员");
    private final IncentiveMapper mapper;
    private final SnowflakeIdGenerator idGenerator;
    private final AdminPermissionService adminPermissionService;
    private final CommunityRoleAccessService communityRoleAccessService;
    private final AdminAuditService adminAuditService;
    private final ObjectMapper objectMapper;

    public RoleWorkspaceDTO workspace(Long userId, Integer page, Integer size) {
        requireUser(userId);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<RoleDefinitionDTO> definitions = mapper.selectRoleDefinitions(IncentiveTypes.MAX_LIST_LIMIT).stream()
                .map(this::toDefinitionDto).toList();
        List<RoleApplicationDTO> applications = mapper.selectUserRoleApplications(
                        userId, (safePage - 1) * safeSize, safeSize).stream()
                .map(this::toApplicationDto).toList();
        List<RoleGrantDTO> grants = mapper.selectUserRoleGrants(
                        userId, (safePage - 1) * safeSize, safeSize).stream()
                .map(this::toGrantDto).toList();
        return RoleWorkspaceDTO.builder()
                .definitions(definitions)
                .applications(page(applications, mapper.countUserRoleApplications(userId), safePage, safeSize))
                .grants(page(grants, mapper.countUserRoleGrants(userId), safePage, safeSize))
                .build();
    }

    public RoleEligibilityDTO eligibility(Long userId, String roleCode, String domainCode) {
        requireUser(userId);
        RoleDefinitionPO definition = requireDefinition(roleCode, domainCode);
        return evaluate(userId, definition);
    }

    public RoleWorkspaceV8DTO workspaceV8(Long userId) {
        requireUser(userId);
        List<RoleDefinitionPO> definitions = mapper.selectRoleDefinitions(IncentiveTypes.MAX_LIST_LIMIT);
        Map<String, RoleApplicationPO> applications = mapper.selectLatestUserRoleApplications(
                        userId, IncentiveTypes.MAX_LIST_LIMIT).stream()
                .collect(Collectors.toMap(
                        item -> scopeKey(item.getRoleCode(), item.getDomainCode()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, RoleGrantPO> grants = mapper.selectLatestUserRoleGrants(
                        userId, IncentiveTypes.MAX_LIST_LIMIT).stream()
                .collect(Collectors.toMap(
                        item -> scopeKey(item.getRoleCode(), item.getDomainCode()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<RoleWorkspaceCardDTO> cards = definitions.stream()
                .map(definition -> {
                    String key = scopeKey(definition.getRoleCode(), definition.getDomainCode());
                    RoleApplicationPO application = applications.get(key);
                    RoleGrantPO grant = grants.get(key);
                    RoleEligibilityDTO eligibility = evaluate(userId, definition);
                    return RoleWorkspaceCardDTO.builder()
                            .definition(toDefinitionDto(definition))
                            .evidence(toEvidenceDto(userId, definition, eligibility, application, grant))
                            .application(application == null ? null : toApplicationDto(application))
                            .grant(grant == null ? null : toGrantDto(grant))
                            .build();
                })
                .toList();
        return RoleWorkspaceV8DTO.builder()
                .generatedAt(LocalDateTime.now())
                .roles(cards)
                .build();
    }

    public RoleEvidenceDTO evidence(Long userId, String roleCode, String domainCode) {
        requireUser(userId);
        RoleDefinitionPO definition = requireDefinition(roleCode, domainCode);
        RoleEligibilityDTO eligibility = evaluate(userId, definition);
        RoleApplicationPO application = mapper.selectLatestUserRoleApplication(
                userId, definition.getRoleCode(), definition.getDomainCode());
        RoleGrantPO grant = mapper.selectLatestUserRoleGrant(
                userId, definition.getRoleCode(), definition.getDomainCode());
        return toEvidenceDto(userId, definition, eligibility, application, grant);
    }

    public RoleReviewContextDTO reviewContext(Long applicationId, String rawReason, Long operatorUid) {
        requireAdmin(operatorUid);
        String reason = IncentiveTypes.requireReason(rawReason);
        RoleApplicationPO application = mapper.selectRoleApplication(requirePositive(applicationId));
        if (application == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        RoleDefinitionPO definition = mapper.selectRoleDefinition(
                application.getRoleCode(), application.getDomainCode());
        if (definition == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        RoleEligibilityDTO eligibility = evaluate(application.getApplicantUid(), definition);
        RoleGrantPO grant = mapper.selectLatestUserRoleGrant(
                application.getApplicantUid(), application.getRoleCode(), application.getDomainCode());
        Map<String, Object> summary = mapper.selectRoleReviewContributionSummary(
                application.getApplicantUid(), domainNumber(application.getDomainCode()));
        List<RoleGrantHistoryDTO> history = grant == null
                ? List.of()
                : mapper.selectRoleGrantHistory(grant.getId(), 50).stream()
                .map(CommunityRoleService::toRoleGrantHistory)
                .toList();
        RoleReviewContextDTO context = RoleReviewContextDTO.builder()
                .application(toApplicationDto(application))
                .definition(toDefinitionDto(definition))
                .evidence(toEvidenceDto(application.getApplicantUid(), definition, eligibility, application, grant))
                .currentGrant(grant == null ? null : toGrantDto(grant))
                .recentTrustedContributionCount(longNumber(summary.get("recentTrustedContributionCount")))
                .completedMaintenanceTaskCount(longNumber(summary.get("completedMaintenanceTaskCount")))
                .returnedMaintenanceTaskCount(longNumber(summary.get("returnedMaintenanceTaskCount")))
                .activeViolationCount((long) eligibility.getViolationCount())
                .riskFrozen(eligibility.getRiskFrozen())
                .grantHistory(history)
                .build();
        adminAuditService.recordRequired(operatorUid, "COMMUNITY_ROLE_REVIEW_CONTEXT_VIEW",
                "COMMUNITY_ROLE_APPLICATION", applicationId, null,
                Map.of(
                        "applicantUid", application.getApplicantUid(),
                        "roleCode", application.getRoleCode(),
                        "domainCode", application.getDomainCode()
                ),
                reason);
        return context;
    }

    public PageResult<RoleApplicationDTO> adminApplications(String status, Integer page, Integer size,
                                                             Long operatorUid) {
        requireAdmin(operatorUid);
        String normalized = status == null ? null : requireApplicationStatus(status);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<RoleApplicationDTO> items = mapper.selectRoleApplicationQueue(
                        normalized, (safePage - 1) * safeSize, safeSize).stream()
                .map(this::toApplicationDto).toList();
        return page(items, mapper.countRoleApplicationQueue(normalized), safePage, safeSize);
    }

    public PageResult<RoleGrantDTO> adminGrants(String status, Integer page, Integer size, Long operatorUid) {
        requireAdmin(operatorUid);
        String normalized = status == null ? null : requireGrantStatus(status);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<RoleGrantDTO> items = mapper.selectRoleGrantQueue(
                        normalized, (safePage - 1) * safeSize, safeSize).stream()
                .map(this::toGrantDto).toList();
        return page(items, mapper.countRoleGrantQueue(normalized), safePage, safeSize);
    }

    @Transactional
    public void updateMetric(Long userId, RoleMetricCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        requireUser(userId);
        if (operatorUid.equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "an administrator cannot edit their own eligibility metric");
        }
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String domain = IncentiveTypes.requireText(cmd.getDomainCode(), 32, "domainCode").toUpperCase();
        int reviewed = Math.max(0, cmd.getCurationReviewedCount() == null ? 0 : cmd.getCurationReviewedCount());
        int correct = Math.max(0, cmd.getCurationCorrectCount() == null ? 0 : cmd.getCurationCorrectCount());
        if (correct > reviewed) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String reason = IncentiveTypes.requireReason(cmd.getReason());
        mapper.upsertRoleMetric(userId, domain, correct, reviewed, operatorUid, reason);
        adminAuditService.recordRequired(operatorUid, "COMMUNITY_ROLE_METRIC_UPDATE",
                "COMMUNITY_ROLE_METRIC", userId + ":" + domain, null,
                Map.of("correct", correct, "reviewed", reviewed, "domainCode", domain), reason);
    }

    @Transactional
    public RoleDefinitionDTO upsertDefinition(RoleDefinitionCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String reason = IncentiveTypes.requireReason(cmd.getReason());
        String roleCode = IncentiveTypes.requireText(cmd.getRoleCode(), 64, "roleCode").toUpperCase();
        if (PROHIBITED_ROLE_FRAGMENTS.stream().anyMatch(roleCode::contains)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "community roles cannot represent platform authority or professional certification");
        }
        String domain = IncentiveTypes.requireText(cmd.getDomainCode(), 32, "domainCode").toUpperCase();
        RoleDefinitionPO po = new RoleDefinitionPO();
        po.setId(idGenerator.nextId());
        po.setRoleCode(roleCode);
        po.setRoleName(IncentiveTypes.requireText(cmd.getRoleName(), 128, "roleName"));
        po.setDescription(IncentiveTypes.clean(cmd.getDescription(), 1000));
        validateRoleText(po.getRoleName(), po.getDescription());
        po.setDomainCode(domain);
        po.setMinAccountAgeDays(nonNegativeInt(cmd.getMinAccountAgeDays()));
        po.setMinDomainReputation(nonNegative(cmd.getMinDomainReputation()));
        po.setMinActivityCount(nonNegativeInt(cmd.getMinActivityCount()));
        po.setMaxViolationCount(nonNegativeInt(cmd.getMaxViolationCount()));
        po.setMinCurationAccuracyBps(Math.max(0, Math.min(10000,
                cmd.getMinCurationAccuracyBps() == null ? 0 : cmd.getMinCurationAccuracyBps())));
        po.setRequiresNoRiskFreeze(Boolean.FALSE.equals(cmd.getRequiresNoRiskFreeze()) ? 0 : 1);
        po.setEnabled(Boolean.TRUE.equals(cmd.getEnabled()) ? 1 : 0);
        po.setCreatedBy(operatorUid);
        po.setUpdatedBy(operatorUid);
        po.setActionReason(reason);
        mapper.upsertRoleDefinition(po);
        RoleDefinitionPO saved = mapper.selectRoleDefinition(roleCode, domain);
        adminAuditService.recordRequired(operatorUid, "COMMUNITY_ROLE_DEFINITION_UPSERT",
                "COMMUNITY_ROLE_DEFINITION", roleCode + ":" + domain, null, saved, reason);
        return toDefinitionDto(saved);
    }

    @Transactional
    public RoleApplicationDTO submitApplication(RoleApplicationCmd cmd, Long userId) {
        requireUser(userId);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        RoleDefinitionPO definition = requireDefinition(cmd.getRoleCode(), cmd.getDomainCode());
        RoleEligibilityDTO eligibility = evaluate(userId, definition);
        if (!Boolean.TRUE.equals(eligibility.getEligible())) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "current account does not meet the community role eligibility gate");
        }
        String snapshot = toJson(eligibility);
        RoleApplicationPO po = new RoleApplicationPO();
        po.setId(idGenerator.nextId());
        po.setApplicantUid(userId);
        po.setRoleCode(definition.getRoleCode());
        po.setDomainCode(definition.getDomainCode());
        po.setStatement(IncentiveTypes.requireText(cmd.getStatement(), 1000, "statement"));
        po.setEligibilitySnapshotJson(snapshot);
        po.setApplicationStatus("SUBMITTED");
        try {
            mapper.insertRoleApplication(po);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "an active application already exists for this role and domain");
        }
        return toApplicationDto(po);
    }

    @Transactional
    public RoleApplicationDTO reviewApplication(Long applicationId, ReviewCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        if (cmd == null || cmd.getApproved() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String reason = IncentiveTypes.requireReason(cmd.getReason());
        RoleApplicationPO application = mapper.lockRoleApplication(applicationId);
        if (application == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"SUBMITTED".equals(application.getApplicationStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (operatorUid.equals(application.getApplicantUid())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "the applicant cannot review their own role application");
        }
        RoleDefinitionPO definition = mapper.selectRoleDefinition(
                application.getRoleCode(), application.getDomainCode());
        if (definition == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(cmd.getApproved())) {
            if (definition.getEnabled() == null || definition.getEnabled() != 1) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "role definition is disabled");
            }
            if (!evaluate(application.getApplicantUid(), definition).getEligible()) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "current eligibility no longer meets the manual role gate");
            }
            if (cmd.getExpiresAt() != null && !cmd.getExpiresAt().isAfter(LocalDateTime.now())) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "expiresAt must be in the future");
            }
        }
        String status = Boolean.TRUE.equals(cmd.getApproved()) ? "APPROVED" : "REJECTED";
        if (mapper.reviewRoleApplication(applicationId, status, operatorUid, reason) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if ("APPROVED".equals(status)) {
            RoleGrantPO grant = new RoleGrantPO();
            grant.setId(idGenerator.nextId());
            grant.setUserId(application.getApplicantUid());
            grant.setRoleCode(application.getRoleCode());
            grant.setDomainCode(application.getDomainCode());
            grant.setGrantStatus("ACTIVE");
            grant.setApplicationId(applicationId);
            grant.setGrantedBy(operatorUid);
            grant.setGrantReason(reason);
            grant.setGrantedAt(LocalDateTime.now());
            grant.setExpiresAt(cmd.getExpiresAt());
            try {
                mapper.insertRoleGrant(grant);
            } catch (DuplicateKeyException e) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "an active or suspended grant already exists for this role and domain");
            }
            mapper.insertRoleGrantHistory(idGenerator.nextId(), grant.getId(), null,
                    "ACTIVE", operatorUid, reason);
        }
        RoleApplicationPO after = mapper.lockRoleApplication(applicationId);
        adminAuditService.recordRequired(operatorUid, "COMMUNITY_ROLE_APPLICATION_REVIEW",
                "COMMUNITY_ROLE_APPLICATION", applicationId, application, after, reason);
        return toApplicationDto(after);
    }

    @Transactional
    public RoleGrantDTO suspendGrant(Long grantId, RoleActionCmd cmd, Long operatorUid) {
        return transitionGrantExplicit(grantId, cmd, operatorUid, "SUSPENDED");
    }

    @Transactional
    public RoleGrantDTO revokeGrant(Long grantId, RoleActionCmd cmd, Long operatorUid) {
        return transitionGrantExplicit(grantId, cmd, operatorUid, "REVOKED");
    }

    @Transactional
    public RoleGrantDTO expireGrant(Long grantId, RoleActionCmd cmd, Long operatorUid) {
        return transitionGrantExplicit(grantId, cmd, operatorUid, "EXPIRED");
    }

    @Transactional
    public int expireDue(Long operatorUid, Integer limit, String rawReason) {
        requireAdmin(operatorUid);
        String reason = IncentiveTypes.requireReason(rawReason);
        int count = 0;
        for (Long id : mapper.selectExpiredGrantIds(IncentiveTypes.safeLimit(limit))) {
            try {
                transitionGrantExplicit(id, new RoleActionCmd(), operatorUid, "EXPIRED", reason);
                count++;
            } catch (BizException ignored) {
                // Another operator may have transitioned this grant; the next bounded sweep will skip it.
            }
        }
        return count;
    }

    public int countExpireDue(Long operatorUid, Integer limit) {
        requireAdmin(operatorUid);
        return mapper.selectExpiredGrantIds(IncentiveTypes.safeLimit(limit)).size();
    }

    private RoleGrantDTO transitionGrantExplicit(Long grantId, RoleActionCmd cmd, Long operatorUid, String target) {
        return transitionGrantExplicit(grantId, cmd, operatorUid, target,
                IncentiveTypes.requireReason(cmd == null ? null : cmd.getReason()));
    }

    private RoleGrantDTO transitionGrantExplicit(Long grantId, RoleActionCmd cmd, Long operatorUid,
                                                 String target, String reason) {
        requireAdmin(operatorUid);
        RoleGrantPO grant = mapper.lockRoleGrant(grantId);
        if (grant == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        String current = grant.getGrantStatus();
        if (!Set.of("ACTIVE", "SUSPENDED").contains(current) || current.equals(target)) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (mapper.transitionRoleGrant(grantId, current, target, operatorUid, reason, null) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        mapper.insertRoleGrantHistory(idGenerator.nextId(), grantId, current, target, operatorUid, reason);
        RoleGrantPO after = mapper.lockRoleGrant(grantId);
        adminAuditService.recordRequired(operatorUid, "COMMUNITY_ROLE_GRANT_" + target,
                "COMMUNITY_ROLE_GRANT", grantId, grant, after, reason);
        return toGrantDto(after);
    }

    private RoleEligibilityDTO evaluate(Long userId, RoleDefinitionPO definition) {
        Map<String, Object> metrics = mapper.selectRoleMetrics(userId, definition.getDomainCode());
        if (metrics == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        int accountAge = number(metrics.get("accountAgeDays"));
        long reputation = longNumber(metrics.get("domainReputation"));
        int activity = number(metrics.get("activityCount"));
        int violations = number(metrics.get("violationCount"));
        int curation = number(metrics.get("curationAccuracyBps"));
        boolean riskFrozen = number(metrics.get("riskFrozen")) > 0;
        List<String> failed = new ArrayList<>();
        if (accountAge < definition.getMinAccountAgeDays()) failed.add("ACCOUNT_AGE");
        if (reputation < definition.getMinDomainReputation()) failed.add("DOMAIN_REPUTATION");
        if (activity < definition.getMinActivityCount()) failed.add("ACTIVITY");
        if (violations > definition.getMaxViolationCount()) failed.add("VIOLATIONS");
        if (curation < definition.getMinCurationAccuracyBps()) failed.add("CURATION_ACCURACY");
        if (definition.getRequiresNoRiskFreeze() == 1 && riskFrozen) failed.add("RISK_FREEZE");
        return RoleEligibilityDTO.builder()
                .roleCode(definition.getRoleCode()).domainCode(definition.getDomainCode())
                .eligible(failed.isEmpty()).accountAgeDays(accountAge).domainReputation(reputation)
                .activityCount(activity).violationCount(violations).curationAccuracyBps(curation)
                .riskFrozen(riskFrozen).failedChecks(failed).manualApprovalRequired(true)
                .automaticallyGrantsAuthority(false).build();
    }

    private RoleEvidenceDTO toEvidenceDto(Long userId,
                                          RoleDefinitionPO definition,
                                          RoleEligibilityDTO eligibility,
                                          RoleApplicationPO application,
                                          RoleGrantPO grant) {
        boolean canApply = Boolean.TRUE.equals(eligibility.getEligible())
                && (application == null || !"SUBMITTED".equals(application.getApplicationStatus()))
                && (grant == null || !Set.of("ACTIVE", "SUSPENDED").contains(grant.getGrantStatus()));
        boolean canUseMaintenanceWorkspace = "CHANNEL_RESOURCE_MAINTAINER".equals(definition.getRoleCode())
                && communityRoleAccessService.hasActiveGrant(
                userId, definition.getRoleCode(), definition.getDomainCode());
        List<String> actions = new ArrayList<>();
        if (canApply) actions.add("APPLY");
        if (application != null) actions.add("VIEW_APPLICATION");
        if (grant != null) actions.add("VIEW_GRANT");
        if (canUseMaintenanceWorkspace) actions.add("OPEN_MAINTENANCE_CANDIDATES");
        return RoleEvidenceDTO.builder()
                .roleCode(definition.getRoleCode())
                .roleName(definition.getRoleName())
                .domainCode(definition.getDomainCode())
                .eligible(eligibility.getEligible())
                .failedChecks(eligibility.getFailedChecks())
                .evidence(List.of(
                        evidence("ACCOUNT_AGE", "账号时长",
                                eligibility.getAccountAgeDays(), definition.getMinAccountAgeDays(),
                                eligibility.getAccountAgeDays() >= definition.getMinAccountAgeDays()),
                        evidence("DOMAIN_REPUTATION", "领域声望",
                                eligibility.getDomainReputation(), definition.getMinDomainReputation(),
                                eligibility.getDomainReputation() >= definition.getMinDomainReputation()),
                        evidence("ACTIVITY", "近期可信贡献",
                                eligibility.getActivityCount(), definition.getMinActivityCount(),
                                eligibility.getActivityCount() >= definition.getMinActivityCount()),
                        evidence("VIOLATIONS", "有效违规数量",
                                eligibility.getViolationCount(), definition.getMaxViolationCount(),
                                eligibility.getViolationCount() <= definition.getMaxViolationCount()),
                        evidence("CURATION_ACCURACY", "策展准确率基点",
                                eligibility.getCurationAccuracyBps(), definition.getMinCurationAccuracyBps(),
                                eligibility.getCurationAccuracyBps() >= definition.getMinCurationAccuracyBps()),
                        evidence("RISK_FREEZE", "风险冻结",
                                Boolean.TRUE.equals(eligibility.getRiskFrozen()) ? 1 : 0,
                                definition.getRequiresNoRiskFreeze() == 1 ? 0 : 1,
                                definition.getRequiresNoRiskFreeze() != 1
                                        || !Boolean.TRUE.equals(eligibility.getRiskFrozen()))
                ))
                .manualApprovalRequired(true)
                .riskFrozen(eligibility.getRiskFrozen())
                .applicationStatus(application == null ? null : application.getApplicationStatus())
                .grantStatus(grant == null ? null : effectiveGrantStatus(grant))
                .expiresAt(grant == null ? null : grant.getExpiresAt())
                .canApply(canApply)
                .canUseMaintenanceWorkspace(canUseMaintenanceWorkspace)
                .availableActions(actions)
                .build();
    }

    private static RoleEvidenceItemDTO evidence(String code, String label,
                                                Object current, Object required, boolean passed) {
        return RoleEvidenceItemDTO.builder()
                .evidenceCode(code)
                .label(label)
                .currentValue(String.valueOf(current))
                .requiredValue(String.valueOf(required))
                .passed(passed)
                .build();
    }

    private static String effectiveGrantStatus(RoleGrantPO grant) {
        if ("ACTIVE".equals(grant.getGrantStatus())
                && grant.getExpiresAt() != null
                && !grant.getExpiresAt().isAfter(LocalDateTime.now())) {
            return "EXPIRED_PENDING_RECONCILIATION";
        }
        return grant.getGrantStatus();
    }

    private static RoleGrantHistoryDTO toRoleGrantHistory(Map<String, Object> row) {
        return RoleGrantHistoryDTO.builder()
                .id(nullableLong(row.get("id")))
                .grantId(nullableLong(row.get("grantId")))
                .fromStatus(text(row.get("fromStatus")))
                .toStatus(text(row.get("toStatus")))
                .operatorUid(nullableLong(row.get("operatorUid")))
                .actionReason(text(row.get("actionReason")))
                .createTime(row.get("createTime") instanceof LocalDateTime value ? value : null)
                .build();
    }

    private RoleDefinitionPO requireDefinition(String roleCode, String domainCode) {
        String code = IncentiveTypes.requireText(roleCode, 64, "roleCode").toUpperCase();
        String domain = IncentiveTypes.requireText(domainCode, 32, "domainCode").toUpperCase();
        RoleDefinitionPO po = mapper.selectRoleDefinition(code, domain);
        if (po == null || po.getEnabled() == null || po.getEnabled() != 1) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return po;
    }

    private RoleDefinitionDTO toDefinitionDto(RoleDefinitionPO po) {
        return RoleDefinitionDTO.builder()
                .id(po.getId()).roleCode(po.getRoleCode()).roleName(po.getRoleName())
                .description(po.getDescription()).domainCode(po.getDomainCode())
                .minAccountAgeDays(po.getMinAccountAgeDays()).minDomainReputation(po.getMinDomainReputation())
                .minActivityCount(po.getMinActivityCount()).maxViolationCount(po.getMaxViolationCount())
                .minCurationAccuracyBps(po.getMinCurationAccuracyBps())
                .requiresNoRiskFreeze(po.getRequiresNoRiskFreeze() != null && po.getRequiresNoRiskFreeze() == 1)
                .enabled(po.getEnabled() != null && po.getEnabled() == 1).updateTime(po.getUpdateTime())
                .build();
    }

    private RoleApplicationDTO toApplicationDto(RoleApplicationPO po) {
        return RoleApplicationDTO.builder()
                .id(po.getId()).applicantUid(po.getApplicantUid()).roleCode(po.getRoleCode())
                .domainCode(po.getDomainCode()).statement(po.getStatement())
                .eligibilitySnapshotJson(po.getEligibilitySnapshotJson()).status(po.getApplicationStatus())
                .reviewerUid(po.getReviewerUid()).reviewReason(po.getReviewReason())
                .createTime(po.getCreateTime()).updateTime(po.getUpdateTime()).build();
    }

    private RoleGrantDTO toGrantDto(RoleGrantPO po) {
        return RoleGrantDTO.builder()
                .id(po.getId()).userId(po.getUserId()).roleCode(po.getRoleCode()).domainCode(po.getDomainCode())
                .status(po.getGrantStatus()).grantedBy(po.getGrantedBy()).grantReason(po.getGrantReason())
                .actionBy(po.getActionBy()).actionReason(po.getActionReason()).grantedAt(po.getGrantedAt())
                .expiresAt(po.getExpiresAt()).updateTime(po.getUpdateTime()).build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "eligibility snapshot serialization failed");
        }
    }

    private static void validateRoleText(String... values) {
        for (String value : values) {
            String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
            if (PROHIBITED_ROLE_TERMS.stream().anyMatch(normalized::contains)) {
                throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                        "community role text cannot represent platform authority or professional certification");
            }
        }
    }

    private void requireAdmin(Long uid) {
        adminPermissionService.requireAdmin(uid);
    }

    private static void requireUser(Long uid) {
        if (uid == null || uid <= 0) throw new BizException(ErrorCode.UNAUTHORIZED);
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static long longNumber(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static Long nullableLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long nonNegative(Long value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static int nonNegativeInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static String requireApplicationStatus(String status) {
        String normalized = IncentiveTypes.upper(status);
        if (!APPLICATION_STATUSES.contains(normalized)) throw new BizException(ErrorCode.PARAM_ERROR);
        return normalized;
    }

    private static String requireGrantStatus(String status) {
        String normalized = IncentiveTypes.upper(status);
        if (!Set.of("ACTIVE", "SUSPENDED", "REVOKED", "EXPIRED").contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static int safePage(Integer page) {
        return page == null || page <= 0 ? 1 : Math.min(page, 1000);
    }

    private static Long requirePositive(Long value) {
        if (value == null || value <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static String scopeKey(String roleCode, String domainCode) {
        return roleCode + ":" + domainCode;
    }

    private static int domainNumber(String domainCode) {
        return switch (domainCode == null ? "" : domainCode.toUpperCase(Locale.ROOT)) {
            case "TECH" -> 1;
            case "CAREER" -> 2;
            case "READING" -> 3;
            case "LIFESTYLE" -> 4;
            case "INVESTMENT" -> 5;
            default -> throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "unsupported community role domain");
        };
    }

    private static <T> PageResult<T> page(List<T> items, long total, int page, int size) {
        boolean hasMore = (long) page * size < total;
        return PageResult.<T>builder().items(items).total(total).hasMore(hasMore)
                .nextCursor(hasMore ? String.valueOf(page + 1) : null).build();
    }
}

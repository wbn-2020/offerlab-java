package com.offerlab.community.post.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.dto.ExpertCertificationApplicationDTO;
import com.offerlab.community.post.api.dto.ExpertCertificationApplicantApplicationDTO;
import com.offerlab.community.post.api.dto.ExpertCertificationApplyCmd;
import com.offerlab.community.post.api.dto.ExpertCertificationEligibilityDTO;
import com.offerlab.community.post.api.dto.ExpertCertificationReviewCmd;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.model.PostDomain;
import com.offerlab.community.post.infrastructure.persistence.mapper.ExpertCertificationMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ExpertCertificationApplicationPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpertCertificationService {

    public static final int STATUS_SUBMITTED = 10;
    public static final int STATUS_APPROVED = 20;
    public static final int STATUS_REJECTED = 30;
    public static final int STATUS_REVOKED = 40;

    private static final String MIGRATION_HINT = "db/migration/20260624_expert_certification.sql";
    private static final int REQUIRED_PUBLIC_POSTS = 3;
    private static final int RECENT_ACTIVITY_DAYS = 90;
    private static final int MAX_LIMIT = 50;
    private static final int SUBMIT_LOCK_TIMEOUT_SECONDS = 3;

    private final ExpertCertificationMapper mapper;
    private final PostMapper postMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final DomainModeratorService domainModeratorService;
    private final AdminAuditService adminAuditService;
    private final MigrationCheckService migrationCheckService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExpertCertificationEligibilityDTO getEligibility(Long applicantUid, Integer domain) {
        requireUser(applicantUid);
        Integer activeDomain = requireDomain(domain);
        List<PostPO> posts = recentPublicPosts(applicantUid, activeDomain);
        boolean enoughPosts = posts.size() >= REQUIRED_PUBLIC_POSTS;
        LocalDateTime recentCutoff = LocalDateTime.now().minusDays(RECENT_ACTIVITY_DAYS);
        boolean recentActivity = posts.stream()
                .map(PostPO::getCreateTime)
                .anyMatch(time -> time != null && !time.isBefore(recentCutoff));

        List<ExpertCertificationEligibilityDTO.CheckItemDTO> checks = List.of(
                ExpertCertificationEligibilityDTO.CheckItemDTO.builder()
                        .code("published_posts")
                        .label("Public domain posts")
                        .passed(enoughPosts)
                        .detail(posts.size() + "/" + REQUIRED_PUBLIC_POSTS)
                        .build(),
                ExpertCertificationEligibilityDTO.CheckItemDTO.builder()
                        .code("recent_activity")
                        .label("Recent activity")
                        .passed(recentActivity)
                        .detail(recentActivity ? "active within 90 days" : "needs one recent public post")
                        .build()
        );

        boolean eligible = enoughPosts && recentActivity;
        return ExpertCertificationEligibilityDTO.builder()
                .domain(activeDomain)
                .domainName(PostDomain.fromCode(activeDomain).getDisplayName())
                .eligible(eligible)
                .riskAcknowledgementRequired(activeDomain == Post.DOMAIN_INVESTMENT)
                .manualReviewOnly(true)
                .riskWarning(riskWarning(activeDomain))
                .explanation(eligible
                        ? "Pilot application can be submitted for manual review."
                        : "More public domain contributions are needed before manual review.")
                .checks(checks)
                .build();
    }

    public List<ExpertCertificationApplicantApplicationDTO> listMine(Long applicantUid, Integer domain) {
        requireUser(applicantUid);
        if (!tableReady()) {
            return List.of();
        }
        Integer activeDomain = domain == null ? null : requireDomain(domain);
        return mapper.selectMine(applicantUid, activeDomain, MAX_LIMIT).stream()
                .map(this::toApplicantDto)
                .toList();
    }

    public List<ExpertCertificationApplicationDTO> listReviewQueue(Integer domain, Integer status, int limit, Long reviewerUid) {
        requireUser(reviewerUid);
        requireWritable();
        Integer activeDomain = requireDomain(domain);
        domainModeratorService.requireModerateDomain(reviewerUid, activeDomain);
        Integer activeStatus = status == null ? null : requireStatus(status);
        return mapper.selectReviewQueue(activeDomain, activeStatus, safeLimit(limit)).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ExpertCertificationApplicantApplicationDTO submit(ExpertCertificationApplyCmd cmd, Long applicantUid) {
        requireUser(applicantUid);
        requireWritable();
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Integer activeDomain = requireDomain(cmd.getDomain());
        ExpertCertificationEligibilityDTO eligibility = getEligibility(applicantUid, activeDomain);
        if (!Boolean.TRUE.equals(eligibility.getEligible())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "Current account does not meet the pilot application gate yet.");
        }
        if (Boolean.TRUE.equals(eligibility.getRiskAcknowledgementRequired())
                && !Boolean.TRUE.equals(cmd.getRiskAcknowledged())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "Investment pilot applications require explicit risk acknowledgement.");
        }

        String lockName = submitLockName(applicantUid, activeDomain);
        acquireSubmitLock(lockName);
        try {
            if (mapper.selectActiveByApplicantAndDomain(applicantUid, activeDomain) != null) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                        "An active pilot application already exists for this domain.");
            }

            LocalDateTime now = LocalDateTime.now();
            ExpertCertificationApplicationPO po = new ExpertCertificationApplicationPO();
            po.setId(idGenerator.nextId());
            po.setApplicantUid(applicantUid);
            po.setDomain(activeDomain);
            po.setStatus(STATUS_SUBMITTED);
            po.setEvidenceSummary(requireText(cmd.getEvidenceSummary(), 500));
            po.setEvidenceLinksJson(toJson(cleanLinks(cmd.getEvidenceLinks())));
            po.setEligibilityPassed(Boolean.TRUE.equals(eligibility.getEligible()) ? 1 : 0);
            po.setEligibilitySummary(limit(eligibility.getExplanation(), 500));
            po.setEligibilitySnapshotJson(toJson(eligibility));
            po.setRiskAcknowledged(Boolean.TRUE.equals(cmd.getRiskAcknowledged()) ? 1 : 0);
            po.setRiskWarning(limit(eligibility.getRiskWarning(), 500));
            po.setCreateTime(now);
            po.setUpdateTime(now);
            po.setIsDeleted(0);
            mapper.insert(po);
            return toApplicantDto(po);
        } finally {
            releaseSubmitLock(lockName);
        }
    }

    @Transactional
    public ExpertCertificationApplicationDTO review(Long applicationId, ExpertCertificationReviewCmd cmd, Long reviewerUid) {
        requireUser(reviewerUid);
        requireWritable();
        if (cmd == null || cmd.getApproved() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ExpertCertificationApplicationPO po = requireApplication(applicationId);
        domainModeratorService.requireModerateDomain(reviewerUid, po.getDomain());
        if (!STATUS_SUBMITTEDEquals(po.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ExpertCertificationApplicationDTO before = toDto(po);
        po.setStatus(Boolean.TRUE.equals(cmd.getApproved()) ? STATUS_APPROVED : STATUS_REJECTED);
        po.setReviewerUid(reviewerUid);
        po.setReviewNote(limit(cmd.getNote(), 500));
        po.setReviewTime(LocalDateTime.now());
        po.setUpdateTime(LocalDateTime.now());
        mapper.updateById(po);
        ExpertCertificationApplicationDTO after = toDto(po);
        adminAuditService.recordRequired(reviewerUid, "EXPERT_CERT_APPLICATION_REVIEW",
                "EXPERT_CERT_APPLICATION", applicationId, before, after, limit(cmd.getNote(), 500));
        return after;
    }

    @Transactional
    public ExpertCertificationApplicantApplicationDTO revoke(Long applicationId, String note, Long operatorUid) {
        requireUser(operatorUid);
        requireWritable();
        ExpertCertificationApplicationPO po = requireApplication(applicationId);
        boolean owner = operatorUid.equals(po.getApplicantUid());
        boolean moderator = !owner && domainModeratorService.canModerateDomain(operatorUid, po.getDomain());
        if (!owner && !moderator) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (STATUS_REVOKEDEquals(po.getStatus())) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        if (!moderator && !(STATUS_SUBMITTEDEquals(po.getStatus()) || STATUS_APPROVEDEquals(po.getStatus()))) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ExpertCertificationApplicationDTO before = toDto(po);
        po.setStatus(STATUS_REVOKED);
        po.setRevokedBy(operatorUid);
        po.setRevokeNote(limit(note, 500));
        po.setRevokedTime(LocalDateTime.now());
        po.setUpdateTime(LocalDateTime.now());
        mapper.updateById(po);
        ExpertCertificationApplicationDTO after = toDto(po);
        if (moderator) {
            adminAuditService.recordRequired(operatorUid, "EXPERT_CERT_APPLICATION_REVOKE",
                    "EXPERT_CERT_APPLICATION", applicationId, before, after, limit(note, 500));
        }
        return toApplicantDto(po);
    }

    private List<PostPO> recentPublicPosts(Long applicantUid, Integer domain) {
        return postMapper.selectPublicPosts(applicantUid, null, null, null, domain, null, Long.MAX_VALUE, REQUIRED_PUBLIC_POSTS + 5);
    }

    private ExpertCertificationApplicationPO requireApplication(Long applicationId) {
        if (applicationId == null || applicationId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ExpertCertificationApplicationPO po = mapper.selectById(applicationId);
        if (po == null || Integer.valueOf(1).equals(po.getIsDeleted())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return po;
    }

    private ExpertCertificationApplicationDTO toDto(ExpertCertificationApplicationPO po) {
        return ExpertCertificationApplicationDTO.builder()
                .id(po.getId())
                .applicantUid(po.getApplicantUid())
                .domain(po.getDomain())
                .domainName(PostDomain.fromCode(po.getDomain()).getDisplayName())
                .status(po.getStatus())
                .statusLabel(statusLabel(po.getStatus()))
                .evidenceSummary(po.getEvidenceSummary())
                .evidenceLinks(readLinks(po.getEvidenceLinksJson()))
                .eligibilityPassed(po.getEligibilityPassed() != null && po.getEligibilityPassed() == 1)
                .eligibilitySummary(po.getEligibilitySummary())
                .riskAcknowledged(po.getRiskAcknowledged() != null && po.getRiskAcknowledged() == 1)
                .riskWarning(po.getRiskWarning())
                .reviewerUid(po.getReviewerUid())
                .reviewNote(po.getReviewNote())
                .revokedBy(po.getRevokedBy())
                .revokeNote(po.getRevokeNote())
                .autoCertified(false)
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .reviewTime(po.getReviewTime())
                .revokedTime(po.getRevokedTime())
                .build();
    }

    private ExpertCertificationApplicantApplicationDTO toApplicantDto(ExpertCertificationApplicationPO po) {
        return ExpertCertificationApplicantApplicationDTO.builder()
                .id(po.getId())
                .applicantUid(po.getApplicantUid())
                .domain(po.getDomain())
                .domainName(PostDomain.fromCode(po.getDomain()).getDisplayName())
                .status(po.getStatus())
                .statusLabel(statusLabel(po.getStatus()))
                .evidenceSummary(po.getEvidenceSummary())
                .evidenceLinks(readLinks(po.getEvidenceLinksJson()))
                .eligibilityPassed(po.getEligibilityPassed() != null && po.getEligibilityPassed() == 1)
                .eligibilitySummary(po.getEligibilitySummary())
                .riskAcknowledged(po.getRiskAcknowledged() != null && po.getRiskAcknowledged() == 1)
                .riskWarning(po.getRiskWarning())
                .autoCertified(false)
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .reviewTime(po.getReviewTime())
                .revokedTime(po.getRevokedTime())
                .build();
    }

    private boolean tableReady() {
        try {
            return migrationCheckService.expertCertificationReady();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void requireWritable() {
        if (!tableReady()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "Expert certification migration is required: " + MIGRATION_HINT);
        }
    }

    private static void requireUser(Long uid) {
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static Integer requireDomain(Integer domain) {
        if (!PostDomain.isValid(domain)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return domain;
    }

    private static Integer requireStatus(Integer status) {
        if (STATUS_SUBMITTED == status || STATUS_APPROVED == status
                || STATUS_REJECTED == status || STATUS_REVOKED == status) {
            return status;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 20 : limit, MAX_LIMIT));
    }

    private static String requireText(String value, int maxLength) {
        String text = limit(value, maxLength);
        if (!StringUtils.hasText(text)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return text;
    }

    private static List<String> cleanLinks(List<String> links) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        return links.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(8)
                .map(link -> link.length() <= 512 ? link : link.substring(0, 512))
                .toList();
    }

    private String toJson(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "Expert certification payload serialization failed.");
        }
    }

    private List<String> readLinks(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String statusLabel(Integer status) {
        if (status == null) {
            return "UNKNOWN";
        }
        return switch (status) {
            case STATUS_SUBMITTED -> "SUBMITTED";
            case STATUS_APPROVED -> "APPROVED";
            case STATUS_REJECTED -> "REJECTED";
            case STATUS_REVOKED -> "REVOKED";
            default -> "UNKNOWN";
        };
    }

    private static String riskWarning(Integer domain) {
        if (domain != Post.DOMAIN_INVESTMENT) {
            return null;
        }
        return "Community content is not investment advice. Approval stays manual and does not create an automatic certification.";
    }

    private static String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private void acquireSubmitLock(String lockName) {
        try {
            Integer locked = mapper.acquireNamedLock(lockName, SUBMIT_LOCK_TIMEOUT_SECONDS);
            if (!Integer.valueOf(1).equals(locked)) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                        "Expert certification submit guard is busy; please retry.");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "Expert certification submit guard is unavailable.");
        }
    }

    private void releaseSubmitLock(String lockName) {
        try {
            mapper.releaseNamedLock(lockName);
        } catch (RuntimeException ignored) {
        }
    }

    private static String submitLockName(Long applicantUid, Integer domain) {
        return "offerlab:expert-cert:" + applicantUid + ":" + domain;
    }

    private static boolean STATUS_SUBMITTEDEquals(Integer status) {
        return Integer.valueOf(STATUS_SUBMITTED).equals(status);
    }

    private static boolean STATUS_APPROVEDEquals(Integer status) {
        return Integer.valueOf(STATUS_APPROVED).equals(status);
    }

    private static boolean STATUS_REVOKEDEquals(Integer status) {
        return Integer.valueOf(STATUS_REVOKED).equals(status);
    }
}

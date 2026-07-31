package com.offerlab.community.incentive.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.incentive.api.IncentiveDtos.BountyCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.BountyAppealCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.BountyAppealDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BountyDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BountyStatusCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.BountySubmissionCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.BountySubmissionDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BountyWorkspaceDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.ReviewCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.ReversalCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.LedgerEntryDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.ThankCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.ThankTicketDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.ThankWorkspaceDTO;
import com.offerlab.community.incentive.domain.IncentiveTypes;
import com.offerlab.community.incentive.infrastructure.IncentiveMapper;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BountyPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BountyAppealPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.BountySubmissionPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.LedgerPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.ThankTicketPO;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.security.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ThankBountyService {
    private static final Set<String> THANK_TARGET_TYPES = Set.of("POST", "COMMENT");
    private static final Set<String> BOUNTY_STATUSES = Set.of("DRAFT", "OPEN", "CLOSED", "CANCELLED");
    private static final Set<String> BOUNTY_REQUEST_TYPES =
            Set.of("PUBLIC_CONTRIBUTION", "CURATION", "COLLABORATION");
    private static final Set<String> BOUNTY_RISK_CATEGORIES = Set.of("LOW");
    private static final Set<String> PROHIBITED_BOUNTY_CLASSIFICATIONS =
            Set.of("INVESTMENT", "MEDICAL", "LEGAL", "PRIVACY", "DOXXING", "GHOSTWRITING");
    private static final Set<String> PROHIBITED_INCENTIVE_TERMS = Set.of(
            "充值", "提现", "转账", "现金", "人民币", "赌博", "抽奖", "认证", "曝光", "审核权", "收益保证",
            "recharge", "withdraw", "transfer", "cash", "lottery", "gambling", "certification",
            "exposure", "moderation privilege", "guaranteed return");

    private final IncentiveMapper mapper;
    private final AccountLedgerService accountLedgerService;
    private final SnowflakeIdGenerator idGenerator;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;
    private final ContentModerationService contentModerationService;

    public ThankWorkspaceDTO thanks(Long userId, Integer page, Integer size) {
        requireUser(userId);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        ThankTicketDTO ticket = ensureTicket(userId);
        List<Map<String, Object>> items = mapper.selectSentThanks(userId, (safePage - 1) * safeSize, safeSize);
        List<Map<String, Object>> received = mapper.selectReceivedThanks(
                userId, (safePage - 1) * safeSize, safeSize);
        return ThankWorkspaceDTO.builder()
                .ticket(ticket)
                .sent(page(items, mapper.countSentThanks(userId), safePage, safeSize))
                .received(page(received, mapper.countReceivedThanks(userId), safePage, safeSize))
                .receivedTotal(mapper.countReceivedThanks(userId))
                .receivedByTargetType(groupStats(mapper.selectReceivedThankTypeStats(userId)))
                .receivedByDomain(groupStats(mapper.selectReceivedThankDomainStats(userId)))
                .build();
    }

    @Transactional
    public ThankTicketDTO sendThank(ThankCmd cmd, Long senderUid) {
        requireUser(senderUid);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        requireUser(cmd.getReceiverUid());
        if (senderUid.equals(cmd.getReceiverUid())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "cannot thank yourself");
        }
        String targetType = IncentiveTypes.upper(cmd.getTargetType());
        if (!THANK_TARGET_TYPES.contains(targetType)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "unsupported thank target type");
        }
        String targetId = IncentiveTypes.requireText(cmd.getTargetId(), 64, "targetId");
        long numericTargetId;
        try {
            numericTargetId = Long.parseLong(targetId);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "targetId must be numeric");
        }
        Long verifiedAuthor = mapper.selectPublicThankTargetAuthor(targetType, numericTargetId);
        if (verifiedAuthor == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                    "thank target is not public, active and attributable");
        }
        if (!verifiedAuthor.equals(cmd.getReceiverUid())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "receiverUid does not match target author");
        }
        LocalDate today = LocalDate.now();
        ensureTicket(senderUid);
        if (mapper.consumeThankTicket(senderUid, today) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "no free thank-you ticket remains today");
        }
        try {
            mapper.insertThankAction(idGenerator.nextId(), senderUid, cmd.getReceiverUid(), targetType, targetId,
                    today, IncentiveTypes.clean(cmd.getNote(), 200));
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "this public contribution has already received your thank-you ticket");
        }
        return ticketDto(mapper.selectThankTicket(senderUid, today));
    }

    public BountyWorkspaceDTO bounties(Long userId, Integer page, Integer size) {
        requireUser(userId);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<BountyDTO> available = mapper.selectOpenBounties((safePage - 1) * safeSize, safeSize).stream()
                .map(this::toBountyDto).toList();
        List<BountySubmissionDTO> submissions = mapper.selectUserBountySubmissions(
                        userId, (safePage - 1) * safeSize, safeSize).stream()
                .map(this::toSubmissionDto).toList();
        return BountyWorkspaceDTO.builder()
                .available(page(available, mapper.countOpenBounties(), safePage, safeSize))
                .submissions(page(submissions, mapper.countUserBountySubmissions(userId), safePage, safeSize))
                .build();
    }

    @Transactional
    public BountyDTO createBounty(BountyCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        if (cmd == null || cmd.getQuota() == null || cmd.getQuota() <= 0
                || cmd.getQuota() > IncentiveTypes.MAX_BOUNTY_QUOTA) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        long pointReward = IncentiveTypes.requirePositive(cmd.getPointReward(), "pointReward");
        if (pointReward > IncentiveTypes.USER_BOUNTY_PERIOD_BUDGET) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "pointReward exceeds the per-user period bounty budget");
        }
        long totalBudget;
        try {
            totalBudget = Math.multiplyExact(pointReward, cmd.getQuota().longValue());
        } catch (ArithmeticException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "bounty total budget is too large");
        }
        if (totalBudget > IncentiveTypes.MAX_BOUNTY_TOTAL_BUDGET) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "bounty total budget exceeds the platform limit");
        }
        String reason = IncentiveTypes.requireReason(cmd.getReason());
        String domain = IncentiveTypes.requireText(cmd.getDomainCode(), 32, "domainCode")
                .toUpperCase(Locale.ROOT);
        requireBountyDomain(domain);
        BountyPO po = new BountyPO();
        po.setId(idGenerator.nextId());
        po.setTitle(IncentiveTypes.requireText(cmd.getTitle(), 160, "title"));
        po.setDescription(IncentiveTypes.requireText(cmd.getDescription(), 2000, "description"));
        po.setDomainCode(domain);
        po.setRequestType(requireBountyRequestType(cmd.getRequestType()));
        po.setRiskCategory(requireBountyRisk(cmd.getRiskCategory()));
        po.setQuota(cmd.getQuota());
        po.setAwardedCount(0);
        po.setPointReward(pointReward);
        po.setTotalBudget(totalBudget);
        po.setReservedBudget(0L);
        po.setConsumedBudget(0L);
        po.setBountyStatus("DRAFT");
        po.setCreatedBy(operatorUid);
        po.setUpdatedBy(operatorUid);
        po.setActionReason(reason);
        requireSafeIncentiveText(po.getTitle());
        requireSafeIncentiveText(po.getDescription());
        requireAllowedClassification(po.getDomainCode(), po.getRequestType(), po.getRiskCategory());
        requireModerationAllowed(operatorUid, "QUOTA_BOUNTY", po.getId(), po.getTitle(), po.getDescription());
        mapper.insertBounty(po);
        adminAuditService.recordRequired(operatorUid, "QUOTA_BOUNTY_CREATE",
                "QUOTA_BOUNTY", po.getId(), null, po, reason);
        return toBountyDto(mapper.selectBounty(po.getId()));
    }

    @Transactional
    public BountyDTO updateBountyStatus(Long bountyId, BountyStatusCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String reason = IncentiveTypes.requireReason(cmd == null ? null : cmd.getReason());
        String target = requireBountyStatus(cmd.getStatus());
        BountyPO bounty = mapper.lockBounty(bountyId);
        if (bounty == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        String current = bounty.getBountyStatus();
        boolean allowed = ("DRAFT".equals(current) && ("OPEN".equals(target) || "CANCELLED".equals(target)))
                || ("OPEN".equals(current) && ("CLOSED".equals(target) || "CANCELLED".equals(target)));
        if (!allowed) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if ("OPEN".equals(target)) {
            requireBountyDomain(bounty.getDomainCode());
            String period = YearMonth.now().toString();
            mapper.insertPlatformBountyBudgetIfAbsent(
                    period, IncentiveTypes.PLATFORM_BOUNTY_PERIOD_BUDGET);
            if (mapper.reservePlatformBountyBudget(period, bounty.getTotalBudget()) != 1) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "platform bounty period budget has been exhausted");
            }
            if (mapper.openBounty(bountyId, period, operatorUid, reason) != 1) {
                throw new BizException(ErrorCode.INVALID_STATUS);
            }
        } else {
            if ("OPEN".equals(current)) {
                long remaining = Math.max(0, bounty.getReservedBudget() - bounty.getConsumedBudget());
                if (remaining > 0
                        && mapper.releasePlatformBountyBudget(bounty.getBudgetPeriod(), remaining) != 1) {
                    throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                            "failed to release unused platform bounty budget");
                }
            }
            if (mapper.updateBountyStatus(bountyId, current, target, operatorUid, reason) != 1) {
                throw new BizException(ErrorCode.INVALID_STATUS);
            }
        }
        BountyPO after = mapper.selectBounty(bountyId);
        adminAuditService.recordRequired(operatorUid, "QUOTA_BOUNTY_STATUS_UPDATE",
                "QUOTA_BOUNTY", bountyId, bounty, after, reason);
        return toBountyDto(after);
    }

    @Transactional
    public BountySubmissionDTO submitBounty(Long bountyId, BountySubmissionCmd cmd, Long userId) {
        requireUser(userId);
        BountyPO bounty = requireBounty(bountyId);
        if (!"OPEN".equals(bounty.getBountyStatus()) || bounty.getAwardedCount() >= bounty.getQuota()) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        requireBountyDomain(bounty.getDomainCode());
        String requestType = requireBountyRequestType(cmd == null ? null : cmd.getRequestType());
        String riskCategory = requireBountyRisk(cmd == null ? null : cmd.getRiskCategory());
        if (!requestType.equals(bounty.getRequestType()) || !riskCategory.equals(bounty.getRiskCategory())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "submission classification does not match bounty");
        }
        Long postId = cmd.getPublicPostId();
        Long publicAuthor = postId == null ? null : mapper.selectPublicPostAuthor(postId);
        if (publicAuthor == null || !publicAuthor.equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "submission must reference the applicant's public non-anonymous post");
        }
        String postDomain = mapper.selectPostDomainCode(postId);
        if (!IncentiveTypes.upper(bounty.getDomainCode()).equals(IncentiveTypes.upper(postDomain))) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "submission evidence must belong to the bounty domain");
        }
        BountySubmissionPO po = new BountySubmissionPO();
        po.setId(idGenerator.nextId());
        po.setBountyId(bountyId);
        po.setApplicantUid(userId);
        po.setPublicPostId(postId);
        po.setRequestType(requestType);
        po.setRiskCategory(riskCategory);
        po.setEvidence(IncentiveTypes.requireText(cmd == null ? null : cmd.getEvidence(), 2000, "evidence"));
        requireSafeIncentiveText(po.getEvidence());
        requireAllowedClassification(bounty.getDomainCode(), requestType, riskCategory);
        requireModerationAllowed(userId, "QUOTA_BOUNTY_SUBMISSION", po.getId(), po.getEvidence());
        po.setSubmissionStatus("SUBMITTED");
        try {
            mapper.insertBountySubmission(po);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "only one submission per user is allowed for this bounty");
        }
        return toSubmissionDto(po);
    }

    public PageResult<BountySubmissionDTO> submissionQueue(String status, Integer page, Integer size,
                                                            Long operatorUid) {
        requireAdmin(operatorUid);
        String normalized = status == null ? null : requireSubmissionStatus(status);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<BountySubmissionDTO> items = mapper.selectBountySubmissionQueue(
                        normalized, (safePage - 1) * safeSize, safeSize).stream()
                .map(this::toSubmissionDto).toList();
        return page(items, mapper.countBountySubmissionQueue(normalized), safePage, safeSize);
    }

    @Transactional
    public BountySubmissionDTO reviewSubmission(Long submissionId, ReviewCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        if (cmd == null || cmd.getApproved() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String reason = IncentiveTypes.requireReason(cmd.getReason());
        BountySubmissionPO submission = mapper.lockBountySubmission(submissionId);
        if (submission == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"SUBMITTED".equals(submission.getSubmissionStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (operatorUid.equals(submission.getApplicantUid())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "the bounty applicant cannot review their own submission");
        }
        BountyPO bounty = mapper.lockBounty(submission.getBountyId());
        if (bounty == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (operatorUid.equals(bounty.getCreatedBy())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "the bounty creator cannot review submissions for this bounty");
        }
        Long entryId = null;
        String targetStatus = Boolean.TRUE.equals(cmd.getApproved()) ? "APPROVED" : "REJECTED";
        if ("APPROVED".equals(targetStatus)) {
            if (!"OPEN".equals(bounty.getBountyStatus())) {
                throw new BizException(ErrorCode.INVALID_STATUS);
            }
            requireBountyDomain(bounty.getDomainCode());
            Long publicAuthor = mapper.selectPublicPostAuthor(submission.getPublicPostId());
            if (publicAuthor == null || !publicAuthor.equals(submission.getApplicantUid())) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "referenced public evidence is no longer eligible");
            }
            String postDomain = mapper.selectPostDomainCode(submission.getPublicPostId());
            if (!IncentiveTypes.upper(bounty.getDomainCode()).equals(IncentiveTypes.upper(postDomain))) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "referenced public evidence no longer belongs to the bounty domain");
            }
            if (mapper.consumeBountyQuota(bounty.getId()) != 1) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "bounty quota has been exhausted");
            }
            mapper.insertUserBountyBudgetIfAbsent(bounty.getBudgetPeriod(), submission.getApplicantUid(),
                    IncentiveTypes.USER_BOUNTY_PERIOD_BUDGET);
            if (mapper.consumeUserBountyBudget(bounty.getBudgetPeriod(), submission.getApplicantUid(),
                    bounty.getPointReward()) != 1) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "applicant bounty period budget has been exhausted");
            }
            if (mapper.consumePlatformBountyBudget(
                    bounty.getBudgetPeriod(), bounty.getPointReward()) != 1) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "platform bounty reserved budget is inconsistent or exhausted");
            }
            LedgerPO entry = accountLedgerService.grantPlatformPoints(submission.getApplicantUid(),
                    bounty.getPointReward(), "BOUNTY:SUBMISSION:" + submissionId,
                    "BOUNTY_SUBMISSION", String.valueOf(submissionId), reason, operatorUid);
            entryId = entry.getId();
        }
        if (mapper.reviewBountySubmission(submissionId, targetStatus, operatorUid, reason, entryId) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        BountySubmissionPO after = mapper.lockBountySubmission(submissionId);
        adminAuditService.recordRequired(operatorUid, "QUOTA_BOUNTY_SUBMISSION_REVIEW",
                "QUOTA_BOUNTY_SUBMISSION", submissionId, submission, after, reason);
        return toSubmissionDto(after);
    }

    @Transactional
    public BountyAppealDTO submitBountyAppeal(Long submissionId, BountyAppealCmd cmd, Long userId) {
        requireUser(userId);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        BountySubmissionPO submission = mapper.lockBountySubmission(submissionId);
        if (submission == null || !userId.equals(submission.getApplicantUid())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!Set.of("APPROVED", "REJECTED").contains(submission.getSubmissionStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        BountyAppealPO po = new BountyAppealPO();
        po.setId(idGenerator.nextId());
        po.setSubmissionId(submissionId);
        po.setApplicantUid(userId);
        po.setOriginalStatus(submission.getSubmissionStatus());
        po.setOriginalReviewerUid(submission.getReviewerUid());
        po.setAppealReason(IncentiveTypes.requireText(cmd.getReason(), 1000, "reason"));
        po.setAppealStatus("SUBMITTED");
        try {
            mapper.insertBountyAppeal(po);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "this bounty submission has already been appealed");
        }
        return toBountyAppealDto(mapper.selectBountyAppeal(po.getId()));
    }

    public PageResult<BountyAppealDTO> userBountyAppeals(Long userId, Integer page, Integer size) {
        requireUser(userId);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<BountyAppealDTO> items = mapper.selectUserBountyAppeals(
                userId, (safePage - 1) * safeSize, safeSize).stream().map(this::toBountyAppealDto).toList();
        return page(items, mapper.countUserBountyAppeals(userId), safePage, safeSize);
    }

    public PageResult<BountyAppealDTO> bountyAppealQueue(String status, Integer page, Integer size,
                                                          Long operatorUid) {
        requireAdmin(operatorUid);
        String normalized = status == null ? null : requireAppealStatus(status);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        List<BountyAppealDTO> items = mapper.selectBountyAppealQueue(
                normalized, (safePage - 1) * safeSize, safeSize).stream().map(this::toBountyAppealDto).toList();
        return page(items, mapper.countBountyAppealQueue(normalized), safePage, safeSize);
    }

    @Transactional
    public BountyAppealDTO reviewBountyAppeal(Long appealId, ReviewCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        if (cmd == null || cmd.getApproved() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        BountyAppealPO appeal = mapper.lockBountyAppeal(appealId);
        if (appeal == null || !"SUBMITTED".equals(appeal.getAppealStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (appeal.getOriginalReviewerUid() != null && appeal.getOriginalReviewerUid().equals(operatorUid)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "the original reviewer cannot review the appeal");
        }
        if (operatorUid.equals(appeal.getApplicantUid())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "the bounty applicant cannot review their own appeal");
        }
        BountySubmissionPO submission = mapper.lockBountySubmission(appeal.getSubmissionId());
        BountyPO bounty = submission == null ? null : mapper.lockBounty(submission.getBountyId());
        if (submission == null || bounty == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (operatorUid.equals(bounty.getCreatedBy())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "the bounty creator cannot review appeals for this bounty");
        }
        String reason = IncentiveTypes.requireReason(cmd.getReason());
        Long compensationEntryId = null;
        String appealStatus = Boolean.TRUE.equals(cmd.getApproved()) ? "APPROVED" : "REJECTED";
        if ("APPROVED".equals(appealStatus)) {
            if ("APPROVED".equals(appeal.getOriginalStatus())) {
                ReversalCmd reversal = new ReversalCmd();
                reversal.setIdempotencyKey("BOUNTY_APPEAL:" + appealId);
                reversal.setReason(reason);
                LedgerEntryDTO reversalEntry = accountLedgerService.reverse(
                        submission.getRewardEntryId(), reversal, operatorUid);
                compensationEntryId = reversalEntry.getId();
                long recoveredAmount = Math.max(0, -reversalEntry.getDeltaTotal());
                if (mapper.restoreBountyQuota(bounty.getId(), recoveredAmount) != 1
                        || (recoveredAmount > 0
                        && (mapper.rollbackPlatformBountyAward(bounty.getBudgetPeriod(), recoveredAmount,
                        "OPEN".equals(bounty.getBountyStatus()) ? 1 : 0) != 1
                        || mapper.rollbackUserBountyAward(bounty.getBudgetPeriod(), submission.getApplicantUid(),
                        recoveredAmount) != 1))
                        || mapper.resolveBountySubmissionAppeal(submission.getId(), "APPROVED", "REJECTED",
                        operatorUid, reason, compensationEntryId) != 1) {
                    throw new BizException(ErrorCode.DATABASE_ERROR);
                }
            } else {
                if (!Set.of("OPEN", "CLOSED").contains(bounty.getBountyStatus())) {
                    throw new BizException(ErrorCode.INVALID_STATUS);
                }
                mapper.insertUserBountyBudgetIfAbsent(bounty.getBudgetPeriod(), submission.getApplicantUid(),
                        IncentiveTypes.USER_BOUNTY_PERIOD_BUDGET);
                if (mapper.consumeUserBountyBudget(bounty.getBudgetPeriod(), submission.getApplicantUid(),
                        bounty.getPointReward()) != 1) {
                    throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "appeal resettlement budget exhausted");
                }
                if ("CLOSED".equals(bounty.getBountyStatus())
                        && mapper.reservePlatformBountyBudget(bounty.getBudgetPeriod(), bounty.getPointReward()) != 1) {
                    throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "period budget cannot resettle appeal");
                }
                if (mapper.consumePlatformBountyBudget(bounty.getBudgetPeriod(), bounty.getPointReward()) != 1
                        || mapper.consumeBountyQuotaForAppeal(bounty.getId()) != 1) {
                    throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "appeal resettlement budget exhausted");
                }
                LedgerPO grant = accountLedgerService.grantPlatformPoints(submission.getApplicantUid(),
                        bounty.getPointReward(), "BOUNTY:APPEAL:" + appealId,
                        "BOUNTY_APPEAL", String.valueOf(appealId), reason, operatorUid);
                compensationEntryId = grant.getId();
                if (mapper.resolveBountySubmissionAppeal(submission.getId(), "REJECTED", "APPROVED",
                        operatorUid, reason, compensationEntryId) != 1) {
                    throw new BizException(ErrorCode.INVALID_STATUS);
                }
            }
        }
        if (mapper.reviewBountyAppeal(appealId, appealStatus, operatorUid, reason, compensationEntryId) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        BountyAppealPO after = mapper.selectBountyAppeal(appealId);
        adminAuditService.recordRequired(operatorUid, "QUOTA_BOUNTY_APPEAL_REVIEW",
                "QUOTA_BOUNTY_APPEAL", appealId, appeal, after, reason);
        return toBountyAppealDto(after);
    }

    private ThankTicketDTO ensureTicket(Long userId) {
        LocalDate today = LocalDate.now();
        mapper.insertThankTicketIfAbsent(idGenerator.nextId(), userId, today,
                IncentiveTypes.DEFAULT_DAILY_THANK_TICKETS);
        return ticketDto(mapper.selectThankTicket(userId, today));
    }

    private ThankTicketDTO ticketDto(ThankTicketPO po) {
        int granted = po == null || po.getGrantedCount() == null ? 0 : po.getGrantedCount();
        int used = po == null || po.getUsedCount() == null ? 0 : po.getUsedCount();
        return ThankTicketDTO.builder()
                .ticketDate(po == null ? LocalDate.now() : po.getTicketDate())
                .grantedCount(granted).usedCount(used).remainingCount(Math.max(0, granted - used))
                .purchasable(false).transferable(false).creditsReceiverBalance(false)
                .build();
    }

    private BountyPO requireBounty(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        // Serialize submissions with status changes so a close/cancel cannot race a new submission.
        BountyPO po = mapper.lockBounty(id);
        if (po == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return po;
    }

    private void requireBountyDomain(String domain) {
        if (mapper.countBountyEnabledDomain(IncentiveTypes.upper(domain)) != 1) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "incentives or bounties are disabled for this domain");
        }
    }

    private BountyDTO toBountyDto(BountyPO po) {
        return BountyDTO.builder()
                .id(po.getId()).title(po.getTitle()).description(po.getDescription())
                .domainCode(po.getDomainCode()).quota(po.getQuota()).awardedCount(po.getAwardedCount())
                .requestType(po.getRequestType()).riskCategory(po.getRiskCategory())
                .pointReward(po.getPointReward()).totalBudget(po.getTotalBudget())
                .budgetPeriod(po.getBudgetPeriod()).reservedBudget(po.getReservedBudget())
                .consumedBudget(po.getConsumedBudget())
                .status(po.getBountyStatus()).createdBy(po.getCreatedBy())
                .createTime(po.getCreateTime()).updateTime(po.getUpdateTime())
                .build();
    }

    private BountySubmissionDTO toSubmissionDto(BountySubmissionPO po) {
        return BountySubmissionDTO.builder()
                .id(po.getId()).bountyId(po.getBountyId()).applicantUid(po.getApplicantUid())
                .publicPostId(po.getPublicPostId()).requestType(po.getRequestType())
                .riskCategory(po.getRiskCategory())
                .evidence(po.getEvidence()).status(po.getSubmissionStatus()).reviewerUid(po.getReviewerUid())
                .reviewReason(po.getReviewReason()).rewardEntryId(po.getRewardEntryId())
                .createTime(po.getCreateTime()).updateTime(po.getUpdateTime())
                .build();
    }

    private BountyAppealDTO toBountyAppealDto(BountyAppealPO po) {
        return BountyAppealDTO.builder()
                .id(po.getId()).submissionId(po.getSubmissionId()).applicantUid(po.getApplicantUid())
                .originalStatus(po.getOriginalStatus()).originalReviewerUid(po.getOriginalReviewerUid())
                .appealReason(po.getAppealReason()).status(po.getAppealStatus())
                .reviewerUid(po.getReviewerUid()).reviewReason(po.getReviewReason())
                .compensationEntryId(po.getCompensationEntryId())
                .createTime(po.getCreateTime()).updateTime(po.getUpdateTime()).build();
    }

    private void requireAdmin(Long uid) {
        adminPermissionService.requireAdmin(uid);
    }

    private static void requireUser(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static String requireBountyStatus(String status) {
        String normalized = IncentiveTypes.upper(status);
        if (!BOUNTY_STATUSES.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static String requireSubmissionStatus(String status) {
        String normalized = IncentiveTypes.upper(status);
        if (!Set.of("SUBMITTED", "APPROVED", "REJECTED").contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static String requireBountyRequestType(String value) {
        String normalized = IncentiveTypes.upper(value);
        if (!BOUNTY_REQUEST_TYPES.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "unsupported bounty requestType");
        }
        return normalized;
    }

    private static String requireBountyRisk(String value) {
        String normalized = IncentiveTypes.upper(value);
        if (!BOUNTY_RISK_CATEGORIES.contains(normalized)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "only LOW risk bounties are enabled");
        }
        return normalized;
    }

    private static String requireAppealStatus(String value) {
        String normalized = IncentiveTypes.upper(value);
        if (!Set.of("SUBMITTED", "APPROVED", "REJECTED").contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static void requireAllowedClassification(String domain, String requestType, String riskCategory) {
        if (PROHIBITED_BOUNTY_CLASSIFICATIONS.contains(IncentiveTypes.upper(domain))
                || PROHIBITED_BOUNTY_CLASSIFICATIONS.contains(IncentiveTypes.upper(requestType))
                || !"LOW".equals(IncentiveTypes.upper(riskCategory))) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "prohibited bounty classification");
        }
    }

    private void requireModerationAllowed(Long uid, String sourceType, Long sourceId, String... values) {
        ContentModerationService.ModerationDecision decision = contentModerationService.checkContent(
                uid, "BOUNTY", sourceType, sourceId, values);
        if (decision.reviewRequired()) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "bounty content requires moderation review");
        }
    }

    private static Map<String, Long> groupStats(List<Map<String, Object>> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get("groupKey");
            Object count = row.get("groupCount");
            if (key != null && count instanceof Number number) {
                result.put(String.valueOf(key), number.longValue());
            }
        }
        return result;
    }

    private static void requireSafeIncentiveText(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (PROHIBITED_INCENTIVE_TERMS.stream().anyMatch(normalized::contains)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "bounty text contains a prohibited money, chance or authority concept");
        }
    }

    private static int safePage(Integer page) {
        return page == null || page <= 0 ? 1 : Math.min(page, 1000);
    }

    private static <T> PageResult<T> page(List<T> items, long total, int page, int size) {
        boolean hasMore = (long) page * size < total;
        return PageResult.<T>builder().items(items).total(total).hasMore(hasMore)
                .nextCursor(hasMore ? String.valueOf(page + 1) : null).build();
    }
}

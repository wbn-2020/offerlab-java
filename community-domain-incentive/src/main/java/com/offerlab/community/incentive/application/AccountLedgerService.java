package com.offerlab.community.incentive.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.incentive.api.IncentiveDtos.AccountDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BatchCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.FreezeCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.LedgerEntryDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.ReconciliationDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.ReversalCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RewardInboxCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RewardInboxReconcileDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RewardRuleCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.TrustedRewardInvalidationCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.TrustedRewardInvalidationResultDTO;
import com.offerlab.community.incentive.api.IncentiveProjectionReconciliationFacade;
import com.offerlab.community.incentive.domain.IncentiveTypes;
import com.offerlab.community.incentive.infrastructure.IncentiveMapper;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.AccountPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.FreezePO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.LedgerPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.InvalidationJobPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.ReconciliationRunPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RecoveryDebtPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RewardBatchPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RewardInboxPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RewardRulePO;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountLedgerService {
    private static final int RECONCILIATION_LIMIT = 1000;
    private static final int INVALIDATION_BATCH_LIMIT = 100;
    private static final int INVALIDATION_MAX_BATCHES_PER_RUN = 5;
    private static final int INVALIDATION_LEASE_SECONDS = 60;

    private final IncentiveMapper mapper;
    private final SnowflakeIdGenerator idGenerator;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;
    private final ObjectMapper objectMapper;
    private final String inlineInvalidationOwner = "inline-" + UUID.randomUUID();

    public List<AccountDTO> summary(Long userId) {
        requireUser(userId);
        return mapper.selectAccounts(userId, IncentiveTypes.MAX_LIST_LIMIT).stream().map(this::toAccountDto).toList();
    }

    public PageResult<LedgerEntryDTO> ledger(Long userId, Integer page, Integer size) {
        requireUser(userId);
        int safePage = safePage(page);
        int safeSize = IncentiveTypes.safeLimit(size);
        int offset = Math.multiplyExact(safePage - 1, safeSize);
        List<LedgerEntryDTO> items = mapper.selectLedgerPage(userId, offset, safeSize).stream()
                .map(this::toLedgerDto)
                .toList();
        return page(items, mapper.countLedger(userId), safePage, safeSize);
    }

    @Transactional
    public RewardInboxPO receiveReward(RewardInboxCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        RewardInboxPO saved = receiveRewardInternal(cmd, operatorUid);
        adminAuditService.recordRequired(operatorUid, "INCENTIVE_REWARD_INBOX_RECEIVE",
                "INCENTIVE_REWARD_INBOX", saved.getId(), null, saved,
                IncentiveTypes.requireReason(cmd.getReason()));
        return saved;
    }

    @Transactional
    public RewardInboxPO receiveTrustedReward(RewardInboxCmd cmd) {
        if (cmd == null || IncentiveTypes.upper(cmd.getEventDomainCode()) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "trusted reward eventDomainCode is required");
        }
        return receiveRewardInternal(cmd, 0L);
    }

    private RewardInboxPO receiveRewardInternal(RewardInboxCmd cmd, Long receivedBy) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        requireUser(cmd.getRecipientUid());
        String stableKey = IncentiveTypes.requireText(cmd.getStableKey(), 96, "stableKey");
        if (mapper.countActiveUser(cmd.getRecipientUid()) != 1) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        RewardRulePO rule = requireRule(cmd.getRuleCode(), cmd.getRuleVersion());
        String domain = IncentiveTypes.normalizeDomain(cmd.getDomainCode(), rule.getAccountType());
        String eventDomain = normalizeEventDomain(cmd.getEventDomainCode(), domain, rule);
        if (!"EVENT_DOMAIN".equals(rule.getDomainCode()) && !domain.equals(rule.getDomainCode())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "reward event domain does not match rule version");
        }
        String eventType = IncentiveTypes.requireText(cmd.getEventType(), 64, "eventType");
        String sourceType = IncentiveTypes.clean(cmd.getSourceReferenceType(), 48);
        String sourceId = IncentiveTypes.clean(cmd.getSourceReferenceId(), 128);
        String parentType = IncentiveTypes.clean(cmd.getParentReferenceType(), 48);
        String parentId = IncentiveTypes.clean(cmd.getParentReferenceId(), 128);
        String payloadJson = validJson(cmd.getPayloadJson());
        String requestFingerprint = rewardInboxRequestFingerprint(
                eventType, cmd.getRecipientUid(), domain, eventDomain, sourceType, sourceId,
                parentType, parentId, rule.getRuleCode(), rule.getRuleVersion(), payloadJson);
        RewardInboxPO existing = mapper.selectInboxByStableKey(stableKey);
        if (existing != null) {
            return requireEquivalentInbox(existing, requestFingerprint);
        }
        RewardInboxPO po = new RewardInboxPO();
        po.setId(idGenerator.nextId());
        po.setStableKey(stableKey);
        po.setEventType(eventType);
        po.setRequestFingerprint(requestFingerprint);
        po.setRecipientUid(cmd.getRecipientUid());
        po.setDomainCode(domain);
        po.setEventDomainCode(eventDomain);
        po.setSourceReferenceType(sourceType);
        po.setSourceReferenceId(sourceId);
        po.setParentReferenceType(parentType);
        po.setParentReferenceId(parentId);
        po.setRuleCode(rule.getRuleCode());
        po.setRuleVersion(rule.getRuleVersion());
        po.setPayloadJson(payloadJson);
        po.setReceivedBy(receivedBy);
        po.setReceiveReason(IncentiveTypes.requireReason(cmd.getReason()));
        po.setInboxStatus("PENDING");
        po.setAttemptCount(0);
        try {
            int inserted = mapper.insertRewardInbox(po);
            if (inserted == 0) {
                return requireEquivalentInbox(
                        mapper.selectInboxByStableKey(stableKey), requestFingerprint);
            }
        } catch (DuplicateKeyException ignored) {
            return requireEquivalentInbox(mapper.selectInboxByStableKey(stableKey), requestFingerprint);
        }
        return mapper.selectInboxByStableKey(stableKey);
    }

    @Transactional
    public RewardRulePO createRule(RewardRuleCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "positive reward amount is required");
        }
        long rewardAmount = IncentiveTypes.requirePositive(cmd.getAmount(), "amount");
        String reason = IncentiveTypes.requireReason(cmd.getReason());
        String type = IncentiveTypes.requireAccountType(cmd.getAccountType());
        String domain = IncentiveTypes.normalizeDomain(cmd.getDomainCode(), type);
        String code = IncentiveTypes.requireText(cmd.getRuleCode(), 64, "ruleCode").toUpperCase();
        if (cmd.getValidFrom() != null && cmd.getValidUntil() != null
                && !cmd.getValidUntil().isAfter(cmd.getValidFrom())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "validUntil must be after validFrom");
        }
        RewardRulePO po = new RewardRulePO();
        po.setId(idGenerator.nextId());
        po.setRuleCode(code);
        po.setRuleVersion(cmd.getRuleVersion());
        po.setAccountType(type);
        po.setDomainCode(domain);
        po.setRewardAmount(rewardAmount);
        po.setDailyUserCap(nonNegative(cmd.getDailyUserCap()));
        po.setLifetimeUserCap(nonNegative(cmd.getLifetimeUserCap()));
        po.setEnabled(Boolean.FALSE.equals(cmd.getEnabled()) ? 0 : 1);
        po.setValidFrom(cmd.getValidFrom());
        po.setValidUntil(cmd.getValidUntil());
        po.setCreatedBy(operatorUid);
        po.setChangeReason(reason);
        try {
            mapper.insertRewardRule(po);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "reward rule code and version already exist");
        }
        adminAuditService.recordRequired(operatorUid, "INCENTIVE_REWARD_RULE_CREATE",
                "INCENTIVE_REWARD_RULE", code + ":" + cmd.getRuleVersion(), null, po, reason);
        return po;
    }

    @Transactional
    public RewardBatchPO processBatch(BatchCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String reason = IncentiveTypes.requireReason(cmd.getReason());
        String batchKey = IncentiveTypes.requireText(cmd.getBatchKey(), 96, "batchKey");
        String requestedRuleCode = IncentiveTypes.requireText(cmd.getRuleCode(), 64, "ruleCode")
                .toUpperCase();
        if (cmd.getRuleVersion() == null || cmd.getRuleVersion() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "ruleVersion must be positive");
        }
        RewardBatchPO existing = mapper.selectBatchByKey(batchKey);
        if (existing != null) {
            requireEquivalentBatch(existing, requestedRuleCode, cmd.getRuleVersion());
            return existing;
        }
        RewardRulePO rule = requireRule(requestedRuleCode, cmd.getRuleVersion());
        int limit = IncentiveTypes.safeLimit(cmd.getLimit());
        List<Long> ids = mapper.selectPendingInboxIds(rule.getRuleCode(), rule.getRuleVersion(), limit);
        RewardBatchPO batch = new RewardBatchPO();
        batch.setId(idGenerator.nextId());
        batch.setBatchKey(batchKey);
        batch.setRuleCode(rule.getRuleCode());
        batch.setRuleVersion(rule.getRuleVersion());
        batch.setBatchStatus("RUNNING");
        batch.setRequestedCount(ids.size());
        batch.setAppliedCount(0);
        batch.setRejectedCount(0);
        batch.setFailedCount(0);
        batch.setCreatedBy(operatorUid);
        batch.setActionReason(reason);
        try {
            mapper.insertRewardBatch(batch);
        } catch (DuplicateKeyException e) {
            RewardBatchPO concurrent = mapper.selectBatchByKey(batchKey);
            if (concurrent != null) {
                requireEquivalentBatch(concurrent, rule.getRuleCode(), rule.getRuleVersion());
                return concurrent;
            }
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "reward batch key was already used");
        }

        int applied = 0;
        int rejected = 0;
        for (Long id : ids) {
            RewardProcessResult result = processInbox(id, batch.getId());
            if (result == RewardProcessResult.APPLIED) {
                applied++;
            } else {
                rejected++;
            }
        }
        mapper.finishBatch(batch.getId(), "COMPLETED", applied, rejected, 0);
        RewardBatchPO completed = mapper.selectBatchByKey(batchKey);
        adminAuditService.recordRequired(operatorUid, "INCENTIVE_REWARD_BATCH_PROCESS",
                "INCENTIVE_REWARD_BATCH", batch.getId(), null, completed, reason);
        return completed;
    }

    public int countOverduePendingRewards(Integer requestedLimit, Long operatorUid) {
        requireAdmin(operatorUid);
        int limit = IncentiveTypes.safeLimit(requestedLimit);
        return mapper.countOverduePendingInbox(rewardInboxCutoff(), limit + 1);
    }

    @Transactional
    public RewardInboxReconcileDTO reconcileOverduePendingRewards(
            Integer requestedLimit,
            String rawReason,
            Long operatorUid) {
        requireAdmin(operatorUid);
        IncentiveTypes.requireReason(rawReason);
        int limit = IncentiveTypes.safeLimit(requestedLimit);
        LocalDateTime cutoff = rewardInboxCutoff();
        List<Long> ids = mapper.selectOverduePendingInboxIdsForUpdate(cutoff, limit);
        int applied = 0;
        int rejected = 0;
        for (Long id : ids) {
            RewardProcessResult result = processInbox(id, null);
            if (result == RewardProcessResult.APPLIED) {
                applied++;
            } else {
                rejected++;
            }
        }
        boolean coverageComplete = mapper.countOverduePendingInbox(cutoff, 1) == 0;
        return RewardInboxReconcileDTO.builder()
                .slaMinutes(IncentiveProjectionReconciliationFacade.REWARD_INBOX_SLA_MINUTES)
                .processedCount(ids.size())
                .appliedCount(applied)
                .rejectedCount(rejected)
                .coverageComplete(coverageComplete)
                .build();
    }

    @Transactional
    public LedgerEntryDTO freeze(Long userId, FreezeCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        requireUser(userId);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String reason = IncentiveTypes.requireReason(cmd == null ? null : cmd.getReason());
        String type = IncentiveTypes.requireAccountType(cmd.getAccountType());
        String domain = IncentiveTypes.normalizeDomain(cmd.getDomainCode(), type);
        long amount = IncentiveTypes.requirePositive(cmd.getAmount(), "amount");
        int blocksSpending = Boolean.FALSE.equals(cmd.getBlockSpending()) ? 0 : 1;
        LedgerPO entry = append(userId, type, domain, "FREEZE", 0, -amount, amount,
                cmd.getIdempotencyKey(), "FREEZE", null, null, null, null, null, reason, operatorUid);
        FreezePO existingFreeze = mapper.selectFreezeByEntry(entry.getId());
        if (existingFreeze != null) {
            if (!Objects.equals(existingFreeze.getFreezeAmount(), amount)
                    || !Objects.equals(existingFreeze.getBlocksSpending(), blocksSpending)
                    || !Objects.equals(existingFreeze.getActionReason(), reason)) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                        "freeze idempotency key was already used by a different freeze request");
            }
            return toLedgerDto(entry);
        }
        FreezePO freeze = new FreezePO();
        freeze.setId(idGenerator.nextId());
        freeze.setAccountId(entry.getAccountId());
        freeze.setUserId(userId);
        freeze.setFreezeAmount(amount);
        freeze.setFreezeStatus("ACTIVE");
        freeze.setBlocksSpending(blocksSpending);
        freeze.setFreezeEntryId(entry.getId());
        freeze.setOperatorUid(operatorUid);
        freeze.setActionReason(reason);
        mapper.insertFreeze(freeze);
        if (freeze.getBlocksSpending() == 1) {
            mapper.updateAccountStatus(entry.getAccountId(),
                    mapper.countPendingRecoveryDebts(entry.getAccountId()) > 0
                            ? "RECOVERY_DUE" : "FROZEN");
        }
        adminAuditService.recordRequired(operatorUid, "INCENTIVE_ACCOUNT_FREEZE",
                "INCENTIVE_ACCOUNT", entry.getAccountId(), null, entry, reason);
        return toLedgerDto(entry);
    }

    @Transactional
    public LedgerEntryDTO releaseFreeze(Long freezeId, ReversalCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        String reason = IncentiveTypes.requireReason(cmd == null ? null : cmd.getReason());
        String rawKey = IncentiveTypes.requireText(cmd == null ? null : cmd.getIdempotencyKey(),
                96, "idempotencyKey");
        FreezePO freeze = mapper.lockFreeze(freezeId);
        if (freeze == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        AccountPO account = requireAccountById(freeze.getAccountId());
        if (!"ACTIVE".equals(freeze.getFreezeStatus())) {
            if ("RELEASED".equals(freeze.getFreezeStatus()) && freeze.getReleaseEntryId() != null) {
                LedgerPO existing = mapper.selectLedgerById(freeze.getReleaseEntryId());
                requireEquivalentLedger(existing, operationKey("RECOVERY", rawKey),
                        ledgerRequestFingerprint(freeze.getUserId(), account.getAccountType(),
                                account.getDomainCode(), "RECOVERY", 0,
                                freeze.getFreezeAmount(), -freeze.getFreezeAmount(),
                                "FREEZE", String.valueOf(freezeId), null, null,
                                null, null, operatorUid));
                if (!Objects.equals(existing.getReason(), reason)) {
                    throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                            "freeze release idempotency key was already used by a different request");
                }
                return toLedgerDto(existing);
            }
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        LedgerPO entry = append(freeze.getUserId(), account.getAccountType(), account.getDomainCode(),
                "RECOVERY", 0, freeze.getFreezeAmount(), -freeze.getFreezeAmount(),
                rawKey, "FREEZE", String.valueOf(freezeId), null, null,
                null, null, reason, operatorUid);
        if (mapper.releaseFreeze(freezeId, entry.getId(), reason) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (mapper.countActiveBlockingFreezes(freeze.getAccountId()) == 0) {
            mapper.updateAccountStatus(freeze.getAccountId(),
                    mapper.countPendingRecoveryDebts(freeze.getAccountId()) > 0
                            ? "RECOVERY_DUE" : "ACTIVE");
        }
        adminAuditService.recordRequired(operatorUid, "INCENTIVE_ACCOUNT_FREEZE_RELEASE",
                "INCENTIVE_FREEZE", freezeId, freeze, entry, reason);
        return toLedgerDto(entry);
    }

    @Transactional
    public LedgerEntryDTO reverse(Long ledgerId, ReversalCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        String reason = IncentiveTypes.requireReason(cmd == null ? null : cmd.getReason());
        String rawKey = IncentiveTypes.requireText(cmd == null ? null : cmd.getIdempotencyKey(),
                96, "idempotencyKey");
        LedgerPO original = mapper.selectLedgerById(ledgerId);
        if (original == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (original.getReversedEntryId() != null || "REVERSAL".equals(original.getEntryType())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        LedgerPO existingReversal = mapper.selectReversalByOriginal(ledgerId);
        if (existingReversal != null) {
            requireEquivalentReversal(existingReversal, original, operationKey("REVERSAL", rawKey));
            return toLedgerDto(existingReversal);
        }
        if (!Set.of("REWARD", "PLATFORM_GRANT").contains(original.getEntryType())) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "only standalone reward grants can be reversed directly");
        }
        LedgerPO reversal = createReversalWithDebt(
                original, rawKey, "LEDGER", String.valueOf(ledgerId), reason, operatorUid);
        adminAuditService.recordRequired(operatorUid, "INCENTIVE_LEDGER_REVERSE",
                "INCENTIVE_LEDGER", ledgerId, original, reversal, reason);
        return toLedgerDto(reversal);
    }

    @Transactional
    public TrustedRewardInvalidationResultDTO invalidateTrustedReward(
            TrustedRewardInvalidationCmd cmd, Long operatorUid) {
        requireAdmin(operatorUid);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String referenceType = IncentiveTypes.requireText(
                IncentiveTypes.upper(cmd.getReferenceType()), 48, "referenceType");
        String referenceId = IncentiveTypes.requireText(cmd.getReferenceId(), 128, "referenceId");
        String reason = IncentiveTypes.requireReason(cmd.getReason());
        enqueueTrustedRewardInvalidation(referenceType, referenceId, reason);
        long cursor = cmd.getCursor() == null ? 0 : Math.max(0, cmd.getCursor());
        int limit = Math.min(cmd.getLimit() == null ? INVALIDATION_BATCH_LIMIT
                : Math.max(1, cmd.getLimit()), INVALIDATION_BATCH_LIMIT);
        InvalidationBatch batch = invalidateTrustedRewardInternal(
                referenceType, referenceId, cursor, limit, reason, operatorUid);
        TrustedRewardInvalidationResultDTO result = invalidationResult(
                referenceType, referenceId, cursor, batch);
        adminAuditService.recordRequired(operatorUid, "INCENTIVE_TRUSTED_REWARD_INVALIDATE",
                "INCENTIVE_REWARD_SOURCE", referenceType + ":" + referenceId,
                null, result, reason);
        return result;
    }

    @Transactional
    public LedgerPO restoreAppeal(String targetType, Long targetId, Long appealId,
                                  String reason, Long operatorUid) {
        requireAdmin(operatorUid);
        String normalizedType = IncentiveTypes.upper(targetType);
        if ("FREEZE".equals(normalizedType)) {
            FreezePO freeze = mapper.lockFreeze(targetId);
            if (freeze == null || !"ACTIVE".equals(freeze.getFreezeStatus())) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "freeze is no longer active");
            }
            AccountPO account = requireAccountById(freeze.getAccountId());
            LedgerPO restore = append(freeze.getUserId(), account.getAccountType(), account.getDomainCode(),
                    "RESTORE", 0, freeze.getFreezeAmount(), -freeze.getFreezeAmount(),
                    "APPEAL:" + appealId, "INCENTIVE_APPEAL", String.valueOf(appealId),
                    null, null, null, null, reason, operatorUid);
            if (mapper.releaseFreeze(freeze.getId(), restore.getId(), reason) != 1) {
                throw new BizException(ErrorCode.INVALID_STATUS);
            }
            if (mapper.countActiveBlockingFreezes(freeze.getAccountId()) == 0) {
                mapper.updateAccountStatus(freeze.getAccountId(),
                        mapper.countPendingRecoveryDebts(freeze.getAccountId()) > 0
                                ? "RECOVERY_DUE" : "ACTIVE");
            }
            return restore;
        }
        LedgerPO original = mapper.selectLedgerById(targetId);
        if (original == null || (!"LEDGER".equals(normalizedType) && !"REVERSAL".equals(normalizedType))) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if ("REVERSAL".equals(normalizedType) && !"REVERSAL".equals(original.getEntryType())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "target is not a reversal ledger entry");
        }
        if ("LEDGER".equals(normalizedType) && !"GOVERNANCE_DEBIT".equals(original.getEntryType())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "only explicit governance debits can be restored through a LEDGER appeal");
        }
        boolean hasRecoveryDebt = "REVERSAL".equals(normalizedType)
                && original.getReversedEntryId() != null
                && mapper.selectRecoveryDebtByOriginal(original.getReversedEntryId()) != null;
        if (!hasRecoveryDebt
                && original.getDeltaTotal() >= 0
                && original.getDeltaAvailable() >= 0
                && original.getDeltaFrozen() >= 0) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "ledger entry has no compensable debit");
        }
        if ("REVERSAL".equals(normalizedType) && original.getReversedEntryId() != null) {
            requireAccountById(original.getAccountId());
            RecoveryDebtPO debt = mapper.lockRecoveryDebtByOriginal(original.getReversedEntryId());
            if (debt != null) {
                Map<String, Object> totals = mapper.selectAppealRecoveryTotals(original.getId(), debt.getId());
                long restoreTotal = number(totals.get("restoreTotal"));
                long restoreAvailable = number(totals.get("restoreAvailable"));
                long restoreFrozen = number(totals.get("restoreFrozen"));
                if (restoreTotal < 0 || restoreAvailable < 0 || restoreFrozen < 0
                        || restoreTotal != restoreAvailable + restoreFrozen) {
                    throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                            "reversal recovery history is inconsistent");
                }
                if ("PENDING".equals(debt.getDebtStatus())) {
                    mapper.cancelRecoveryDebt(original.getReversedEntryId(), reason);
                    refreshRecoveryState(original.getAccountId());
                }
                LedgerPO restore = append(original.getUserId(), original.getAccountType(), original.getDomainCode(),
                        "RESTORE", restoreTotal, restoreAvailable, restoreFrozen,
                        "APPEAL:" + appealId, "INCENTIVE_APPEAL", String.valueOf(appealId),
                        original.getRuleCode(), original.getRuleVersion(), null, null, reason, operatorUid);
                settleRecoveryDebt(restore, reason);
                return restore;
            }
        }
        LedgerPO restore = append(original.getUserId(), original.getAccountType(), original.getDomainCode(),
                "RESTORE", -original.getDeltaTotal(), -original.getDeltaAvailable(), -original.getDeltaFrozen(),
                "APPEAL:" + appealId, "INCENTIVE_APPEAL", String.valueOf(appealId),
                original.getRuleCode(), original.getRuleVersion(), null, null, reason, operatorUid);
        settleRecoveryDebt(restore, reason);
        return restore;
    }

    @Transactional
    public ReconciliationDTO reconcile(Integer requestedLimit, String rawReason, Long operatorUid) {
        requireAdmin(operatorUid);
        String reason = IncentiveTypes.requireReason(rawReason);
        int limit = reconciliationLimit(requestedLimit);
        mapper.insertReconciliationCursorIfAbsent();
        Map<String, Object> cursor = mapper.lockReconciliationCursor();
        long cursorStart = number(cursor.get("lastAccountId"));
        long cycleNo = Math.max(1, number(cursor.get("cycleNo")));
        long runId = idGenerator.nextId();
        mapper.insertReconciliationRun(runId, cycleNo, cursorStart, operatorUid, reason);
        List<Map<String, Object>> rows = mapper.selectReconciliationRows(cursorStart, limit);
        int mismatches = 0;
        long totalDifference = 0;
        for (Map<String, Object> row : rows) {
            long projectedTotal = number(row.get("projectedTotal"));
            long ledgerTotal = number(row.get("ledgerTotal"));
            long projectedAvailable = number(row.get("projectedAvailable"));
            long ledgerAvailable = number(row.get("ledgerAvailable"));
            long projectedFrozen = number(row.get("projectedFrozen"));
            long ledgerFrozen = number(row.get("ledgerFrozen"));
            long difference = Math.abs(projectedTotal - ledgerTotal)
                    + Math.abs(projectedAvailable - ledgerAvailable)
                    + Math.abs(projectedFrozen - ledgerFrozen);
            if (difference > 0) {
                mismatches++;
                totalDifference = Math.addExact(totalDifference, difference);
                mapper.insertReconciliationItem(idGenerator.nextId(), runId, number(row.get("accountId")),
                        projectedTotal, ledgerTotal, projectedAvailable, ledgerAvailable,
                        projectedFrozen, ledgerFrozen, difference);
            }
        }
        long cursorEnd = rows.isEmpty() ? cursorStart : number(rows.get(rows.size() - 1).get("accountId"));
        boolean coverageComplete = rows.size() < limit;
        long nextCursor = coverageComplete ? 0 : cursorEnd;
        mapper.finishReconciliation(runId, rows.size(), mismatches, totalDifference,
                cursorEnd, nextCursor, coverageComplete ? 1 : 0);
        mapper.advanceReconciliationCursor(nextCursor, coverageComplete ? 1 : 0);
        ReconciliationDTO result = toReconciliationDto(mapper.selectReconciliationRun(runId));
        adminAuditService.recordRequired(operatorUid, "INCENTIVE_RECONCILIATION_RUN",
                "INCENTIVE_RECONCILIATION", runId, null, result, reason);
        return result;
    }

    public ReconciliationDTO previewReconciliation(Integer requestedLimit, String rawReason, Long operatorUid) {
        requireAdmin(operatorUid);
        String reason = IncentiveTypes.requireReason(rawReason);
        int limit = reconciliationLimit(requestedLimit);
        Map<String, Object> cursor = mapper.selectReconciliationCursor();
        long cursorStart = cursor == null ? 0 : number(cursor.get("lastAccountId"));
        long cycleNo = cursor == null ? 1 : Math.max(1, number(cursor.get("cycleNo")));
        List<Map<String, Object>> rows = mapper.selectReconciliationRows(cursorStart, limit);
        int mismatches = 0;
        long totalDifference = 0;
        for (Map<String, Object> row : rows) {
            long difference = reconciliationDifference(row);
            if (difference > 0) {
                mismatches++;
                totalDifference = Math.addExact(totalDifference, difference);
            }
        }
        long cursorEnd = rows.isEmpty() ? cursorStart : number(rows.get(rows.size() - 1).get("accountId"));
        boolean coverageComplete = rows.size() < limit;
        LocalDateTime now = LocalDateTime.now();
        return ReconciliationDTO.builder()
                .status("DRY_RUN")
                .scannedCount(rows.size())
                .mismatchCount(mismatches)
                .totalAbsoluteDifference(totalDifference)
                .cycleNo(cycleNo)
                .cursorStartAccountId(cursorStart)
                .cursorEndAccountId(cursorEnd)
                .nextCursorAccountId(coverageComplete ? 0 : cursorEnd)
                .coverageComplete(coverageComplete)
                .operatorUid(operatorUid)
                .reason(reason)
                .createTime(now)
                .finishTime(now)
                .build();
    }

    public List<ReconciliationDTO> reconciliationRuns(Integer limit, Long operatorUid) {
        requireAdmin(operatorUid);
        return mapper.selectReconciliationRuns(IncentiveTypes.safeLimit(limit)).stream()
                .map(this::toReconciliationDto)
                .toList();
    }

    @Transactional
    public LedgerPO spendPoints(Long userId, long amount, String idempotencyKey,
                                String referenceType, String referenceId, String reason) {
        IncentiveTypes.requirePositive(amount, "amount");
        return append(userId, "POINT", IncentiveTypes.GLOBAL_DOMAIN, "SPEND",
                -amount, -amount, 0, idempotencyKey, referenceType, referenceId,
                null, null, null, null, reason, userId);
    }

    @Transactional
    public LedgerPO refundPoints(Long userId, long amount, String idempotencyKey,
                                 String referenceType, String referenceId, String reason, Long operatorUid) {
        IncentiveTypes.requirePositive(amount, "amount");
        LedgerPO refund = append(userId, "POINT", IncentiveTypes.GLOBAL_DOMAIN, "REFUND",
                amount, amount, 0, idempotencyKey, referenceType, referenceId,
                null, null, null, null, reason, operatorUid);
        settleRecoveryDebt(refund, reason);
        return refund;
    }

    @Transactional
    public LedgerPO grantPlatformPoints(Long userId, long amount, String idempotencyKey,
                                        String referenceType, String referenceId, String reason, Long operatorUid) {
        IncentiveTypes.requirePositive(amount, "amount");
        LedgerPO grant = append(userId, "POINT", IncentiveTypes.GLOBAL_DOMAIN, "PLATFORM_GRANT",
                amount, amount, 0, idempotencyKey, referenceType, referenceId,
                null, null, null, null, reason, operatorUid);
        settleRecoveryDebt(grant, reason);
        return grant;
    }

    private RewardProcessResult processInbox(Long inboxId, Long batchId) {
        RewardInboxPO inbox = mapper.lockInbox(inboxId);
        if (inbox == null || !"PENDING".equals(inbox.getInboxStatus())) {
            return RewardProcessResult.REJECTED;
        }
        RewardRulePO rule = requireRule(inbox.getRuleCode(), inbox.getRuleVersion());
        LocalDateTime now = LocalDateTime.now();
        if (rule.getEnabled() == null || rule.getEnabled() != 1
                || (rule.getValidFrom() != null && now.isBefore(rule.getValidFrom()))
                || (rule.getValidUntil() != null && !now.isBefore(rule.getValidUntil()))) {
            requireInboxUpdated(mapper.updateInbox(inboxId, "REJECTED", batchId, null, "RULE_INACTIVE"));
            return RewardProcessResult.REJECTED;
        }
        if (inbox.getEventDomainCode() != null
                && mapper.countIncentiveEnabledDomain(inbox.getEventDomainCode()) != 1) {
            requireInboxUpdated(mapper.updateInbox(
                    inboxId, "REJECTED", batchId, null, "EVENT_DOMAIN_INCENTIVES_DISABLED"));
            return RewardProcessResult.REJECTED;
        }
        if (isRewardInvalidated(inbox)) {
            requireInboxUpdated(mapper.updateInbox(
                    inboxId, "REJECTED", batchId, null, "TRUSTED_SOURCE_INVALIDATED"));
            return RewardProcessResult.REJECTED;
        }
        mapper.insertRewardGuardIfAbsent(inbox.getRecipientUid(), rule.getRuleCode(), rule.getRuleVersion());
        Map<String, Object> guard = mapper.lockRewardGuard(
                inbox.getRecipientUid(), rule.getRuleCode(), rule.getRuleVersion());
        if (guard == null) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                    "reward guard projection is missing");
        }
        long daily = dailyAwardedFor(guard);
        long lifetime = number(guard.get("lifetimeAwarded"));
        if ((rule.getDailyUserCap() > 0
                && exceedsCap(daily, rule.getRewardAmount(), rule.getDailyUserCap()))
                || (rule.getLifetimeUserCap() > 0
                && exceedsCap(lifetime, rule.getRewardAmount(), rule.getLifetimeUserCap()))) {
            requireInboxUpdated(mapper.updateInbox(inboxId, "REJECTED", batchId, null, "RULE_CAP_REACHED"));
            return RewardProcessResult.REJECTED;
        }
        String rewardDomain = "EVENT_DOMAIN".equals(rule.getDomainCode())
                ? inbox.getDomainCode() : rule.getDomainCode();
        LedgerPO entry = append(inbox.getRecipientUid(), rule.getAccountType(), rewardDomain,
                "REWARD", rule.getRewardAmount(), rule.getRewardAmount(), 0,
                "INBOX:" + inbox.getStableKey(), "REWARD_INBOX", String.valueOf(inboxId),
                rule.getRuleCode(), rule.getRuleVersion(), batchId, null,
                "Reward rule " + rule.getRuleCode() + " v" + rule.getRuleVersion(), null);
        settleRecoveryDebt(entry, "Automatic recovery from future reward");
        if (mapper.incrementRewardGuard(inbox.getRecipientUid(), rule.getRuleCode(),
                rule.getRuleVersion(), rule.getRewardAmount()) != 1) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                    "failed to update reward guard projection");
        }
        requireInboxUpdated(mapper.updateInbox(inboxId, "APPLIED", batchId, entry.getId(), null));
        return RewardProcessResult.APPLIED;
    }

    private LedgerPO append(Long userId, String accountType, String domainCode, String entryType,
                            long deltaTotal, long deltaAvailable, long deltaFrozen, String rawIdempotencyKey,
                            String referenceType, String referenceId, String ruleCode, Integer ruleVersion,
                            Long batchId, Long reversedEntryId, String reason, Long operatorUid) {
        requireUser(userId);
        String type = IncentiveTypes.requireAccountType(accountType);
        String domain = IncentiveTypes.normalizeDomain(domainCode, type);
        String normalizedEntryType = IncentiveTypes.requireText(entryType, 24, "entryType");
        String normalizedReferenceType = IncentiveTypes.clean(referenceType, 32);
        String normalizedReferenceId = IncentiveTypes.clean(referenceId, 64);
        String normalizedRuleCode = IncentiveTypes.clean(ruleCode, 64);
        String rawKey = IncentiveTypes.requireText(rawIdempotencyKey, 96, "idempotencyKey");
        String idempotencyKey = operationKey(normalizedEntryType, rawKey);
        String requestFingerprint = ledgerRequestFingerprint(userId, type, domain, normalizedEntryType,
                deltaTotal, deltaAvailable, deltaFrozen, normalizedReferenceType, normalizedReferenceId,
                normalizedRuleCode, ruleVersion, batchId, reversedEntryId, operatorUid);
        if (deltaTotal != deltaAvailable + deltaFrozen) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "ledger deltas do not balance");
        }
        LedgerPO existing = mapper.selectLedgerByIdempotency(idempotencyKey);
        if (existing != null) {
            requireEquivalentLedger(existing, idempotencyKey, requestFingerprint);
            return existing;
        }
        mapper.insertAccountIfAbsent(idGenerator.nextId(), userId, type, domain);
        AccountPO account = mapper.lockAccount(userId, type, domain);
        if (account == null) {
            throw new BizException(ErrorCode.DATABASE_ERROR);
        }
        existing = mapper.selectLedgerByIdempotency(idempotencyKey);
        if (existing != null) {
            requireEquivalentLedger(existing, idempotencyKey, requestFingerprint);
            return existing;
        }
        String status = account.getAccountStatus();
        if ("SPEND".equals(entryType) && !"ACTIVE".equals(status)) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "account is risk-frozen and cannot spend");
        }
        long activeDeltaAvailable = deltaAvailable;
        long activeDeltaFrozen = deltaFrozen;
        if (!"ACTIVE".equals(status) && deltaTotal > 0 && deltaFrozen == 0) {
            activeDeltaAvailable = 0;
            activeDeltaFrozen = deltaTotal;
        }
        long totalAfter = Math.addExact(account.getTotalBalance(), deltaTotal);
        long availableAfter = Math.addExact(account.getAvailableBalance(), activeDeltaAvailable);
        long frozenAfter = Math.addExact(account.getFrozenBalance(), activeDeltaFrozen);
        if (totalAfter < 0 || availableAfter < 0 || frozenAfter < 0 || totalAfter != availableAfter + frozenAfter) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "insufficient or inconsistent incentive balance");
        }
        String nextStatus = status;
        if (mapper.updateAccount(account.getId(), account.getVersion(), deltaTotal,
                activeDeltaAvailable, activeDeltaFrozen, nextStatus) != 1) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(), "incentive account projection update conflict");
        }
        LedgerPO entry = new LedgerPO();
        entry.setId(idGenerator.nextId());
        entry.setAccountId(account.getId());
        entry.setUserId(userId);
        entry.setAccountType(type);
        entry.setDomainCode(domain);
        entry.setEntryType(normalizedEntryType);
        entry.setDeltaTotal(deltaTotal);
        entry.setDeltaAvailable(activeDeltaAvailable);
        entry.setDeltaFrozen(activeDeltaFrozen);
        entry.setTotalAfter(totalAfter);
        entry.setAvailableAfter(availableAfter);
        entry.setFrozenAfter(frozenAfter);
        entry.setIdempotencyKey(idempotencyKey);
        entry.setRequestFingerprint(requestFingerprint);
        entry.setReferenceType(normalizedReferenceType);
        entry.setReferenceId(normalizedReferenceId);
        entry.setRuleCode(normalizedRuleCode);
        entry.setRuleVersion(ruleVersion);
        entry.setBatchId(batchId);
        entry.setReversedEntryId(reversedEntryId);
        entry.setReason(IncentiveTypes.requireReason(reason));
        entry.setOperatorUid(operatorUid);
        mapper.insertLedger(entry);
        return mapper.selectLedgerById(entry.getId());
    }

    private InvalidationBatch invalidateTrustedRewardInternal(String referenceType, String referenceId,
                                                               long cursor, int limit,
                                                               String reason, Long operatorUid) {
        List<LedgerPO> originals = mapper.selectAppliedRewardLedgersBySource(
                referenceType, referenceId, cursor, limit);
        List<LedgerPO> reversals = new ArrayList<>();
        for (LedgerPO original : originals) {
            LedgerPO existing = mapper.selectReversalByOriginal(original.getId());
            if (existing != null) {
                reversals.add(existing);
                continue;
            }
            reversals.add(createReversalWithDebt(
                    original,
                    "INVALIDATE:" + sha256(referenceType + ":" + referenceId + ":" + original.getId()),
                    "TRUSTED_REWARD_SOURCE", referenceType + ":" + referenceId,
                    reason, operatorUid));
        }
        long nextCursor = originals.isEmpty() ? cursor : originals.get(originals.size() - 1).getId();
        return new InvalidationBatch(reversals, nextCursor, originals.size() < limit);
    }

    private boolean isRewardInvalidated(RewardInboxPO inbox) {
        if (inbox.getSourceReferenceType() != null && inbox.getSourceReferenceId() != null) {
            String sourceKey = sha256(
                    inbox.getSourceReferenceType() + "|" + inbox.getSourceReferenceId());
            if (mapper.lockInvalidationJobByKey(sourceKey) != null) {
                return true;
            }
            if ("COMMENT_HELPFUL_THRESHOLD_REACHED".equals(inbox.getSourceReferenceType())
                    && inbox.getSourceReferenceId().startsWith("comment:")) {
                String commentId = inbox.getSourceReferenceId().substring("comment:".length());
                if (mapper.countInvalidationJobs("COMMENT", commentId) > 0
                        || mapper.countCommentBranchInvalidation(commentId) > 0) {
                    return true;
                }
            }
        }
        if (inbox.getParentReferenceType() != null && inbox.getParentReferenceId() != null) {
            String parentKey = sha256(
                    inbox.getParentReferenceType() + "|" + inbox.getParentReferenceId());
            return mapper.lockInvalidationJobByKey(parentKey) != null;
        }
        return false;
    }

    @Transactional
    public InvalidationJobPO enqueueTrustedRewardInvalidation(
            String referenceType, String referenceId, String reason) {
        String normalizedType = IncentiveTypes.requireText(
                IncentiveTypes.upper(referenceType), 48, "referenceType");
        String normalizedId = IncentiveTypes.requireText(referenceId, 128, "referenceId");
        String normalizedReason = IncentiveTypes.requireReason(reason);
        String jobKey = sha256(normalizedType + "|" + normalizedId);
        InvalidationJobPO existing = mapper.selectInvalidationJobByKey(jobKey);
        if (existing != null) {
            requireEquivalentInvalidationJob(existing, normalizedType, normalizedId);
            mapper.rejectPendingInboxByInvalidation(normalizedType, normalizedId);
            return existing;
        }
        InvalidationJobPO job = new InvalidationJobPO();
        job.setId(idGenerator.nextId());
        job.setJobKey(jobKey);
        job.setReferenceType(normalizedType);
        job.setReferenceId(normalizedId);
        job.setInvalidationReason(normalizedReason);
        try {
            if (mapper.insertInvalidationJob(job) == 0) {
                existing = mapper.selectInvalidationJobByKey(jobKey);
                requireEquivalentInvalidationJob(existing, normalizedType, normalizedId);
                mapper.rejectPendingInboxByInvalidation(normalizedType, normalizedId);
                return existing;
            }
        } catch (DuplicateKeyException ignored) {
            existing = mapper.selectInvalidationJobByKey(jobKey);
            requireEquivalentInvalidationJob(existing, normalizedType, normalizedId);
            mapper.rejectPendingInboxByInvalidation(normalizedType, normalizedId);
            return existing;
        }
        mapper.rejectPendingInboxByInvalidation(normalizedType, normalizedId);
        return mapper.selectInvalidationJobByKey(jobKey);
    }

    @Transactional
    public boolean processInvalidationJobInline(Long jobId) {
        InvalidationJobPO job = mapper.lockInvalidationJob(jobId);
        if (job == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if ("COMPLETED".equals(job.getJobStatus())) {
            return true;
        }
        if (mapper.claimInvalidationJob(jobId, inlineInvalidationOwner, INVALIDATION_LEASE_SECONDS) != 1) {
            return false;
        }
        job.setJobStatus("RUNNING");
        job.setLeaseOwner(inlineInvalidationOwner);
        return processClaimedInvalidationJobLocked(
                job, inlineInvalidationOwner, INVALIDATION_MAX_BATCHES_PER_RUN);
    }

    @Transactional
    public List<Long> claimPendingInvalidationJobs(int requestedLimit, String owner) {
        String normalizedOwner = IncentiveTypes.requireText(owner, 64, "leaseOwner");
        int limit = Math.min(Math.max(1, requestedLimit), 20);
        List<Long> candidates = mapper.selectInvalidationJobsForClaim(limit);
        List<Long> claimed = new ArrayList<>();
        for (Long id : candidates) {
            if (mapper.claimInvalidationJob(id, normalizedOwner, INVALIDATION_LEASE_SECONDS) == 1) {
                claimed.add(id);
            }
        }
        return claimed;
    }

    @Transactional
    public boolean processClaimedInvalidationJob(Long jobId, String owner) {
        String normalizedOwner = IncentiveTypes.requireText(owner, 64, "leaseOwner");
        InvalidationJobPO job = mapper.lockInvalidationJob(jobId);
        if (job == null || !"RUNNING".equals(job.getJobStatus())
                || !Objects.equals(job.getLeaseOwner(), normalizedOwner)) {
            return false;
        }
        return processClaimedInvalidationJobLocked(
                job, normalizedOwner, INVALIDATION_MAX_BATCHES_PER_RUN);
    }

    @Transactional
    public void releaseInvalidationJobAfterFailure(Long jobId, String owner, String error) {
        mapper.releaseInvalidationJob(jobId,
                IncentiveTypes.requireText(owner, 64, "leaseOwner"),
                IncentiveTypes.clean(error, 500));
    }

    private boolean processClaimedInvalidationJobLocked(
            InvalidationJobPO job, String owner, int maxBatches) {
        long cursor = job.getCursorValue() == null ? 0 : job.getCursorValue();
        int processed = 0;
        for (int batchNo = 0; batchNo < maxBatches; batchNo++) {
            InvalidationBatch batch = invalidateTrustedRewardInternal(
                    job.getReferenceType(), job.getReferenceId(), cursor,
                    INVALIDATION_BATCH_LIMIT, job.getInvalidationReason(), null);
            processed = Math.addExact(processed, batch.reversals().size());
            cursor = batch.nextCursor();
            if (batch.coverageComplete()) {
                if (mapper.completeInvalidationJob(job.getId(), owner, cursor, processed) != 1) {
                    throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                            "failed to complete invalidation job");
                }
                return true;
            }
        }
        if (mapper.advanceInvalidationJob(job.getId(), owner, cursor, processed) != 1) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                    "failed to advance invalidation job cursor");
        }
        return false;
    }

    private LedgerPO createReversalWithDebt(LedgerPO original, String rawKey,
                                            String referenceType, String referenceId,
                                            String reason, Long operatorUid) {
        LedgerPO existing = mapper.selectReversalByOriginal(original.getId());
        String expectedKey = operationKey("REVERSAL", rawKey);
        if (existing != null) {
            requireEquivalentReversal(existing, original, expectedKey);
            return existing;
        }
        AccountPO account = requireAccountById(original.getAccountId());
        existing = mapper.lockReversalByOriginal(original.getId());
        if (existing != null) {
            requireEquivalentReversal(existing, original, expectedKey);
            return existing;
        }
        long amount = Math.max(0, original.getDeltaTotal());
        long availableRecovery = Math.min(amount, account.getAvailableBalance());
        long blockingFrozen = Math.min(account.getFrozenBalance(),
                mapper.sumActiveBlockingFreezeAmount(account.getId()));
        long recoverableFrozen = Math.max(0, account.getFrozenBalance() - blockingFrozen);
        long frozenRecovery = Math.min(amount - availableRecovery, recoverableFrozen);
        long recovered = Math.addExact(availableRecovery, frozenRecovery);
        LedgerPO reversal = append(original.getUserId(), original.getAccountType(), original.getDomainCode(),
                "REVERSAL", -recovered, -availableRecovery, -frozenRecovery,
                rawKey, referenceType, referenceId,
                original.getRuleCode(), original.getRuleVersion(), original.getBatchId(),
                original.getId(), reason, operatorUid);
        long outstanding = amount - recovered;
        if (outstanding > 0) {
            RecoveryDebtPO debt = new RecoveryDebtPO();
            debt.setId(idGenerator.nextId());
            debt.setAccountId(original.getAccountId());
            debt.setUserId(original.getUserId());
            debt.setOriginalEntryId(original.getId());
            debt.setReversalEntryId(reversal.getId());
            debt.setOriginalAmount(amount);
            debt.setRecoveredAmount(recovered);
            debt.setOutstandingAmount(outstanding);
            debt.setDebtStatus("PENDING");
            debt.setActionReason(reason);
            try {
                mapper.insertRecoveryDebt(debt);
            } catch (DuplicateKeyException e) {
                RecoveryDebtPO concurrent = mapper.lockRecoveryDebtByOriginal(original.getId());
                if (concurrent == null || !Objects.equals(concurrent.getReversalEntryId(), reversal.getId())) {
                    throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                            "recovery debt already belongs to another reversal");
                }
            }
            refreshRecoveryState(original.getAccountId());
        }
        return reversal;
    }

    private void settleRecoveryDebt(LedgerPO positiveEntry, String reason) {
        if (positiveEntry == null || positiveEntry.getDeltaTotal() == null
                || positiveEntry.getDeltaTotal() <= 0) {
            return;
        }
        long remainingCredit = positiveEntry.getDeltaTotal();
        long frozenCreditRemaining = Math.max(0, positiveEntry.getDeltaFrozen());
        while (remainingCredit > 0) {
            List<RecoveryDebtPO> debts = mapper.lockPendingRecoveryDebts(
                    positiveEntry.getAccountId(), INVALIDATION_BATCH_LIMIT);
            if (debts.isEmpty()) {
                break;
            }
            for (RecoveryDebtPO debt : debts) {
                if (remainingCredit <= 0) {
                    break;
                }
                long amount = Math.min(remainingCredit, debt.getOutstandingAmount());
                AccountPO account = requireAccountById(positiveEntry.getAccountId());
                long frozenTake = Math.min(amount,
                        Math.min(frozenCreditRemaining, account.getFrozenBalance()));
                long availableTake = amount - frozenTake;
                if (availableTake > account.getAvailableBalance()) {
                    throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                            "recovery debt account projection is inconsistent");
                }
                String recoveryKey = "DEBT:" + positiveEntry.getId() + ":" + debt.getId();
                LedgerPO existingRecovery = mapper.selectLedgerByIdempotency(
                        operationKey("DEBT_RECOVERY", recoveryKey));
                append(positiveEntry.getUserId(), positiveEntry.getAccountType(), positiveEntry.getDomainCode(),
                        "DEBT_RECOVERY", -amount, -availableTake, -frozenTake,
                        recoveryKey,
                        "RECOVERY_DEBT", String.valueOf(debt.getId()), null, null, null, null,
                        reason, null);
                if (existingRecovery == null && mapper.recoverDebt(debt.getId(), amount) != 1) {
                    throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                            "failed to apply recovery debt offset");
                }
                remainingCredit -= amount;
                frozenCreditRemaining -= frozenTake;
            }
        }
        if (frozenCreditRemaining > 0
                && mapper.countPendingRecoveryDebts(positiveEntry.getAccountId()) == 0
                && mapper.countActiveBlockingFreezes(positiveEntry.getAccountId()) == 0) {
            AccountPO account = requireAccountById(positiveEntry.getAccountId());
            long release = Math.min(frozenCreditRemaining, account.getFrozenBalance());
            if (release > 0) {
                append(positiveEntry.getUserId(), positiveEntry.getAccountType(), positiveEntry.getDomainCode(),
                        "RECOVERY_RELEASE", 0, release, -release,
                        "RECOVERY_RELEASE:" + positiveEntry.getId(),
                        "RECOVERY_SOURCE", String.valueOf(positiveEntry.getId()),
                        null, null, null, null,
                        "Release reward remainder after recovery debt was cleared", null);
            }
        }
        refreshRecoveryState(positiveEntry.getAccountId());
    }

    private void refreshRecoveryState(Long accountId) {
        long debt = mapper.sumPendingRecoveryDebt(accountId);
        String status = debt > 0
                ? "RECOVERY_DUE"
                : mapper.countActiveBlockingFreezes(accountId) > 0 ? "FROZEN" : "ACTIVE";
        if (mapper.updateAccountRecoveryState(accountId, debt, status) != 1) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                    "failed to update recovery debt projection");
        }
    }

    private TrustedRewardInvalidationResultDTO invalidationResult(
            String referenceType, String referenceId, long cursor, InvalidationBatch batch) {
        return TrustedRewardInvalidationResultDTO.builder()
                .referenceType(referenceType).referenceId(referenceId).cursor(cursor)
                .nextCursor(batch.coverageComplete() ? null : batch.nextCursor())
                .coverageComplete(batch.coverageComplete())
                .processedCount(batch.reversals().size())
                .reversals(batch.reversals().stream().map(this::toLedgerDto).toList())
                .build();
    }

    private RewardRulePO requireRule(String code, Integer version) {
        if (version == null || version <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        RewardRulePO rule = mapper.selectRewardRule(IncentiveTypes.requireText(code, 64, "ruleCode").toUpperCase(), version);
        if (rule == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rule;
    }

    private AccountPO requireAccountById(Long accountId) {
        if (accountId == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        AccountPO account = mapper.lockAccountById(accountId);
        if (account == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return account;
    }

    private String validJson(String json) {
        String clean = IncentiveTypes.clean(json, 4000);
        if (clean == null) {
            return null;
        }
        try {
            objectMapper.readTree(clean);
            return clean;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "payloadJson must be valid JSON");
        }
    }

    private AccountDTO toAccountDto(AccountPO po) {
        return AccountDTO.builder()
                .id(po.getId()).userId(po.getUserId()).accountType(po.getAccountType())
                .domainCode(po.getDomainCode()).totalBalance(po.getTotalBalance())
                .availableBalance(po.getAvailableBalance()).frozenBalance(po.getFrozenBalance())
                .recoveryDebt(po.getRecoveryDebt())
                .status(po.getAccountStatus()).version(po.getVersion()).updateTime(po.getUpdateTime())
                .build();
    }

    public LedgerEntryDTO toLedgerDto(LedgerPO po) {
        return LedgerEntryDTO.builder()
                .id(po.getId()).accountId(po.getAccountId()).userId(po.getUserId())
                .accountType(po.getAccountType()).domainCode(po.getDomainCode()).entryType(po.getEntryType())
                .deltaTotal(po.getDeltaTotal()).deltaAvailable(po.getDeltaAvailable()).deltaFrozen(po.getDeltaFrozen())
                .totalAfter(po.getTotalAfter()).availableAfter(po.getAvailableAfter()).frozenAfter(po.getFrozenAfter())
                .idempotencyKey(po.getIdempotencyKey()).referenceType(po.getReferenceType())
                .referenceId(po.getReferenceId()).ruleCode(po.getRuleCode()).ruleVersion(po.getRuleVersion())
                .batchId(po.getBatchId()).reversedEntryId(po.getReversedEntryId()).reason(po.getReason())
                .operatorUid(po.getOperatorUid()).createTime(po.getCreateTime())
                .build();
    }

    private ReconciliationDTO toReconciliationDto(ReconciliationRunPO po) {
        return ReconciliationDTO.builder()
                .runId(po.getId()).status(po.getRunStatus()).scannedCount(po.getScannedCount())
                .mismatchCount(po.getMismatchCount()).totalAbsoluteDifference(po.getTotalAbsoluteDifference())
                .cycleNo(po.getCycleNo()).cursorStartAccountId(po.getCursorStartAccountId())
                .cursorEndAccountId(po.getCursorEndAccountId()).nextCursorAccountId(po.getNextCursorAccountId())
                .coverageComplete(po.getCoverageComplete() != null && po.getCoverageComplete() == 1)
                .operatorUid(po.getOperatorUid()).reason(po.getActionReason())
                .createTime(po.getCreateTime()).finishTime(po.getFinishTime())
                .build();
    }

    private void requireAdmin(Long uid) {
        adminPermissionService.requireAdmin(uid);
    }

    private static void requireUser(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static int safePage(Integer page) {
        return page == null || page <= 0 ? 1 : Math.min(page, 1000);
    }

    private static long nonNegative(Long value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    /**
     * Daily totals roll on the DATABASE day (incrementRewardGuard writes
     * CURRENT_DATE), so the read side must compare against the same clock.
     * The JVM clock is only a fallback when the projection row did not report
     * the DB day (audit item 5.7: JVM/DB timezone skew over- or under-counted
     * awards near midnight).
     */
    static long dailyAwardedFor(Map<String, Object> guard) {
        LocalDate counterDate = guardDate(guard.get("counterDate"), LocalDate.MIN);
        LocalDate dbToday = guardDate(guard.get("dbToday"), null);
        LocalDate today = dbToday != null ? dbToday : LocalDate.now();
        return today.equals(counterDate) ? number(guard.get("dailyAwarded")) : 0;
    }

    private static LocalDate guardDate(Object value, LocalDate fallback) {
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return value instanceof LocalDate date ? date : fallback;
    }

    private static int reconciliationLimit(Integer requestedLimit) {
        return Math.min(IncentiveTypes.safeLimit(requestedLimit) * 10, RECONCILIATION_LIMIT);
    }

    private static LocalDateTime rewardInboxCutoff() {
        return LocalDateTime.now().minusMinutes(
                IncentiveProjectionReconciliationFacade.REWARD_INBOX_SLA_MINUTES);
    }

    private static long reconciliationDifference(Map<String, Object> row) {
        long projectedTotal = number(row.get("projectedTotal"));
        long ledgerTotal = number(row.get("ledgerTotal"));
        long projectedAvailable = number(row.get("projectedAvailable"));
        long ledgerAvailable = number(row.get("ledgerAvailable"));
        long projectedFrozen = number(row.get("projectedFrozen"));
        long ledgerFrozen = number(row.get("ledgerFrozen"));
        return Math.abs(projectedTotal - ledgerTotal)
                + Math.abs(projectedAvailable - ledgerAvailable)
                + Math.abs(projectedFrozen - ledgerFrozen);
    }

    private static boolean exceedsCap(long current, long increment, long cap) {
        return current > cap || increment > cap - current;
    }

    private String normalizeEventDomain(String rawEventDomain, String accountDomain, RewardRulePO rule) {
        String eventDomain = IncentiveTypes.upper(rawEventDomain);
        if (eventDomain == null && "EVENT_DOMAIN".equals(rule.getDomainCode())) {
            eventDomain = accountDomain;
        }
        if (eventDomain != null && IncentiveTypes.GLOBAL_DOMAIN.equals(eventDomain)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "eventDomainCode must identify a content domain");
        }
        if ("EVENT_DOMAIN".equals(rule.getDomainCode()) && !Objects.equals(accountDomain, eventDomain)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "event domain does not match reputation account");
        }
        return eventDomain;
    }

    private RewardInboxPO requireEquivalentInbox(RewardInboxPO existing, String expectedFingerprint) {
        if (existing == null || !Objects.equals(existing.getRequestFingerprint(), expectedFingerprint)) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "reward stableKey was already used by a different reward request");
        }
        return existing;
    }

    private static void requireEquivalentBatch(RewardBatchPO existing, String ruleCode, Integer ruleVersion) {
        if (existing == null
                || !Objects.equals(existing.getRuleCode(), ruleCode)
                || !Objects.equals(existing.getRuleVersion(), ruleVersion)) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "reward batch key was already used for another rule");
        }
    }

    private static void requireInboxUpdated(int updated) {
        if (updated != 1) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                    "reward inbox projection update failed");
        }
    }

    private static String rewardInboxRequestFingerprint(
            String eventType, Long recipientUid, String domain, String eventDomain,
            String sourceType, String sourceId, String parentType, String parentId,
            String ruleCode, Integer ruleVersion, String payloadJson) {
        return sha256(String.join("|",
                String.valueOf(eventType), String.valueOf(recipientUid),
                String.valueOf(domain), String.valueOf(eventDomain),
                String.valueOf(sourceType), String.valueOf(sourceId),
                String.valueOf(parentType), String.valueOf(parentId),
                String.valueOf(ruleCode), String.valueOf(ruleVersion),
                String.valueOf(payloadJson)));
    }

    private static String operationKey(String entryType, String rawKey) {
        String raw = "LEDGER:" + entryType + ":" + rawKey;
        if (raw.length() <= 96) {
            return raw;
        }
        return "LEDGER:" + entryType + ":" + sha256(raw);
    }

    private static String ledgerRequestFingerprint(Long userId, String accountType, String domainCode,
                                                   String entryType, long deltaTotal, long deltaAvailable,
                                                   long deltaFrozen, String referenceType, String referenceId,
                                                   String ruleCode, Integer ruleVersion, Long batchId,
                                                   Long reversedEntryId, Long operatorUid) {
        return sha256(String.join("|",
                String.valueOf(userId), String.valueOf(accountType), String.valueOf(domainCode),
                String.valueOf(entryType), String.valueOf(deltaTotal), String.valueOf(deltaAvailable),
                String.valueOf(deltaFrozen), String.valueOf(referenceType), String.valueOf(referenceId),
                String.valueOf(ruleCode), String.valueOf(ruleVersion), String.valueOf(batchId),
                String.valueOf(reversedEntryId), String.valueOf(operatorUid)));
    }

    private static void requireEquivalentLedger(LedgerPO existing, String expectedKey,
                                                String expectedFingerprint) {
        if (existing == null
                || !Objects.equals(existing.getIdempotencyKey(), expectedKey)
                || !Objects.equals(existing.getRequestFingerprint(), expectedFingerprint)) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "ledger idempotency key was already used by a different operation");
        }
    }

    private static void requireEquivalentReversal(
            LedgerPO existing, LedgerPO original, String expectedKey) {
        if (existing == null
                || !Objects.equals(existing.getIdempotencyKey(), expectedKey)
                || !Objects.equals(existing.getReversedEntryId(), original.getId())
                || !Objects.equals(existing.getUserId(), original.getUserId())
                || !Objects.equals(existing.getAccountId(), original.getAccountId())
                || !"REVERSAL".equals(existing.getEntryType())) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "reversal idempotency key was already used by another target");
        }
    }

    private static void requireEquivalentInvalidationJob(
            InvalidationJobPO job, String referenceType, String referenceId) {
        if (job == null
                || !Objects.equals(job.getReferenceType(), referenceType)
                || !Objects.equals(job.getReferenceId(), referenceId)) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "invalidation job key belongs to another trusted source");
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static <T> PageResult<T> page(List<T> items, long total, int page, int size) {
        boolean hasMore = (long) page * size < total;
        return PageResult.<T>builder()
                .items(items).total(total).hasMore(hasMore)
                .nextCursor(hasMore ? String.valueOf(page + 1) : null)
                .build();
    }

    private record InvalidationBatch(List<LedgerPO> reversals, long nextCursor,
                                     boolean coverageComplete) {
    }

    private enum RewardProcessResult {
        APPLIED, REJECTED
    }
}

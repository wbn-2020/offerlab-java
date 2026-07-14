package com.offerlab.community.incentive.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class IncentivePersistence {
    private IncentivePersistence() {
    }

    @Data
    @TableName("t_incentive_account")
    public static class AccountPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private Long userId;
        private String accountType;
        private String domainCode;
        private Long totalBalance;
        private Long availableBalance;
        private Long frozenBalance;
        private Long recoveryDebt;
        private String accountStatus;
        private Long version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @TableName("t_incentive_ledger")
    public static class LedgerPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private Long accountId;
        private Long userId;
        private String accountType;
        private String domainCode;
        private String entryType;
        private Long deltaTotal;
        private Long deltaAvailable;
        private Long deltaFrozen;
        private Long totalAfter;
        private Long availableAfter;
        private Long frozenAfter;
        private String idempotencyKey;
        private String requestFingerprint;
        private String referenceType;
        private String referenceId;
        private String ruleCode;
        private Integer ruleVersion;
        private Long batchId;
        private Long reversedEntryId;
        private String reason;
        private Long operatorUid;
        private String auditJson;
        private LocalDateTime createTime;
    }

    @Data
    @TableName("t_incentive_reward_rule")
    public static class RewardRulePO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String ruleCode;
        private Integer ruleVersion;
        private String accountType;
        private String domainCode;
        private Long rewardAmount;
        private Long dailyUserCap;
        private Long lifetimeUserCap;
        private Integer enabled;
        private LocalDateTime validFrom;
        private LocalDateTime validUntil;
        private Long createdBy;
        private String changeReason;
        private LocalDateTime createTime;
    }

    @Data
    @TableName("t_incentive_reward_inbox")
    public static class RewardInboxPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String stableKey;
        private String eventType;
        private String requestFingerprint;
        private Long recipientUid;
        private String domainCode;
        private String eventDomainCode;
        private String sourceReferenceType;
        private String sourceReferenceId;
        private String parentReferenceType;
        private String parentReferenceId;
        private String ruleCode;
        private Integer ruleVersion;
        private String payloadJson;
        private Long receivedBy;
        private String receiveReason;
        private String inboxStatus;
        private Long batchId;
        private Integer attemptCount;
        private Long processedEntryId;
        private String errorMessage;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private LocalDateTime processedTime;
    }

    @Data
    @TableName("t_incentive_invalidation_job")
    public static class InvalidationJobPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String jobKey;
        private String referenceType;
        private String referenceId;
        private String invalidationReason;
        private String jobStatus;
        private Long cursorValue;
        private Integer processedCount;
        private Integer attemptCount;
        private String leaseOwner;
        private LocalDateTime leaseUntil;
        private String lastError;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private LocalDateTime completedTime;
    }

    @Data
    @TableName("t_incentive_recovery_debt")
    public static class RecoveryDebtPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private Long accountId;
        private Long userId;
        private Long originalEntryId;
        private Long reversalEntryId;
        private Long originalAmount;
        private Long recoveredAmount;
        private Long outstandingAmount;
        private String debtStatus;
        private String actionReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private LocalDateTime settledTime;
    }

    @Data
    @TableName("t_incentive_reward_batch")
    public static class RewardBatchPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String batchKey;
        private String ruleCode;
        private Integer ruleVersion;
        private String batchStatus;
        private Integer requestedCount;
        private Integer appliedCount;
        private Integer rejectedCount;
        private Integer failedCount;
        private Long createdBy;
        private String actionReason;
        private LocalDateTime createTime;
        private LocalDateTime finishTime;
    }

    @Data
    @TableName("t_incentive_freeze_record")
    public static class FreezePO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private Long accountId;
        private Long userId;
        private Long freezeAmount;
        private String freezeStatus;
        private Integer blocksSpending;
        private Long freezeEntryId;
        private Long releaseEntryId;
        private Long operatorUid;
        private String actionReason;
        private String releaseReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @TableName("t_incentive_reconciliation_run")
    public static class ReconciliationRunPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String runStatus;
        private Integer scannedCount;
        private Integer mismatchCount;
        private Long totalAbsoluteDifference;
        private Long cycleNo;
        private Long cursorStartAccountId;
        private Long cursorEndAccountId;
        private Long nextCursorAccountId;
        private Integer coverageComplete;
        private Long operatorUid;
        private String actionReason;
        private LocalDateTime createTime;
        private LocalDateTime finishTime;
    }

    @Data
    @TableName("t_incentive_appeal")
    public static class IncentiveAppealPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private Long appellantUid;
        private String targetType;
        private Long targetId;
        private Long relatedLedgerId;
        private Long relatedRecoveryDebtId;
        private Long originalOperatorUid;
        private String appealReason;
        private String appealStatus;
        private Long reviewerUid;
        private String reviewReason;
        private Long restoreEntryId;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @TableName("t_incentive_risk_finding")
    public static class RiskFindingPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String stableKey;
        private String findingType;
        private String subjectType;
        private String subjectId;
        private String domainCode;
        private String severity;
        private String evaluationStatus;
        private String findingStatus;
        private Long metricValue;
        private Long thresholdValue;
        private String evidenceJson;
        private Long scanRunId;
        private Long resolvedBy;
        private String resolutionReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @TableName("t_virtual_benefit_catalog")
    public static class BenefitPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String benefitCode;
        private String benefitName;
        private String description;
        private String category;
        private String deliveryType;
        private Long pointCost;
        private Integer totalStock;
        private Integer availableStock;
        private Integer enabled;
        private Long createdBy;
        private Long updatedBy;
        private String actionReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @TableName("t_virtual_benefit_order")
    public static class BenefitOrderPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String orderNo;
        private String idempotencyKey;
        private String requestFingerprint;
        private Long userId;
        private Long benefitId;
        private String benefitCode;
        private String benefitName;
        private Integer quantity;
        private Long unitPointCost;
        private Long totalPointCost;
        private String orderStatus;
        private Long reserveEntryId;
        private Long refundEntryId;
        private String deliveryReference;
        private Long operatorUid;
        private String actionReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private LocalDateTime deliveredTime;
        private LocalDateTime cancelledTime;
        private LocalDateTime refundedTime;
    }

    @Data
    @TableName("t_virtual_benefit_entitlement")
    public static class BenefitEntitlementPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private Long orderId;
        private Long userId;
        private String benefitCode;
        private String entitlementType;
        private String entitlementKey;
        private Long quantityTotal;
        private Long quantityRemaining;
        private String entitlementStatus;
        private Integer reversible;
        private String payloadJson;
        private Long grantedBy;
        private String grantReason;
        private Long revokedBy;
        private String revokeReason;
        private LocalDateTime grantedAt;
        private LocalDateTime revokedAt;
        private LocalDateTime updateTime;
    }

    @Data
    @TableName("t_virtual_benefit_entitlement_usage")
    public static class BenefitEntitlementUsagePO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private Long entitlementId;
        private Long userId;
        private Long amount;
        private String idempotencyKey;
        private String requestFingerprint;
        private String actionReason;
        private LocalDateTime createTime;
    }

    @Data
    @TableName("t_thank_ticket_daily")
    public static class ThankTicketPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private Long userId;
        private LocalDate ticketDate;
        private Integer grantedCount;
        private Integer usedCount;
        private Long version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @TableName("t_quota_bounty")
    public static class BountyPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String title;
        private String description;
        private String domainCode;
        private String requestType;
        private String riskCategory;
        private Integer quota;
        private Integer awardedCount;
        private Long pointReward;
        private Long totalBudget;
        private String budgetPeriod;
        private Long reservedBudget;
        private Long consumedBudget;
        private String bountyStatus;
        private Long createdBy;
        private Long updatedBy;
        private String actionReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @TableName("t_quota_bounty_submission")
    public static class BountySubmissionPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private Long bountyId;
        private Long applicantUid;
        private Long publicPostId;
        private String requestType;
        private String riskCategory;
        private String evidence;
        private String submissionStatus;
        private Long reviewerUid;
        private String reviewReason;
        private Long rewardEntryId;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @TableName("t_quota_bounty_appeal")
    public static class BountyAppealPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private Long submissionId;
        private Long applicantUid;
        private String originalStatus;
        private Long originalReviewerUid;
        private String appealReason;
        private String appealStatus;
        private Long reviewerUid;
        private String reviewReason;
        private Long compensationEntryId;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @TableName("t_community_role_definition")
    public static class RoleDefinitionPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String roleCode;
        private String roleName;
        private String description;
        private String domainCode;
        private Integer minAccountAgeDays;
        private Long minDomainReputation;
        private Integer minActivityCount;
        private Integer maxViolationCount;
        private Integer minCurationAccuracyBps;
        private Integer requiresNoRiskFreeze;
        private Integer enabled;
        private Long createdBy;
        private Long updatedBy;
        private String actionReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @TableName("t_community_role_application")
    public static class RoleApplicationPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private Long applicantUid;
        private String roleCode;
        private String domainCode;
        private String statement;
        private String eligibilitySnapshotJson;
        private String applicationStatus;
        private Long reviewerUid;
        private String reviewReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @TableName("t_community_role_grant")
    public static class RoleGrantPO {
        @TableId(type = IdType.INPUT)
        private Long id;
        private Long userId;
        private String roleCode;
        private String domainCode;
        private String grantStatus;
        private Long applicationId;
        private Long grantedBy;
        private String grantReason;
        private Long actionBy;
        private String actionReason;
        private LocalDateTime grantedAt;
        private LocalDateTime expiresAt;
        private LocalDateTime updateTime;
    }
}

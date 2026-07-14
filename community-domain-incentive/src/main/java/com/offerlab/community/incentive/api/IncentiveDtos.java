package com.offerlab.community.incentive.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class IncentiveDtos {
    private IncentiveDtos() {
    }

    @Data @Builder
    public static class AccountDTO {
        private Long id;
        private Long userId;
        private String accountType;
        private String domainCode;
        private Long totalBalance;
        private Long availableBalance;
        private Long frozenBalance;
        private Long recoveryDebt;
        private String status;
        private Long version;
        private LocalDateTime updateTime;
    }

    @Data @Builder
    public static class LedgerEntryDTO {
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
        private String referenceType;
        private String referenceId;
        private String ruleCode;
        private Integer ruleVersion;
        private Long batchId;
        private Long reversedEntryId;
        private String reason;
        private Long operatorUid;
        private LocalDateTime createTime;
    }

    @Data
    public static class RewardInboxCmd {
        @NotBlank @Size(max = 96) private String stableKey;
        @NotBlank @Size(max = 64) private String eventType;
        @NotNull private Long recipientUid;
        @Size(max = 32) private String domainCode;
        @Size(max = 32) private String eventDomainCode;
        @Size(max = 48) private String sourceReferenceType;
        @Size(max = 128) private String sourceReferenceId;
        @Size(max = 48) private String parentReferenceType;
        @Size(max = 128) private String parentReferenceId;
        @NotBlank @Size(max = 64) private String ruleCode;
        @NotNull @Min(1) private Integer ruleVersion;
        @Size(max = 4000) private String payloadJson;
        @NotBlank @Size(max = 500) private String reason;
    }

    @Data
    public static class RewardRuleCmd {
        @NotBlank @Size(max = 64) private String ruleCode;
        @NotNull @Min(1) private Integer ruleVersion;
        @NotBlank @Size(max = 16) private String accountType;
        @Size(max = 32) private String domainCode;
        @NotNull private Long amount;
        @Min(0) private Long dailyUserCap;
        @Min(0) private Long lifetimeUserCap;
        private Boolean enabled;
        private LocalDateTime validFrom;
        private LocalDateTime validUntil;
        @NotBlank @Size(max = 500) private String reason;
    }

    @Data
    public static class BatchCmd {
        @NotBlank @Size(max = 96) private String batchKey;
        @NotBlank @Size(max = 64) private String ruleCode;
        @NotNull @Min(1) private Integer ruleVersion;
        @NotBlank @Size(max = 500) private String reason;
        @Min(1) @Max(100) private Integer limit;
    }

    @Data
    public static class FreezeCmd {
        @NotBlank @Size(max = 16) private String accountType;
        @Size(max = 32) private String domainCode;
        @NotNull @Min(1) private Long amount;
        private Boolean blockSpending;
        @NotBlank @Size(max = 96) private String idempotencyKey;
        @NotBlank @Size(max = 500) private String reason;
    }

    @Data
    public static class ReversalCmd {
        @NotBlank @Size(max = 96) private String idempotencyKey;
        @NotBlank @Size(max = 500) private String reason;
    }

    @Data
    public static class IncentiveAppealCmd {
        @NotBlank @Size(max = 16) private String targetType;
        @NotNull private Long targetId;
        @NotBlank @Size(max = 1000) private String reason;
    }

    @Data
    public static class AppealReviewCmd {
        @NotNull private Boolean approved;
        @NotBlank @Size(max = 500) private String reason;
    }

    @Data @Builder
    public static class IncentiveAppealDTO {
        private Long id;
        private Long appellantUid;
        private String targetType;
        private Long targetId;
        private Long relatedLedgerId;
        private Long relatedRecoveryDebtId;
        private String status;
        private String appealReason;
        private Long reviewerUid;
        private String reviewReason;
        private Long restoreEntryId;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class RiskScanCmd {
        @NotBlank @Size(max = 48) private String scanType;
        @Min(1) @Max(500) private Integer limit;
        @NotBlank @Size(max = 500) private String reason;
    }

    @Data @Builder
    public static class RiskFindingDTO {
        private Long id;
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
    public static class RiskFindingActionCmd {
        @NotBlank @Size(max = 500) private String reason;
    }

    @Data @Builder
    public static class RiskScanResultDTO {
        private Long runId;
        private String scanType;
        private Long cycleNo;
        private Long cursorStart;
        private Long cursorEnd;
        private Long nextCursor;
        private Boolean coverageComplete;
        private Integer scannedCount;
        private Integer findingCount;
    }

    @Data
    public static class TrustedRewardInvalidationCmd {
        @NotBlank @Size(max = 48) private String referenceType;
        @NotBlank @Size(max = 128) private String referenceId;
        @NotBlank @Size(max = 500) private String reason;
        @Min(0) private Long cursor;
        @Min(1) @Max(100) private Integer limit;
    }

    @Data @Builder
    public static class TrustedRewardInvalidationResultDTO {
        private String referenceType;
        private String referenceId;
        private Long cursor;
        private Long nextCursor;
        private Boolean coverageComplete;
        private Integer processedCount;
        private List<LedgerEntryDTO> reversals;
    }

    @Data @Builder
    public static class ReconciliationDTO {
        private Long runId;
        private String status;
        private Integer scannedCount;
        private Integer mismatchCount;
        private Long totalAbsoluteDifference;
        private Long cycleNo;
        private Long cursorStartAccountId;
        private Long cursorEndAccountId;
        private Long nextCursorAccountId;
        private Boolean coverageComplete;
        private Long operatorUid;
        private String reason;
        private LocalDateTime createTime;
        private LocalDateTime finishTime;
    }

    @Data
    public static class BenefitCatalogCmd {
        @NotBlank @Size(max = 64) private String benefitCode;
        @NotBlank @Size(max = 128) private String name;
        @Size(max = 1000) private String description;
        @NotBlank @Size(max = 32) private String category;
        @NotBlank @Size(max = 32) private String deliveryType;
        @NotNull @Min(1) private Long pointCost;
        @Min(0) private Integer totalStock;
        private Boolean enabled;
        @NotBlank @Size(max = 500) private String reason;
    }

    @Data @Builder
    public static class BenefitDTO {
        private Long id;
        private String benefitCode;
        private String name;
        private String description;
        private String category;
        private String deliveryType;
        private Long pointCost;
        private Integer totalStock;
        private Integer availableStock;
        private Boolean enabled;
        private LocalDateTime updateTime;
    }

    @Data
    public static class BenefitOrderCmd {
        @NotNull private Long benefitId;
        @Min(1) @Max(10) private Integer quantity;
        @NotBlank @Size(max = 96) private String idempotencyKey;
    }

    @Data
    public static class OrderActionCmd {
        @NotBlank @Size(max = 500) private String reason;
        @Size(max = 256) private String deliveryReference;
    }

    @Data @Builder
    public static class BenefitOrderDTO {
        private Long id;
        private String orderNo;
        private Long userId;
        private Long benefitId;
        private String benefitCode;
        private String benefitName;
        private Integer quantity;
        private Long unitPointCost;
        private Long totalPointCost;
        private String status;
        private String deliveryReference;
        private String actionReason;
        private Long operatorUid;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data @Builder
    public static class BenefitEntitlementDTO {
        private Long id;
        private Long orderId;
        private Long userId;
        private String benefitCode;
        private String entitlementType;
        private String entitlementKey;
        private Long quantityTotal;
        private Long quantityRemaining;
        private String status;
        private Boolean reversible;
        private String payloadJson;
        private LocalDateTime grantedAt;
        private LocalDateTime revokedAt;
        private LocalDateTime updateTime;
    }

    @Data
    public static class EntitlementConsumeCmd {
        @NotNull @Min(1) private Long amount;
        @NotBlank @Size(max = 96) private String idempotencyKey;
        @NotBlank @Size(max = 500) private String reason;
    }

    @Data
    public static class ThankCmd {
        @NotNull private Long receiverUid;
        @NotBlank @Size(max = 32) private String targetType;
        @NotBlank @Size(max = 64) private String targetId;
        @Size(max = 200) private String note;
    }

    @Data @Builder
    public static class ThankTicketDTO {
        private LocalDate ticketDate;
        private Integer grantedCount;
        private Integer usedCount;
        private Integer remainingCount;
        private Boolean purchasable;
        private Boolean transferable;
        private Boolean creditsReceiverBalance;
    }

    @Data @Builder
    public static class ThankWorkspaceDTO {
        private ThankTicketDTO ticket;
        private com.offerlab.community.common.result.PageResult<java.util.Map<String, Object>> sent;
        private com.offerlab.community.common.result.PageResult<java.util.Map<String, Object>> received;
        private Long receivedTotal;
        private java.util.Map<String, Long> receivedByTargetType;
        private java.util.Map<String, Long> receivedByDomain;
    }

    @Data
    public static class BountyCmd {
        @NotBlank @Size(max = 160) private String title;
        @NotBlank @Size(max = 2000) private String description;
        @NotBlank @Size(max = 32) private String domainCode;
        @NotBlank @Size(max = 32) private String requestType;
        @NotBlank @Size(max = 16) private String riskCategory;
        @NotNull @Min(1) @Max(500) private Integer quota;
        @NotNull @Min(1) private Long pointReward;
        @NotBlank @Size(max = 500) private String reason;
    }

    @Data
    public static class BountyStatusCmd {
        @NotBlank @Size(max = 16) private String status;
        @NotBlank @Size(max = 500) private String reason;
    }

    @Data
    public static class BountySubmissionCmd {
        @NotNull private Long publicPostId;
        @NotBlank @Size(max = 32) private String requestType;
        @NotBlank @Size(max = 16) private String riskCategory;
        @NotBlank @Size(max = 2000) private String evidence;
    }

    @Data
    public static class ReviewCmd {
        @NotNull private Boolean approved;
        @NotBlank @Size(max = 500) private String reason;
        private LocalDateTime expiresAt;
    }

    @Data @Builder
    public static class BountyDTO {
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
        private String status;
        private Long createdBy;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data @Builder
    public static class BountySubmissionDTO {
        private Long id;
        private Long bountyId;
        private Long applicantUid;
        private Long publicPostId;
        private String requestType;
        private String riskCategory;
        private String evidence;
        private String status;
        private Long reviewerUid;
        private String reviewReason;
        private Long rewardEntryId;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class BountyAppealCmd {
        @NotBlank @Size(max = 1000) private String reason;
    }

    @Data @Builder
    public static class BountyAppealDTO {
        private Long id;
        private Long submissionId;
        private Long applicantUid;
        private String originalStatus;
        private Long originalReviewerUid;
        private String appealReason;
        private String status;
        private Long reviewerUid;
        private String reviewReason;
        private Long compensationEntryId;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data @Builder
    public static class BountyWorkspaceDTO {
        private com.offerlab.community.common.result.PageResult<BountyDTO> available;
        private com.offerlab.community.common.result.PageResult<BountySubmissionDTO> submissions;
    }

    @Data
    public static class RoleDefinitionCmd {
        @NotBlank @Size(max = 64) private String roleCode;
        @NotBlank @Size(max = 128) private String roleName;
        @Size(max = 1000) private String description;
        @NotBlank @Size(max = 32) private String domainCode;
        @Min(0) private Integer minAccountAgeDays;
        @Min(0) private Long minDomainReputation;
        @Min(0) private Integer minActivityCount;
        @Min(0) private Integer maxViolationCount;
        @Min(0) @Max(10000) private Integer minCurationAccuracyBps;
        private Boolean requiresNoRiskFreeze;
        private Boolean enabled;
        @NotBlank @Size(max = 500) private String reason;
    }

    @Data
    public static class RoleMetricCmd {
        @NotBlank @Size(max = 32) private String domainCode;
        @Min(0) private Integer curationCorrectCount;
        @Min(0) private Integer curationReviewedCount;
        @NotBlank @Size(max = 500) private String reason;
    }

    @Data @Builder
    public static class RoleDefinitionDTO {
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
        private Boolean requiresNoRiskFreeze;
        private Boolean enabled;
        private LocalDateTime updateTime;
    }

    @Data @Builder
    public static class RoleEligibilityDTO {
        private String roleCode;
        private String domainCode;
        private Boolean eligible;
        private Integer accountAgeDays;
        private Long domainReputation;
        private Integer activityCount;
        private Integer violationCount;
        private Integer curationAccuracyBps;
        private Boolean riskFrozen;
        private List<String> failedChecks;
        private Boolean manualApprovalRequired;
        private Boolean automaticallyGrantsAuthority;
    }

    @Data
    public static class RoleApplicationCmd {
        @NotBlank @Size(max = 64) private String roleCode;
        @NotBlank @Size(max = 32) private String domainCode;
        @NotBlank @Size(max = 1000) private String statement;
    }

    @Data
    public static class RoleActionCmd {
        @NotBlank @Size(max = 500) private String reason;
        private LocalDateTime expiresAt;
    }

    @Data @Builder
    public static class RoleApplicationDTO {
        private Long id;
        private Long applicantUid;
        private String roleCode;
        private String domainCode;
        private String statement;
        private String eligibilitySnapshotJson;
        private String status;
        private Long reviewerUid;
        private String reviewReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data @Builder
    public static class RoleGrantDTO {
        private Long id;
        private Long userId;
        private String roleCode;
        private String domainCode;
        private String status;
        private Long grantedBy;
        private String grantReason;
        private Long actionBy;
        private String actionReason;
        private LocalDateTime grantedAt;
        private LocalDateTime expiresAt;
        private LocalDateTime updateTime;
    }

    @Data @Builder
    public static class RoleWorkspaceDTO {
        private List<RoleDefinitionDTO> definitions;
        private com.offerlab.community.common.result.PageResult<RoleApplicationDTO> applications;
        private com.offerlab.community.common.result.PageResult<RoleGrantDTO> grants;
    }
}

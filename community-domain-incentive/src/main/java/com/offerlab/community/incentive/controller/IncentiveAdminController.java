package com.offerlab.community.incentive.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.incentive.api.IncentiveDtos.BatchCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.AppealReviewCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.BenefitCatalogCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.BenefitDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BenefitOrderDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BountyCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.BountyDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BountyStatusCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.BountyAppealDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BountySubmissionDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.FreezeCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.LedgerEntryDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.OrderActionCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.ReconciliationDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.ReversalCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.ReviewCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RewardInboxCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RewardRuleCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.IncentiveAppealDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RiskFindingDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RiskFindingActionCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RiskScanCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RiskScanResultDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.TrustedRewardInvalidationCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.TrustedRewardInvalidationResultDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleActionCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleApplicationDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleDefinitionCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleDefinitionDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleGrantDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleMetricCmd;
import com.offerlab.community.incentive.application.AccountLedgerService;
import com.offerlab.community.incentive.application.BenefitService;
import com.offerlab.community.incentive.application.CommunityRoleService;
import com.offerlab.community.incentive.application.IncentiveGovernanceService;
import com.offerlab.community.incentive.application.ThankBountyService;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RewardBatchPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RewardInboxPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RewardRulePO;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incentives/admin")
@RequiredArgsConstructor
public class IncentiveAdminController {
    private final AccountLedgerService accountLedgerService;
    private final BenefitService benefitService;
    private final ThankBountyService thankBountyService;
    private final CommunityRoleService communityRoleService;
    private final IncentiveGovernanceService incentiveGovernanceService;

    @PostMapping("/rewards/inbox")
    @RateLimit(key = "'incentive:admin:reward-inbox:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<RewardInboxPO> receiveReward(@Valid @RequestBody RewardInboxCmd cmd) {
        return Result.ok(accountLedgerService.receiveReward(cmd, UserContext.require()));
    }

    @PostMapping("/rewards/rules")
    @RateLimit(key = "'incentive:admin:reward-rule:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<RewardRulePO> createRule(@Valid @RequestBody RewardRuleCmd cmd) {
        return Result.ok(accountLedgerService.createRule(cmd, UserContext.require()));
    }

    @PostMapping("/rewards/batches")
    @RateLimit(key = "'incentive:admin:reward-batch:' + #uid", rate = 5, per = 300, failOpen = false)
    public Result<RewardBatchPO> processBatch(@Valid @RequestBody BatchCmd cmd) {
        return Result.ok(accountLedgerService.processBatch(cmd, UserContext.require()));
    }

    @PostMapping("/accounts/{userId}/freeze")
    @RateLimit(key = "'incentive:admin:freeze:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<LedgerEntryDTO> freeze(@PathVariable Long userId, @Valid @RequestBody FreezeCmd cmd) {
        return Result.ok(accountLedgerService.freeze(userId, cmd, UserContext.require()));
    }

    @PostMapping("/freezes/{freezeId}/release")
    @RateLimit(key = "'incentive:admin:freeze-release:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<LedgerEntryDTO> releaseFreeze(@PathVariable Long freezeId,
                                                @Valid @RequestBody ReversalCmd cmd) {
        return Result.ok(accountLedgerService.releaseFreeze(freezeId, cmd, UserContext.require()));
    }

    @PostMapping("/ledger/{ledgerId}/reverse")
    @RateLimit(key = "'incentive:admin:ledger-reverse:' + #uid", rate = 5, per = 300, failOpen = false)
    public Result<LedgerEntryDTO> reverse(@PathVariable Long ledgerId, @Valid @RequestBody ReversalCmd cmd) {
        return Result.ok(accountLedgerService.reverse(ledgerId, cmd, UserContext.require()));
    }

    @PostMapping("/reconciliation")
    @RateLimit(key = "'incentive:admin:reconciliation:' + #uid", rate = 2, per = 600, failOpen = false)
    public Result<ReconciliationDTO> reconcile(@RequestParam(defaultValue = "100") Integer limit,
                                               @RequestParam String reason) {
        return Result.ok(accountLedgerService.reconcile(limit, reason, UserContext.require()));
    }

    @GetMapping("/reconciliation")
    public Result<List<ReconciliationDTO>> reconciliationRuns(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.ok(accountLedgerService.reconciliationRuns(limit, UserContext.require()));
    }

    @PostMapping("/rewards/invalidate")
    @RateLimit(key = "'incentive:admin:reward-invalidate:' + #uid", rate = 5, per = 300, failOpen = false)
    public Result<TrustedRewardInvalidationResultDTO> invalidateReward(
            @Valid @RequestBody TrustedRewardInvalidationCmd cmd) {
        return Result.ok(accountLedgerService.invalidateTrustedReward(cmd, UserContext.require()));
    }

    @GetMapping("/appeals")
    public Result<PageResult<IncentiveAppealDTO>> appealQueue(@RequestParam(required = false) String status,
                                                              @RequestParam(defaultValue = "1") Integer page,
                                                              @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(incentiveGovernanceService.appealQueue(
                status, page, size, UserContext.require()));
    }

    @PostMapping("/appeals/{appealId}/review")
    @RateLimit(key = "'incentive:admin:appeal-review:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<IncentiveAppealDTO> reviewAppeal(@PathVariable Long appealId,
                                                   @Valid @RequestBody AppealReviewCmd cmd) {
        return Result.ok(incentiveGovernanceService.reviewAppeal(appealId, cmd, UserContext.require()));
    }

    @PostMapping("/risk/scans")
    @RateLimit(key = "'incentive:admin:risk-scan:' + #uid", rate = 3, per = 600, failOpen = false)
    public Result<RiskScanResultDTO> scanRisk(@Valid @RequestBody RiskScanCmd cmd) {
        return Result.ok(incentiveGovernanceService.scanRisk(cmd, UserContext.require()));
    }

    @GetMapping("/risk/findings")
    public Result<PageResult<RiskFindingDTO>> riskFindings(@RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "1") Integer page,
                                                           @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(incentiveGovernanceService.findings(status, page, size, UserContext.require()));
    }

    @PostMapping("/risk/findings/{findingId}/resolve")
    @RateLimit(key = "'incentive:admin:risk-finding-resolve:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<RiskFindingDTO> resolveRiskFinding(
            @PathVariable Long findingId, @Valid @RequestBody RiskFindingActionCmd cmd) {
        return Result.ok(incentiveGovernanceService.resolveFinding(
                findingId, cmd, "RESOLVED", UserContext.require()));
    }

    @PostMapping("/risk/findings/{findingId}/ignore")
    @RateLimit(key = "'incentive:admin:risk-finding-ignore:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<RiskFindingDTO> ignoreRiskFinding(
            @PathVariable Long findingId, @Valid @RequestBody RiskFindingActionCmd cmd) {
        return Result.ok(incentiveGovernanceService.resolveFinding(
                findingId, cmd, "IGNORED", UserContext.require()));
    }

    @GetMapping("/benefits/catalog")
    public Result<PageResult<BenefitDTO>> adminCatalog(@RequestParam(defaultValue = "1") Integer page,
                                                        @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(benefitService.adminCatalog(page, size, UserContext.require()));
    }

    @PostMapping("/benefits/catalog")
    @RateLimit(key = "'incentive:admin:benefit-catalog:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<BenefitDTO> upsertCatalog(@Valid @RequestBody BenefitCatalogCmd cmd) {
        return Result.ok(benefitService.upsertCatalog(cmd, UserContext.require()));
    }

    @GetMapping("/benefits/orders")
    public Result<PageResult<BenefitOrderDTO>> adminOrders(@RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "1") Integer page,
                                                           @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(benefitService.adminOrders(status, page, size, UserContext.require()));
    }

    @PostMapping("/benefits/orders/{orderId}/deliver")
    @RateLimit(key = "'incentive:admin:benefit-deliver:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<BenefitOrderDTO> deliver(@PathVariable Long orderId, @Valid @RequestBody OrderActionCmd cmd) {
        return Result.ok(benefitService.deliver(orderId, cmd, UserContext.require()));
    }

    @PostMapping("/benefits/orders/{orderId}/cancel")
    @RateLimit(key = "'incentive:admin:benefit-cancel:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<BenefitOrderDTO> cancel(@PathVariable Long orderId, @Valid @RequestBody OrderActionCmd cmd) {
        return Result.ok(benefitService.adminCancel(orderId, cmd, UserContext.require()));
    }

    @PostMapping("/benefits/orders/{orderId}/refund")
    @RateLimit(key = "'incentive:admin:benefit-refund:' + #uid", rate = 5, per = 300, failOpen = false)
    public Result<BenefitOrderDTO> refund(@PathVariable Long orderId, @Valid @RequestBody OrderActionCmd cmd) {
        return Result.ok(benefitService.refund(orderId, cmd, UserContext.require()));
    }

    @PostMapping("/bounties")
    @RateLimit(key = "'incentive:admin:bounty-create:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<BountyDTO> createBounty(@Valid @RequestBody BountyCmd cmd) {
        return Result.ok(thankBountyService.createBounty(cmd, UserContext.require()));
    }

    @PostMapping("/bounties/{bountyId}/status")
    @RateLimit(key = "'incentive:admin:bounty-status:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<BountyDTO> updateBountyStatus(@PathVariable Long bountyId,
                                                @Valid @RequestBody BountyStatusCmd cmd) {
        return Result.ok(thankBountyService.updateBountyStatus(bountyId, cmd, UserContext.require()));
    }

    @GetMapping("/bounties/submissions")
    public Result<PageResult<BountySubmissionDTO>> bountyQueue(@RequestParam(required = false) String status,
                                                               @RequestParam(defaultValue = "1") Integer page,
                                                               @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(thankBountyService.submissionQueue(status, page, size, UserContext.require()));
    }

    @PostMapping("/bounties/submissions/{submissionId}/review")
    @RateLimit(key = "'incentive:admin:bounty-review:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<BountySubmissionDTO> reviewBounty(@PathVariable Long submissionId,
                                                     @Valid @RequestBody ReviewCmd cmd) {
        return Result.ok(thankBountyService.reviewSubmission(submissionId, cmd, UserContext.require()));
    }

    @GetMapping("/bounties/appeals")
    public Result<PageResult<BountyAppealDTO>> bountyAppeals(@RequestParam(required = false) String status,
                                                             @RequestParam(defaultValue = "1") Integer page,
                                                             @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(thankBountyService.bountyAppealQueue(
                status, page, size, UserContext.require()));
    }

    @PostMapping("/bounties/appeals/{appealId}/review")
    @RateLimit(key = "'incentive:admin:bounty-appeal-review:' + #uid", rate = 5, per = 300, failOpen = false)
    public Result<BountyAppealDTO> reviewBountyAppeal(@PathVariable Long appealId,
                                                      @Valid @RequestBody ReviewCmd cmd) {
        return Result.ok(thankBountyService.reviewBountyAppeal(appealId, cmd, UserContext.require()));
    }

    @PostMapping("/roles/definitions")
    @RateLimit(key = "'incentive:admin:role-definition:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<RoleDefinitionDTO> upsertRoleDefinition(@Valid @RequestBody RoleDefinitionCmd cmd) {
        return Result.ok(communityRoleService.upsertDefinition(cmd, UserContext.require()));
    }

    @PostMapping("/roles/metrics/{userId}")
    @RateLimit(key = "'incentive:admin:role-metric:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<Void> updateRoleMetric(@PathVariable Long userId, @Valid @RequestBody RoleMetricCmd cmd) {
        communityRoleService.updateMetric(userId, cmd, UserContext.require());
        return Result.ok();
    }

    @GetMapping("/roles/applications")
    public Result<PageResult<RoleApplicationDTO>> roleApplications(@RequestParam(required = false) String status,
                                                                   @RequestParam(defaultValue = "1") Integer page,
                                                                   @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(communityRoleService.adminApplications(status, page, size, UserContext.require()));
    }

    @PostMapping("/roles/applications/{applicationId}/review")
    @RateLimit(key = "'incentive:admin:role-review:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<RoleApplicationDTO> reviewRole(@PathVariable Long applicationId,
                                                  @Valid @RequestBody ReviewCmd cmd) {
        return Result.ok(communityRoleService.reviewApplication(applicationId, cmd, UserContext.require()));
    }

    @GetMapping("/roles/grants")
    public Result<PageResult<RoleGrantDTO>> roleGrants(@RequestParam(required = false) String status,
                                                       @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(communityRoleService.adminGrants(status, page, size, UserContext.require()));
    }

    @PostMapping("/roles/grants/{grantId}/suspend")
    @RateLimit(key = "'incentive:admin:role-suspend:' + #uid", rate = 5, per = 300, failOpen = false)
    public Result<RoleGrantDTO> suspendRole(@PathVariable Long grantId, @Valid @RequestBody RoleActionCmd cmd) {
        return Result.ok(communityRoleService.suspendGrant(grantId, cmd, UserContext.require()));
    }

    @PostMapping("/roles/grants/{grantId}/revoke")
    @RateLimit(key = "'incentive:admin:role-revoke:' + #uid", rate = 5, per = 300, failOpen = false)
    public Result<RoleGrantDTO> revokeRole(@PathVariable Long grantId, @Valid @RequestBody RoleActionCmd cmd) {
        return Result.ok(communityRoleService.revokeGrant(grantId, cmd, UserContext.require()));
    }

    @PostMapping("/roles/grants/{grantId}/expire")
    @RateLimit(key = "'incentive:admin:role-expire:' + #uid", rate = 5, per = 300, failOpen = false)
    public Result<RoleGrantDTO> expireRole(@PathVariable Long grantId, @Valid @RequestBody RoleActionCmd cmd) {
        return Result.ok(communityRoleService.expireGrant(grantId, cmd, UserContext.require()));
    }

    @PostMapping("/roles/grants/expire-due")
    @RateLimit(key = "'incentive:admin:role-expire-due:' + #uid", rate = 2, per = 600, failOpen = false)
    public Result<Integer> expireDue(@RequestParam(defaultValue = "100") Integer limit,
                                     @RequestParam String reason) {
        return Result.ok(communityRoleService.expireDue(UserContext.require(), limit, reason));
    }
}

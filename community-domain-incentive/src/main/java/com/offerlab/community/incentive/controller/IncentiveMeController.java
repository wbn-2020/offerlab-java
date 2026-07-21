package com.offerlab.community.incentive.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.incentive.api.IncentiveDtos.AccountDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BenefitOrderDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BenefitEntitlementDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.EntitlementConsumeCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.IncentiveAppealCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.IncentiveAppealDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BountyAppealCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.BountyAppealDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BountySubmissionCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.BountySubmissionDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BountyWorkspaceDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.LedgerEntryDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleApplicationCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleApplicationDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleEvidenceDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleEligibilityDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleWorkspaceDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleWorkspaceV8DTO;
import com.offerlab.community.incentive.api.IncentiveDtos.ThankCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.ThankTicketDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.ThankWorkspaceDTO;
import com.offerlab.community.incentive.application.AccountLedgerService;
import com.offerlab.community.incentive.application.BenefitService;
import com.offerlab.community.incentive.application.CommunityRoleService;
import com.offerlab.community.incentive.application.IncentiveGovernanceService;
import com.offerlab.community.incentive.application.ThankBountyService;
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
@RequestMapping("/api/v1/incentives/me")
@RequiredArgsConstructor
public class IncentiveMeController {
    private final AccountLedgerService accountLedgerService;
    private final BenefitService benefitService;
    private final ThankBountyService thankBountyService;
    private final CommunityRoleService communityRoleService;
    private final IncentiveGovernanceService incentiveGovernanceService;

    @GetMapping("/summary")
    public Result<List<AccountDTO>> summary() {
        return Result.ok(accountLedgerService.summary(UserContext.require()));
    }

    @GetMapping("/ledger")
    public Result<PageResult<LedgerEntryDTO>> ledger(@RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(accountLedgerService.ledger(UserContext.require(), page, size));
    }

    @GetMapping("/orders")
    public Result<PageResult<BenefitOrderDTO>> orders(@RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(benefitService.orders(UserContext.require(), page, size));
    }

    @GetMapping("/entitlements")
    public Result<PageResult<BenefitEntitlementDTO>> entitlements(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(benefitService.entitlements(UserContext.require(), page, size));
    }

    @PostMapping("/entitlements/{entitlementId}/consume")
    @RateLimit(key = "'incentive:entitlement-consume:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<BenefitEntitlementDTO> consumeEntitlement(
            @PathVariable Long entitlementId, @Valid @RequestBody EntitlementConsumeCmd cmd) {
        return Result.ok(benefitService.consumeEntitlement(entitlementId, cmd, UserContext.require()));
    }

    @GetMapping("/appeals")
    public Result<PageResult<IncentiveAppealDTO>> appeals(@RequestParam(defaultValue = "1") Integer page,
                                                          @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(incentiveGovernanceService.userAppeals(UserContext.require(), page, size));
    }

    @PostMapping("/appeals")
    @RateLimit(key = "'incentive:appeal-submit:' + #uid", rate = 3, per = 600, failOpen = false)
    public Result<IncentiveAppealDTO> submitAppeal(@Valid @RequestBody IncentiveAppealCmd cmd) {
        return Result.ok(incentiveGovernanceService.submitAppeal(cmd, UserContext.require()));
    }

    @GetMapping("/thanks")
    public Result<ThankWorkspaceDTO> thanks(@RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(thankBountyService.thanks(UserContext.require(), page, size));
    }

    @PostMapping("/thanks")
    @RateLimit(key = "'incentive:thank:' + #uid", rate = 12, per = 60, failOpen = false)
    public Result<ThankTicketDTO> sendThank(@Valid @RequestBody ThankCmd cmd) {
        return Result.ok(thankBountyService.sendThank(cmd, UserContext.require()));
    }

    @GetMapping("/bounties")
    public Result<BountyWorkspaceDTO> bounties(@RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(thankBountyService.bounties(UserContext.require(), page, size));
    }

    @PostMapping("/bounties/{bountyId}/submissions")
    @RateLimit(key = "'incentive:bounty-submit:' + #uid", rate = 5, per = 300, failOpen = false)
    public Result<BountySubmissionDTO> submitBounty(@PathVariable Long bountyId,
                                                     @Valid @RequestBody BountySubmissionCmd cmd) {
        return Result.ok(thankBountyService.submitBounty(bountyId, cmd, UserContext.require()));
    }

    @GetMapping("/bounties/appeals")
    public Result<PageResult<BountyAppealDTO>> bountyAppeals(@RequestParam(defaultValue = "1") Integer page,
                                                             @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(thankBountyService.userBountyAppeals(UserContext.require(), page, size));
    }

    @PostMapping("/bounties/submissions/{submissionId}/appeals")
    @RateLimit(key = "'incentive:bounty-appeal-submit:' + #uid", rate = 2, per = 600, failOpen = false)
    public Result<BountyAppealDTO> submitBountyAppeal(
            @PathVariable Long submissionId, @Valid @RequestBody BountyAppealCmd cmd) {
        return Result.ok(thankBountyService.submitBountyAppeal(
                submissionId, cmd, UserContext.require()));
    }

    @GetMapping("/roles")
    @RateLimit(key = "'incentive:roles:workspace-legacy:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<RoleWorkspaceDTO> roles(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(communityRoleService.workspace(UserContext.require(), page, size));
    }

    @GetMapping("/roles/workspace")
    @RateLimit(key = "'incentive:roles:workspace:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<RoleWorkspaceV8DTO> roleWorkspace() {
        return Result.ok(communityRoleService.workspaceV8(UserContext.require()));
    }

    @GetMapping("/roles/eligibility")
    @RateLimit(key = "'incentive:roles:eligibility:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<RoleEligibilityDTO> roleEligibility(@RequestParam String roleCode,
                                                       @RequestParam String domainCode) {
        return Result.ok(communityRoleService.eligibility(UserContext.require(), roleCode, domainCode));
    }

    @GetMapping("/roles/{roleCode}/evidence")
    @RateLimit(key = "'incentive:roles:evidence:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<RoleEvidenceDTO> roleEvidence(@PathVariable String roleCode,
                                                 @RequestParam String domainCode) {
        return Result.ok(communityRoleService.evidence(UserContext.require(), roleCode, domainCode));
    }

    @PostMapping("/roles/applications")
    @RateLimit(key = "'incentive:role-apply:' + #uid", rate = 3, per = 600, failOpen = false)
    public Result<RoleApplicationDTO> submitRole(@Valid @RequestBody RoleApplicationCmd cmd) {
        return Result.ok(communityRoleService.submitApplication(cmd, UserContext.require()));
    }
}

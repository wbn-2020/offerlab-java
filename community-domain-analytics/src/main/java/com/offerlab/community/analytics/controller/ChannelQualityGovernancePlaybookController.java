package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookCommands.BindCasePlaybookCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookCommands.CasePlaybookTransitionCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookCommands.CreatePlaybookCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookCommands.CreateVersionCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookCommands.VerifyCheckCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookCommands.VersionTransitionCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookDTO;
import com.offerlab.community.analytics.application.ChannelQualityGovernancePlaybookService;
import com.offerlab.community.common.result.Result;
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
@RequestMapping("/api/v1/community-health")
@RequiredArgsConstructor
public class ChannelQualityGovernancePlaybookController {
    private final ChannelQualityGovernancePlaybookService playbookService;

    @GetMapping("/quality-governance-playbooks")
    @RateLimit(key = "'quality-playbook:list:' + #uid", rate = 30, per = 60, failOpen = false)
    public Result<List<ChannelQualityGovernancePlaybookDTO>> playbooks(
            @RequestParam(required = false) String status, @RequestParam(required = false) Integer domain) {
        return Result.ok(playbookService.listPlaybooks(status, domain, UserContext.require()));
    }

    @GetMapping("/quality-governance-playbooks/{playbookId}")
    @RateLimit(key = "'quality-playbook:detail:' + #uid", rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityGovernancePlaybookDTO> playbook(@PathVariable Long playbookId) {
        return Result.ok(playbookService.getPlaybook(playbookId, UserContext.require()));
    }

    @GetMapping("/quality-governance-playbook-versions/{versionId}")
    @RateLimit(key = "'quality-playbook:version:' + #uid", rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityGovernancePlaybookDTO.VersionDTO> version(@PathVariable Long versionId) {
        return Result.ok(playbookService.getVersion(versionId, UserContext.require()));
    }

    @PostMapping("/quality-governance-playbooks")
    @RateLimit(key = "'quality-playbook:create:' + #uid", rate = 5, per = 300, failOpen = false)
    public Result<ChannelQualityGovernancePlaybookDTO> create(@Valid @RequestBody CreatePlaybookCmd cmd) {
        return Result.ok(playbookService.create(cmd, UserContext.require()));
    }

    @PostMapping("/quality-governance-playbooks/{playbookId}/versions")
    @RateLimit(key = "'quality-playbook:draft:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityGovernancePlaybookDTO.VersionDTO> createDraft(
            @PathVariable Long playbookId, @Valid @RequestBody CreateVersionCmd cmd) {
        return Result.ok(playbookService.createDraft(playbookId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-governance-playbook-versions/{versionId}/publish")
    @RateLimit(key = "'quality-playbook:publish:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityGovernancePlaybookDTO.VersionDTO> publish(
            @PathVariable Long versionId, @Valid @RequestBody VersionTransitionCmd cmd) {
        return Result.ok(playbookService.publish(versionId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-governance-playbook-versions/{versionId}/retire")
    @RateLimit(key = "'quality-playbook:retire:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityGovernancePlaybookDTO.VersionDTO> retire(
            @PathVariable Long versionId, @Valid @RequestBody VersionTransitionCmd cmd) {
        return Result.ok(playbookService.retire(versionId, cmd, UserContext.require()));
    }

    @GetMapping("/quality-review-risk-cases/{caseId}/playbook-recommendations")
    @RateLimit(key = "'quality-playbook:recommend:' + #uid", rate = 20, per = 60, failOpen = false)
    public Result<ChannelQualityGovernancePlaybookDTO.RecommendationPageDTO> recommendations(
            @PathVariable Long caseId, @RequestParam(required = false) Integer size) {
        return Result.ok(playbookService.recommendations(caseId, size, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/playbooks")
    @RateLimit(key = "'quality-playbook:bind:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO> bind(
            @PathVariable Long caseId, @Valid @RequestBody BindCasePlaybookCmd cmd) {
        return Result.ok(playbookService.bind(caseId, cmd, UserContext.require()));
    }

    @GetMapping("/quality-review-risk-cases/{caseId}/playbooks")
    @RateLimit(key = "'quality-playbook:case-list:' + #uid", rate = 20, per = 60, failOpen = false)
    public Result<List<ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO>> casePlaybooks(@PathVariable Long caseId) {
        return Result.ok(playbookService.casePlaybooks(caseId, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-case-playbooks/{casePlaybookId}/accept")
    @RateLimit(key = "'quality-playbook:accept:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO> accept(
            @PathVariable Long casePlaybookId, @Valid @RequestBody CasePlaybookTransitionCmd cmd) {
        return Result.ok(playbookService.accept(casePlaybookId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-case-playbooks/{casePlaybookId}/checks/{checkKey}/verify")
    @RateLimit(key = "'quality-playbook:verify:' + #uid", rate = 20, per = 300, failOpen = false)
    public Result<ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO> verifyCheck(
            @PathVariable Long casePlaybookId, @PathVariable String checkKey,
            @Valid @RequestBody VerifyCheckCmd cmd) {
        return Result.ok(playbookService.verifyCheck(casePlaybookId, checkKey, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-case-playbooks/{casePlaybookId}/checks/{checkKey}/waive")
    @RateLimit(key = "'quality-playbook:check-waive:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO> waiveCheck(
            @PathVariable Long casePlaybookId, @PathVariable String checkKey,
            @Valid @RequestBody VerifyCheckCmd cmd) {
        return Result.ok(playbookService.waiveCheck(casePlaybookId, checkKey, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-case-playbooks/{casePlaybookId}/complete")
    @RateLimit(key = "'quality-playbook:complete:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO> complete(
            @PathVariable Long casePlaybookId, @Valid @RequestBody CasePlaybookTransitionCmd cmd) {
        return Result.ok(playbookService.complete(casePlaybookId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-case-playbooks/{casePlaybookId}/waive")
    @RateLimit(key = "'quality-playbook:waive:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO> waive(
            @PathVariable Long casePlaybookId, @Valid @RequestBody CasePlaybookTransitionCmd cmd) {
        return Result.ok(playbookService.waive(casePlaybookId, cmd, UserContext.require()));
    }
}

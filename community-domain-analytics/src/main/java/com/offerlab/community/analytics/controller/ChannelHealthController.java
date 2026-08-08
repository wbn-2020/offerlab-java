package com.offerlab.community.analytics.controller;

import com.offerlab.community.analytics.api.dto.ChannelHealthDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernanceTodoPageDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewCandidateDispositionCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewCandidateDispositionDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewCandidatePageDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchCreateCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchCoordinationDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchDetailDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchEventPageDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchExtendDeadlineCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchPageDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchReassignActiveTasksCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchRiskNoteCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchWithdrawOpenTasksCmd;
import com.offerlab.community.analytics.application.ChannelHealthService;
import com.offerlab.community.analytics.application.ChannelQualityGovernanceTodoService;
import com.offerlab.community.analytics.application.ChannelQualityReviewBatchCoordinationService;
import com.offerlab.community.analytics.application.ChannelQualityReviewBatchService;
import com.offerlab.community.analytics.application.ChannelQualityReviewCandidateDispositionService;
import com.offerlab.community.analytics.application.ChannelQualityReviewCandidateService;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseAcknowledgeCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseAssignOwnerCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseActionReferenceDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseActionReferenceWriteResultDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseCloseCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseClosePreviewDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseCloseResultDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseCloseSnapshotDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseCloseSnapshotReadDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseCreateCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseEvidenceEntryDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseEvidenceEntryWriteResultDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseEventPageDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.ActionReferenceCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.ClosePreviewCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.EvidenceEntryCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.RecurrenceLinkCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.ResolutionRevisionCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.RetrospectiveAssignOwnerCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.RetrospectiveCompleteCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.RetrospectiveFindingCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.RetrospectiveInitializeCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCommands.RetrospectiveStartCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceCursorPageDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseGovernanceMilestoneDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCasePlanCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseProgressCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseQueuePageDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseRecurrenceLinkDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseResolutionRevisionDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseResolutionRevisionWriteResultDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseRetrospectiveDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseRetrospectiveEventDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseRetrospectiveReadDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseSubmitResolutionCmd;
import com.offerlab.community.analytics.application.ChannelQualityReviewRiskCaseGovernanceService;
import com.offerlab.community.analytics.application.ChannelQualityReviewRiskCaseService;
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
public class ChannelHealthController {

    private final ChannelHealthService channelHealthService;
    private final ChannelQualityReviewCandidateService channelQualityReviewCandidateService;
    private final ChannelQualityReviewCandidateDispositionService candidateDispositionService;
    private final ChannelQualityReviewBatchService batchService;
    private final ChannelQualityReviewBatchCoordinationService coordinationService;
    private final ChannelQualityReviewRiskCaseService riskCaseService;
    private final ChannelQualityReviewRiskCaseGovernanceService riskCaseGovernanceService;
    private final ChannelQualityGovernanceTodoService governanceTodoService;

    @GetMapping("/channels")
    @RateLimit(key = "'channel-health:list:' + #uid", rate = 60, per = 60, failOpen = false)
    public Result<List<ChannelHealthDTO>> channels(@RequestParam(required = false) Integer domain) {
        return Result.ok(channelHealthService.list(domain, UserContext.require()));
    }

    @GetMapping("/me/governance-todos")
    @RateLimit(key = "'channel-health:my-governance-todos:' + #uid", rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityGovernanceTodoPageDTO> myGovernanceTodos(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String dueState,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(governanceTodoService.listMine(
                status, taskType, dueState, cursor, size, UserContext.require()));
    }

    @GetMapping("/governance-todos")
    @RateLimit(key = "'channel-health:governance-todo-list:' + #uid", rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityGovernanceTodoPageDTO> governanceTodos(
            @RequestParam Integer domain,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String dueState,
            @RequestParam(required = false) String escalationLevel,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(governanceTodoService.listByDomain(
                domain, status, taskType, dueState, escalationLevel, cursor, size, UserContext.require()));
    }

    @GetMapping("/quality-review-candidates")
    @RateLimit(key = "'channel-health:quality-review-candidates:' + #uid", rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewCandidatePageDTO> qualityReviewCandidates(
            @RequestParam Integer domain,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(channelQualityReviewCandidateService.list(domain, cursor, size, UserContext.require()));
    }

    @PostMapping("/quality-review-candidates/disposition")
    @RateLimit(key = "'channel-health:quality-review-candidate-disposition:' + #uid",
            rate = 20, per = 300, failOpen = false)
    public Result<ChannelQualityReviewCandidateDispositionDTO> disposeQualityReviewCandidate(
            @Valid @RequestBody ChannelQualityReviewCandidateDispositionCmd cmd) {
        return Result.ok(candidateDispositionService.dispose(cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-batches")
    @RateLimit(key = "'channel-health:quality-review-batch-create:' + #uid",
            rate = 5, per = 300, failOpen = false)
    public Result<ChannelQualityReviewBatchDetailDTO> createQualityReviewBatch(
            @Valid @RequestBody ChannelQualityReviewBatchCreateCmd cmd) {
        return Result.ok(batchService.create(cmd, UserContext.require()));
    }

    @GetMapping("/quality-review-batches")
    @RateLimit(key = "'channel-health:quality-review-batch-list:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewBatchPageDTO> qualityReviewBatches(
            @RequestParam Integer domain,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(batchService.list(domain, cursor, size, UserContext.require()));
    }

    @GetMapping("/quality-review-batches/{batchId}")
    @RateLimit(key = "'channel-health:quality-review-batch-detail:' + #uid",
            rate = 60, per = 60, failOpen = false)
    public Result<ChannelQualityReviewBatchDetailDTO> qualityReviewBatch(
            @PathVariable Long batchId) {
        return Result.ok(batchService.get(batchId, UserContext.require()));
    }

    @GetMapping("/quality-review-batches/{batchId}/coordination")
    @RateLimit(key = "'channel-health:quality-review-batch-coordination:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewBatchCoordinationDTO> qualityReviewBatchCoordination(
            @PathVariable Long batchId) {
        return Result.ok(coordinationService.coordination(batchId, UserContext.require()));
    }

    @PostMapping("/quality-review-batches/{batchId}/extend-deadline")
    @RateLimit(key = "'channel-health:quality-review-batch-extend:' + #uid",
            rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityReviewBatchCoordinationDTO> extendQualityReviewBatchDeadline(
            @PathVariable Long batchId,
            @Valid @RequestBody ChannelQualityReviewBatchExtendDeadlineCmd cmd) {
        return Result.ok(coordinationService.extendDeadline(batchId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-batches/{batchId}/reassign-active-tasks")
    @RateLimit(key = "'channel-health:quality-review-batch-reassign:' + #uid",
            rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityReviewBatchCoordinationDTO> reassignQualityReviewBatchTasks(
            @PathVariable Long batchId,
            @Valid @RequestBody ChannelQualityReviewBatchReassignActiveTasksCmd cmd) {
        return Result.ok(coordinationService.reassignActiveTasks(batchId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-batches/{batchId}/risk-notes")
    @RateLimit(key = "'channel-health:quality-review-batch-risk-note:' + #uid",
            rate = 20, per = 300, failOpen = false)
    public Result<ChannelQualityReviewBatchCoordinationDTO> addQualityReviewBatchRiskNote(
            @PathVariable Long batchId,
            @Valid @RequestBody ChannelQualityReviewBatchRiskNoteCmd cmd) {
        return Result.ok(coordinationService.addRiskNote(batchId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-batches/{batchId}/withdraw-open-tasks")
    @RateLimit(key = "'channel-health:quality-review-batch-withdraw:' + #uid",
            rate = 5, per = 300, failOpen = false)
    public Result<ChannelQualityReviewBatchCoordinationDTO> withdrawQualityReviewBatchOpenTasks(
            @PathVariable Long batchId,
            @Valid @RequestBody ChannelQualityReviewBatchWithdrawOpenTasksCmd cmd) {
        return Result.ok(coordinationService.withdrawOpenTasks(batchId, cmd, UserContext.require()));
    }

    @GetMapping("/quality-review-batches/{batchId}/events")
    @RateLimit(key = "'channel-health:quality-review-batch-events:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewBatchEventPageDTO> qualityReviewBatchEvents(
            @PathVariable Long batchId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(coordinationService.events(batchId, cursor, size, UserContext.require()));
    }

    @GetMapping("/quality-review-risk-cases")
    @RateLimit(key = "'channel-health:quality-review-risk-case-list:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseQueuePageDTO> qualityReviewRiskCases(
            @RequestParam Integer domain,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(riskCaseService.queue(domain, mode, cursor, size, UserContext.require()));
    }

    @PostMapping("/quality-review-batches/{batchId}/risk-cases")
    @RateLimit(key = "'channel-health:quality-review-risk-case-create:' + #uid",
            rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseDTO> createQualityReviewRiskCase(
            @PathVariable Long batchId,
            @Valid @RequestBody ChannelQualityReviewRiskCaseCreateCmd cmd) {
        return Result.ok(riskCaseService.create(batchId, cmd, UserContext.require()));
    }

    @GetMapping("/quality-review-risk-cases/{caseId}")
    @RateLimit(key = "'channel-health:quality-review-risk-case-detail:' + #uid",
            rate = 60, per = 60, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseDTO> qualityReviewRiskCase(@PathVariable Long caseId) {
        return Result.ok(riskCaseService.detail(caseId, UserContext.require()));
    }

    @GetMapping("/quality-review-risk-cases/{caseId}/events")
    @RateLimit(key = "'channel-health:quality-review-risk-case-events:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseEventPageDTO> qualityReviewRiskCaseEvents(
            @PathVariable Long caseId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(riskCaseService.events(caseId, cursor, size, UserContext.require()));
    }

    @GetMapping("/quality-review-risk-cases/{caseId}/governance")
    @RateLimit(key = "'channel-health:quality-review-risk-case-governance:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseGovernanceDTO> qualityReviewRiskCaseGovernance(
            @PathVariable Long caseId) {
        return Result.ok(riskCaseGovernanceService.governance(caseId, UserContext.require()));
    }

    @GetMapping("/quality-review-risk-cases/{caseId}/governance-milestones")
    @RateLimit(key = "'channel-health:quality-review-risk-case-governance-milestones:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseGovernanceCursorPageDTO<ChannelQualityReviewRiskCaseGovernanceMilestoneDTO>>
    qualityReviewRiskCaseGovernanceMilestones(
            @PathVariable Long caseId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(riskCaseGovernanceService.milestones(caseId, cursor, size, UserContext.require()));
    }

    @GetMapping("/quality-review-risk-cases/{caseId}/resolution-revisions")
    @RateLimit(key = "'channel-health:quality-review-risk-case-resolution-revisions:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseGovernanceCursorPageDTO<ChannelQualityReviewRiskCaseResolutionRevisionDTO>>
    qualityReviewRiskCaseResolutionRevisions(
            @PathVariable Long caseId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(riskCaseGovernanceService.resolutionRevisions(
                caseId, cursor, size, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/resolution-revisions")
    @RateLimit(key = "'channel-health:quality-review-risk-case-resolution-revision-add:' + #uid",
            rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseResolutionRevisionWriteResultDTO>
    addQualityReviewRiskCaseResolutionRevision(
            @PathVariable Long caseId,
            @Valid @RequestBody ResolutionRevisionCmd cmd) {
        Long operatorUid = UserContext.require();
        ChannelQualityReviewRiskCaseResolutionRevisionDTO revision =
                riskCaseGovernanceService.addResolutionRevision(caseId, cmd, operatorUid);
        return Result.ok(ChannelQualityReviewRiskCaseResolutionRevisionWriteResultDTO.builder()
                .governance(riskCaseGovernanceService.governance(caseId, operatorUid))
                .resolutionRevision(revision)
                .build());
    }

    @GetMapping("/quality-review-risk-cases/{caseId}/action-references")
    @RateLimit(key = "'channel-health:quality-review-risk-case-action-references:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseGovernanceCursorPageDTO<ChannelQualityReviewRiskCaseActionReferenceDTO>>
    qualityReviewRiskCaseActionReferences(
            @PathVariable Long caseId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(riskCaseGovernanceService.actionReferences(
                caseId, cursor, size, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/action-references")
    @RateLimit(key = "'channel-health:quality-review-risk-case-action-reference-add:' + #uid",
            rate = 20, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseActionReferenceWriteResultDTO>
    addQualityReviewRiskCaseActionReference(
            @PathVariable Long caseId,
            @Valid @RequestBody ActionReferenceCmd cmd) {
        Long operatorUid = UserContext.require();
        ChannelQualityReviewRiskCaseActionReferenceDTO actionReference =
                riskCaseGovernanceService.addActionReference(caseId, cmd, operatorUid);
        return Result.ok(ChannelQualityReviewRiskCaseActionReferenceWriteResultDTO.builder()
                .governance(riskCaseGovernanceService.governance(caseId, operatorUid))
                .actionReference(actionReference)
                .build());
    }

    @GetMapping("/quality-review-risk-cases/{caseId}/evidence")
    @RateLimit(key = "'channel-health:quality-review-risk-case-evidence:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseGovernanceCursorPageDTO<ChannelQualityReviewRiskCaseEvidenceEntryDTO>>
    qualityReviewRiskCaseEvidence(
            @PathVariable Long caseId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(riskCaseGovernanceService.evidence(caseId, cursor, size, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/evidence")
    @RateLimit(key = "'channel-health:quality-review-risk-case-evidence-add:' + #uid",
            rate = 20, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseEvidenceEntryWriteResultDTO>
    addQualityReviewRiskCaseEvidence(
            @PathVariable Long caseId,
            @Valid @RequestBody EvidenceEntryCmd cmd) {
        Long operatorUid = UserContext.require();
        ChannelQualityReviewRiskCaseEvidenceEntryDTO evidenceEntry =
                riskCaseGovernanceService.addEvidence(caseId, cmd, operatorUid);
        return Result.ok(ChannelQualityReviewRiskCaseEvidenceEntryWriteResultDTO.builder()
                .governance(riskCaseGovernanceService.governance(caseId, operatorUid))
                .evidenceEntry(evidenceEntry)
                .build());
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/close-preview")
    @RateLimit(key = "'channel-health:quality-review-risk-case-close-preview:' + #uid",
            rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseClosePreviewDTO> previewQualityReviewRiskCaseClose(
            @PathVariable Long caseId,
            @Valid @RequestBody ClosePreviewCmd cmd) {
        return Result.ok(riskCaseGovernanceService.closePreview(caseId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/assign-owner")
    @RateLimit(key = "'channel-health:quality-review-risk-case-assign-owner:' + #uid",
            rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseDTO> assignQualityReviewRiskCaseOwner(
            @PathVariable Long caseId,
            @Valid @RequestBody ChannelQualityReviewRiskCaseAssignOwnerCmd cmd) {
        return Result.ok(riskCaseService.assignOwner(caseId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/acknowledge")
    @RateLimit(key = "'channel-health:quality-review-risk-case-acknowledge:' + #uid",
            rate = 20, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseDTO> acknowledgeQualityReviewRiskCase(
            @PathVariable Long caseId,
            @Valid @RequestBody ChannelQualityReviewRiskCaseAcknowledgeCmd cmd) {
        return Result.ok(riskCaseService.acknowledge(caseId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/plan")
    @RateLimit(key = "'channel-health:quality-review-risk-case-plan:' + #uid",
            rate = 20, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseDTO> planQualityReviewRiskCase(
            @PathVariable Long caseId,
            @Valid @RequestBody ChannelQualityReviewRiskCasePlanCmd cmd) {
        return Result.ok(riskCaseService.recordPlan(caseId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/progress")
    @RateLimit(key = "'channel-health:quality-review-risk-case-progress:' + #uid",
            rate = 30, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseDTO> progressQualityReviewRiskCase(
            @PathVariable Long caseId,
            @Valid @RequestBody ChannelQualityReviewRiskCaseProgressCmd cmd) {
        return Result.ok(riskCaseService.recordProgress(caseId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/submit-resolution")
    @RateLimit(key = "'channel-health:quality-review-risk-case-resolution:' + #uid",
            rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseDTO> submitQualityReviewRiskCaseResolution(
            @PathVariable Long caseId,
            @Valid @RequestBody ChannelQualityReviewRiskCaseSubmitResolutionCmd cmd) {
        return Result.ok(riskCaseService.submitResolution(caseId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/close")
    @RateLimit(key = "'channel-health:quality-review-risk-case-close:' + #uid",
            rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseCloseResultDTO> closeQualityReviewRiskCase(
            @PathVariable Long caseId,
            @Valid @RequestBody ChannelQualityReviewRiskCaseCloseCmd cmd) {
        Long operatorUid = UserContext.require();
        ChannelQualityReviewRiskCaseCloseSnapshotDTO snapshot =
                riskCaseGovernanceService.closeWithSnapshot(caseId, cmd, operatorUid);
        ChannelQualityReviewRiskCaseDTO caseDetail = riskCaseService.detail(caseId, operatorUid);
        ChannelQualityReviewRiskCaseRetrospectiveDTO retrospective =
                riskCaseGovernanceService.retrospective(caseId, operatorUid);
        ChannelQualityReviewRiskCaseGovernanceDTO governance =
                riskCaseGovernanceService.governance(caseId, operatorUid);
        return Result.ok(ChannelQualityReviewRiskCaseCloseResultDTO.builder()
                .caseDetail(caseDetail)
                .snapshot(snapshot)
                .retrospective(retrospective)
                .milestoneFactVersion(governance.getMilestoneFactVersion())
                .build());
    }

    @GetMapping("/quality-review-risk-cases/{caseId}/close-snapshot")
    @RateLimit(key = "'channel-health:quality-review-risk-case-close-snapshot:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseCloseSnapshotReadDTO> qualityReviewRiskCaseCloseSnapshot(
            @PathVariable Long caseId) {
        return Result.ok(riskCaseGovernanceService.closeSnapshot(caseId, UserContext.require()));
    }

    @GetMapping("/quality-review-risk-cases/{caseId}/retrospective")
    @RateLimit(key = "'channel-health:quality-review-risk-case-retrospective:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseRetrospectiveReadDTO> qualityReviewRiskCaseRetrospective(
            @PathVariable Long caseId) {
        Long operatorUid = UserContext.require();
        ChannelQualityReviewRiskCaseGovernanceDTO governance =
                riskCaseGovernanceService.governance(caseId, operatorUid);
        return Result.ok(ChannelQualityReviewRiskCaseRetrospectiveReadDTO.builder()
                .caseId(governance.getCaseId())
                .legacyClosedWithoutSnapshot(governance.getLegacyClosedWithoutSnapshot())
                .retrospective(riskCaseGovernanceService.retrospective(caseId, operatorUid))
                .build());
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/retrospective/initialize")
    @RateLimit(key = "'channel-health:quality-review-risk-case-retrospective-initialize:' + #uid",
            rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseRetrospectiveDTO> initializeQualityReviewRiskCaseRetrospective(
            @PathVariable Long caseId,
            @Valid @RequestBody RetrospectiveInitializeCmd cmd) {
        return Result.ok(riskCaseGovernanceService.initializeRetrospective(
                caseId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/retrospective/assign-owner")
    @RateLimit(key = "'channel-health:quality-review-risk-case-retrospective-assign:' + #uid",
            rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseRetrospectiveDTO> assignQualityReviewRiskCaseRetrospectiveOwner(
            @PathVariable Long caseId,
            @Valid @RequestBody RetrospectiveAssignOwnerCmd cmd) {
        return Result.ok(riskCaseGovernanceService.assignRetrospectiveOwner(
                caseId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/retrospective/start")
    @RateLimit(key = "'channel-health:quality-review-risk-case-retrospective-start:' + #uid",
            rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseRetrospectiveDTO> startQualityReviewRiskCaseRetrospective(
            @PathVariable Long caseId,
            @Valid @RequestBody RetrospectiveStartCmd cmd) {
        return Result.ok(riskCaseGovernanceService.startRetrospective(
                caseId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/retrospective/findings")
    @RateLimit(key = "'channel-health:quality-review-risk-case-retrospective-finding:' + #uid",
            rate = 20, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseRetrospectiveDTO> addQualityReviewRiskCaseRetrospectiveFinding(
            @PathVariable Long caseId,
            @Valid @RequestBody RetrospectiveFindingCmd cmd) {
        return Result.ok(riskCaseGovernanceService.recordRetrospectiveFinding(
                caseId, cmd, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/retrospective/complete")
    @RateLimit(key = "'channel-health:quality-review-risk-case-retrospective-complete:' + #uid",
            rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseRetrospectiveDTO> completeQualityReviewRiskCaseRetrospective(
            @PathVariable Long caseId,
            @Valid @RequestBody RetrospectiveCompleteCmd cmd) {
        return Result.ok(riskCaseGovernanceService.completeRetrospective(
                caseId, cmd, UserContext.require()));
    }

    @GetMapping("/quality-review-risk-cases/{caseId}/retrospective/events")
    @RateLimit(key = "'channel-health:quality-review-risk-case-retrospective-events:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseGovernanceCursorPageDTO<ChannelQualityReviewRiskCaseRetrospectiveEventDTO>>
    qualityReviewRiskCaseRetrospectiveEvents(
            @PathVariable Long caseId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(riskCaseGovernanceService.retrospectiveEvents(
                caseId, cursor, size, UserContext.require()));
    }

    @GetMapping("/quality-review-risk-cases/{caseId}/recurrence-links")
    @RateLimit(key = "'channel-health:quality-review-risk-case-recurrence-links:' + #uid",
            rate = 30, per = 60, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseGovernanceCursorPageDTO<ChannelQualityReviewRiskCaseRecurrenceLinkDTO>>
    qualityReviewRiskCaseRecurrenceLinks(
            @PathVariable Long caseId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(riskCaseGovernanceService.recurrenceLinks(
                caseId, cursor, size, UserContext.require()));
    }

    @PostMapping("/quality-review-risk-cases/{caseId}/recurrence-links")
    @RateLimit(key = "'channel-health:quality-review-risk-case-recurrence-link-add:' + #uid",
            rate = 10, per = 300, failOpen = false)
    public Result<ChannelQualityReviewRiskCaseRecurrenceLinkDTO> addQualityReviewRiskCaseRecurrenceLink(
            @PathVariable Long caseId,
            @Valid @RequestBody RecurrenceLinkCmd cmd) {
        return Result.ok(riskCaseGovernanceService.addRecurrenceLink(
                caseId, cmd, UserContext.require()));
    }
}

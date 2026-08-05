package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.utils.RiskConfirmation;
import com.offerlab.community.infra.audit.AdminAuditLog;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.dto.OperationCandidateDTO;
import com.offerlab.community.post.api.dto.OperationCurationItemCmd;
import com.offerlab.community.post.api.dto.OperationCurationItemDTO;
import com.offerlab.community.post.api.dto.OperationSlotCmd;
import com.offerlab.community.post.api.dto.OperationSlotDTO;
import com.offerlab.community.post.api.dto.OperationSlotItemCmd;
import com.offerlab.community.post.api.dto.OperationSlotItemDTO;
import com.offerlab.community.post.api.dto.OperationTopicCmd;
import com.offerlab.community.post.api.dto.OperationTopicDTO;
import com.offerlab.community.post.api.dto.OperationTopicCandidateHintCmd;
import com.offerlab.community.post.application.OperationCurationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
@Validated
public class OperationCurationController {

    private final OperationCurationService operationCurationService;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;

    @GetMapping("/admin/candidates")
    @RateLimit(key = "'operation:admin:candidates:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<List<OperationCandidateDTO>> candidates(@RequestParam(required = false) @Size(max = 80) String keyword,
                                                           @RequestParam(required = false) Integer domain,
                                                           @RequestParam(required = false) Integer postType,
                                                           @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        requireOps();
        return Result.ok(operationCurationService.listCandidates(keyword, domain, postType, limit));
    }

    @GetMapping("/admin/topics/{topicId}/candidates")
    @RateLimit(key = "'operation:admin:topic-candidates:' + #uid + ':' + #topicId", rate = 120, per = 60, failOpen = false)
    public Result<List<OperationCandidateDTO>> topicCandidates(@PathVariable Long topicId,
                                                               @RequestParam(required = false) @Size(max = 80) String keyword,
                                                               @RequestParam(required = false) Integer domain,
                                                               @RequestParam(required = false) Integer postType,
                                                               @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        requireOps();
        return Result.ok(operationCurationService.listTopicCandidates(topicId, keyword, domain, postType, limit));
    }

    @PostMapping("/admin/topics/{topicId}/candidate-hints")
    @RateLimit(key = "'operation:admin:candidate-hints:' + #uid + ':' + #topicId", rate = 30, per = 60, failOpen = false)
    public Result<List<OperationCandidateDTO>> receiveTopicCandidateHints(
            @PathVariable Long topicId,
            @Valid @RequestBody List<OperationTopicCandidateHintCmd> hints) {
        return Result.ok(operationCurationService.receiveTopicCandidateHints(topicId, hints, requireOpsMutation()));
    }

    @GetMapping("/admin/curation-pool")
    @RateLimit(key = "'operation:admin:curation-pool:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<List<OperationCurationItemDTO>> curationPool(@RequestParam(required = false) @Size(max = 32) String status,
                                                               @RequestParam(required = false) @Size(max = 32) String sourceType,
                                                               @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        requireOps();
        return Result.ok(operationCurationService.listCurationItems(status, sourceType, limit));
    }

    @PostMapping("/admin/curation-pool/items")
    @RateLimit(key = "'operation:admin:curation-item:upsert:' + #uid", rate = 40, per = 60, failOpen = false)
    public Result<OperationCurationItemDTO> upsertCurationItem(@Valid @RequestBody OperationCurationItemCmd cmd) {
        return Result.ok(operationCurationService.upsertCurationItem(cmd, requireOpsMutation()));
    }

    @DeleteMapping("/admin/curation-pool/items/{itemId}")
    @RateLimit(key = "'operation:admin:curation-item:delete:' + #uid + ':' + #itemId", rate = 20, per = 60, failOpen = false)
    public Result<Void> deleteCurationItem(@PathVariable Long itemId,
                                           @RequestParam(required = false) String note) {
        operationCurationService.deleteCurationItem(itemId, requireOpsMutation(), note);
        return Result.ok();
    }

    @GetMapping("/admin/slots")
    @RateLimit(key = "'operation:admin:slots:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<List<OperationSlotDTO>> adminSlots(@RequestParam(required = false) @Size(max = 32) String status,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        requireOps();
        return Result.ok(operationCurationService.listAdminSlots(status, limit));
    }

    @PostMapping("/admin/slots")
    @RateLimit(key = "'operation:admin:slot:upsert:' + #uid", rate = 30, per = 60, failOpen = false)
    public Result<OperationSlotDTO> upsertSlot(@Valid @RequestBody OperationSlotCmd cmd) {
        return Result.ok(operationCurationService.upsertSlot(cmd, requireOpsMutation()));
    }

    @PostMapping("/admin/slots/{slotId}/items")
    @RateLimit(key = "'operation:admin:slot-item:upsert:' + #uid + ':' + #slotId", rate = 40, per = 60, failOpen = false)
    public Result<OperationSlotItemDTO> upsertSlotItem(@PathVariable Long slotId,
                                                       @Valid @RequestBody OperationSlotItemCmd cmd) {
        return Result.ok(operationCurationService.upsertSlotItem(slotId, cmd, requireOpsMutation()));
    }

    @DeleteMapping("/admin/slots/items/{itemId}")
    @RateLimit(key = "'operation:admin:slot-item:delete:' + #uid + ':' + #itemId", rate = 20, per = 60, failOpen = false)
    public Result<Void> deleteSlotItem(@PathVariable Long itemId,
                                       @RequestParam(required = false) String note) {
        operationCurationService.deleteSlotItem(itemId, requireOpsMutation(), note);
        return Result.ok();
    }

    @PostMapping("/admin/slots/{slotId}/publish")
    @RateLimit(key = "'operation:admin:slot:publish:' + #uid + ':' + #slotId", rate = 10, per = 60, failOpen = false)
    public Result<OperationSlotDTO> publishSlot(@PathVariable Long slotId,
                                                @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.publishSlot(slotId, requireOpsMutation(), requireCriticalNote(req)));
    }

    @PostMapping("/admin/slots/{slotId}/offline")
    @RateLimit(key = "'operation:admin:slot:offline:' + #uid + ':' + #slotId", rate = 10, per = 60, failOpen = false)
    public Result<OperationSlotDTO> offlineSlot(@PathVariable Long slotId,
                                                @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.offlineSlot(slotId, requireOpsMutation(), requireCriticalNote(req)));
    }

    @PostMapping("/admin/slots/{slotId}/rollback")
    @RateLimit(key = "'operation:admin:slot:rollback:' + #uid + ':' + #slotId", rate = 10, per = 60, failOpen = false)
    public Result<OperationSlotDTO> rollbackSlot(@PathVariable Long slotId,
                                                 @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.rollbackSlot(slotId, requireOpsMutation(), requireCriticalNote(req)));
    }

    @GetMapping("/admin/topics")
    @RateLimit(key = "'operation:admin:topics:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<List<OperationTopicDTO>> adminTopics(@RequestParam(required = false) @Size(max = 32) String status,
                                                       @RequestParam(required = false) @Size(max = 32) String operationType,
                                                       @RequestParam(required = false) @Size(max = 80) String keyword,
                                                       @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        requireOps();
        return Result.ok(operationCurationService.listAdminTopics(status, operationType, keyword, limit));
    }

    @GetMapping("/admin/topics/{topicId}")
    @RateLimit(key = "'operation:admin:topic:detail:' + #uid + ':' + #topicId", rate = 120, per = 60, failOpen = false)
    public Result<OperationTopicDTO> adminTopic(@PathVariable Long topicId) {
        requireOps();
        return Result.ok(operationCurationService.getAdminTopic(topicId));
    }

    @GetMapping("/admin/audit-logs")
    @RateLimit(key = "'operation:admin:audit-logs:' + #uid", rate = 120, per = 60, failOpen = false)
    public Result<List<AdminAuditLog>> auditLogs(@RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        requireOps();
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100));
        List<AdminAuditLog> logs = adminAuditService.listRecent(null, null, 100).stream()
                .filter(log -> startsWithOperation(log.getAction()) || startsWithOperation(log.getResourceType()))
                .limit(safeLimit)
                .toList();
        return Result.ok(logs);
    }

    @PostMapping("/admin/topics")
    @RateLimit(key = "'operation:admin:topic:create:' + #uid", rate = 20, per = 60, failOpen = false)
    public Result<OperationTopicDTO> createTopic(@Valid @RequestBody OperationTopicCmd cmd) {
        return Result.ok(operationCurationService.createTopic(cmd, requireOpsMutation()));
    }

    @PutMapping("/admin/topics/{topicId}")
    @RateLimit(key = "'operation:admin:topic:update:' + #uid + ':' + #topicId", rate = 30, per = 60, failOpen = false)
    public Result<OperationTopicDTO> updateTopic(@PathVariable Long topicId,
                                                 @Valid @RequestBody OperationTopicCmd cmd) {
        return Result.ok(operationCurationService.updateTopic(topicId, cmd, requireOpsMutation()));
    }

    @GetMapping("/admin/topics/{topicId}/preview")
    @RateLimit(key = "'operation:admin:topic:preview:get:' + #uid + ':' + #topicId", rate = 120, per = 60, failOpen = false)
    public Result<OperationTopicDTO> previewTopic(@PathVariable Long topicId) {
        requireOps();
        return Result.ok(operationCurationService.previewTopic(topicId));
    }

    @PostMapping("/admin/topics/{topicId}/preview")
    @RateLimit(key = "'operation:admin:topic:preview:mark:' + #uid + ':' + #topicId", rate = 30, per = 60, failOpen = false)
    public Result<OperationTopicDTO> markPreview(@PathVariable Long topicId,
                                                 @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.markPreview(topicId, requireOpsMutation(),
                requireExpectedDraftRevision(req), req == null ? null : req.getNote()));
    }

    @PostMapping("/admin/topics/{topicId}/publish-check")
    @RateLimit(key = "'operation:admin:topic:publish-check:' + #uid + ':' + #topicId", rate = 30, per = 60, failOpen = false)
    public Result<Map<String, Object>> checkTopicPublish(@PathVariable Long topicId) {
        requireOps();
        return Result.ok(operationCurationService.checkTopicPublish(topicId));
    }

    @PostMapping("/admin/topics/{topicId}/publish")
    @RateLimit(key = "'operation:admin:topic:publish:' + #uid + ':' + #topicId", rate = 10, per = 60, failOpen = false)
    public Result<OperationTopicDTO> publishTopic(@PathVariable Long topicId,
                                                  @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.publishTopic(topicId, requireOpsMutation(),
                requireExpectedDraftRevision(req), requireCriticalNote(req)));
    }

    @PostMapping("/admin/topics/{topicId}/offline")
    @RateLimit(key = "'operation:admin:topic:offline:' + #uid + ':' + #topicId", rate = 10, per = 60, failOpen = false)
    public Result<OperationTopicDTO> offlineTopic(@PathVariable Long topicId,
                                                  @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.offlineTopic(topicId, requireOpsMutation(),
                requireExpectedDraftRevision(req), requireCriticalNote(req)));
    }

    @PostMapping("/admin/topics/{topicId}/rollback")
    @RateLimit(key = "'operation:admin:topic:rollback:' + #uid + ':' + #topicId", rate = 10, per = 60, failOpen = false)
    public Result<OperationTopicDTO> rollbackTopic(@PathVariable Long topicId,
                                                   @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.rollbackTopic(topicId, requireOpsMutation(),
                requireExpectedDraftRevision(req), requireCriticalNote(req)));
    }

    @PostMapping("/admin/topics/{topicId}/archive")
    @RateLimit(key = "'operation:admin:topic:archive:' + #uid + ':' + #topicId", rate = 10, per = 60, failOpen = false)
    public Result<OperationTopicDTO> archiveTopic(@PathVariable Long topicId,
                                                  @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.archiveTopic(topicId, requireOpsMutation(),
                requireExpectedDraftRevision(req), requireCriticalNote(req)));
    }

    @PublicApi
    @GetMapping("/slots/{slotCode}")
    @RateLimit(key = "'public:operations:slot:' + #slotCode + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<OperationSlotDTO> publicSlot(@PathVariable @Size(max = 64) String slotCode,
                                               @RequestParam(defaultValue = "0") @Min(0) @Max(20) int limit,
                                               HttpServletRequest request) {
        return Result.ok(operationCurationService.getPublicSlot(slotCode, limit, UserContext.get()));
    }

    @PublicApi
    @GetMapping("/topics/{slug}")
    @RateLimit(key = "'public:operations:topic:' + #slug + ':' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<OperationTopicDTO> publicTopic(@PathVariable @Size(max = 128) String slug,
                                                  HttpServletRequest request) {
        return Result.ok(operationCurationService.getPublicTopic(slug, UserContext.get()));
    }

    private Long requireOps() {
        Long uid = UserContext.require();
        adminPermissionService.requireStrictAdmin(uid);
        return uid;
    }

    private Long requireOpsMutation() {
        Long uid = requireOps();
        if (!adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_OPS)) {
            throw new com.offerlab.community.common.exception.BizException(com.offerlab.community.common.result.ErrorCode.FORBIDDEN);
        }
        return uid;
    }

    private boolean startsWithOperation(String value) {
        return value != null && value.startsWith("OPERATION_");
    }

    private String requireCriticalNote(NoteReq req) {
        return RiskConfirmation.requireCritical(req == null ? null : req.getNote(),
                req == null ? null : req.getConfirmationPhrase());
    }

    private Integer requireExpectedDraftRevision(NoteReq req) {
        if (req == null || req.getExpectedDraftRevision() == null) {
            throw new com.offerlab.community.common.exception.BizException(
                    com.offerlab.community.common.result.ErrorCode.PARAM_ERROR.getCode(),
                    "expectedDraftRevision is required");
        }
        return req.getExpectedDraftRevision();
    }

    @Data
    public static class NoteReq {
        @Size(max = 500)
        private String note;
        @Size(max = 64)
        private String confirmationPhrase;
        @PositiveOrZero
        private Integer expectedDraftRevision;
    }
}

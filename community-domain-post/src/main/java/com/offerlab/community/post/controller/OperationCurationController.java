package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.utils.RiskConfirmation;
import com.offerlab.community.infra.audit.AdminAuditLog;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.post.api.dto.OperationCandidateDTO;
import com.offerlab.community.post.api.dto.OperationCurationItemCmd;
import com.offerlab.community.post.api.dto.OperationCurationItemDTO;
import com.offerlab.community.post.api.dto.OperationSlotCmd;
import com.offerlab.community.post.api.dto.OperationSlotDTO;
import com.offerlab.community.post.api.dto.OperationSlotItemCmd;
import com.offerlab.community.post.api.dto.OperationSlotItemDTO;
import com.offerlab.community.post.api.dto.OperationTopicCmd;
import com.offerlab.community.post.api.dto.OperationTopicDTO;
import com.offerlab.community.post.application.OperationCurationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
public class OperationCurationController {

    private final OperationCurationService operationCurationService;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;

    @GetMapping("/admin/candidates")
    public Result<List<OperationCandidateDTO>> candidates(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Integer domain,
                                                          @RequestParam(required = false) Integer postType,
                                                          @RequestParam(defaultValue = "50") int limit) {
        requireOps();
        return Result.ok(operationCurationService.listCandidates(keyword, domain, postType, limit));
    }

    @GetMapping("/admin/curation-pool")
    public Result<List<OperationCurationItemDTO>> curationPool(@RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String sourceType,
                                                               @RequestParam(defaultValue = "50") int limit) {
        requireOps();
        return Result.ok(operationCurationService.listCurationItems(status, sourceType, limit));
    }

    @PostMapping("/admin/curation-pool/items")
    public Result<OperationCurationItemDTO> upsertCurationItem(@Valid @RequestBody OperationCurationItemCmd cmd) {
        return Result.ok(operationCurationService.upsertCurationItem(cmd, requireOps()));
    }

    @DeleteMapping("/admin/curation-pool/items/{itemId}")
    public Result<Void> deleteCurationItem(@PathVariable Long itemId,
                                           @RequestParam(required = false) String note) {
        operationCurationService.deleteCurationItem(itemId, requireOps(), note);
        return Result.ok();
    }

    @GetMapping("/admin/slots")
    public Result<List<OperationSlotDTO>> adminSlots(@RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "20") int limit) {
        requireOps();
        return Result.ok(operationCurationService.listAdminSlots(status, limit));
    }

    @PostMapping("/admin/slots")
    public Result<OperationSlotDTO> upsertSlot(@Valid @RequestBody OperationSlotCmd cmd) {
        return Result.ok(operationCurationService.upsertSlot(cmd, requireOpsMutation()));
    }

    @PostMapping("/admin/slots/{slotId}/items")
    public Result<OperationSlotItemDTO> upsertSlotItem(@PathVariable Long slotId,
                                                       @Valid @RequestBody OperationSlotItemCmd cmd) {
        return Result.ok(operationCurationService.upsertSlotItem(slotId, cmd, requireOpsMutation()));
    }

    @DeleteMapping("/admin/slots/items/{itemId}")
    public Result<Void> deleteSlotItem(@PathVariable Long itemId,
                                       @RequestParam(required = false) String note) {
        operationCurationService.deleteSlotItem(itemId, requireOpsMutation(), note);
        return Result.ok();
    }

    @PostMapping("/admin/slots/{slotId}/publish")
    public Result<OperationSlotDTO> publishSlot(@PathVariable Long slotId,
                                                @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.publishSlot(slotId, requireOpsMutation(), requireCriticalNote(req)));
    }

    @PostMapping("/admin/slots/{slotId}/offline")
    public Result<OperationSlotDTO> offlineSlot(@PathVariable Long slotId,
                                                @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.offlineSlot(slotId, requireOpsMutation(), requireCriticalNote(req)));
    }

    @PostMapping("/admin/slots/{slotId}/rollback")
    public Result<OperationSlotDTO> rollbackSlot(@PathVariable Long slotId,
                                                 @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.rollbackSlot(slotId, requireOpsMutation(), requireCriticalNote(req)));
    }

    @GetMapping("/admin/topics")
    public Result<List<OperationTopicDTO>> adminTopics(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String operationType,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(defaultValue = "50") int limit) {
        requireOps();
        return Result.ok(operationCurationService.listAdminTopics(status, operationType, keyword, limit));
    }

    @GetMapping("/admin/audit-logs")
    public Result<List<AdminAuditLog>> auditLogs(@RequestParam(defaultValue = "50") int limit) {
        requireOps();
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100));
        List<AdminAuditLog> logs = adminAuditService.listRecent(null, null, 100).stream()
                .filter(log -> startsWithOperation(log.getAction()) || startsWithOperation(log.getResourceType()))
                .limit(safeLimit)
                .toList();
        return Result.ok(logs);
    }

    @PostMapping("/admin/topics")
    public Result<OperationTopicDTO> createTopic(@Valid @RequestBody OperationTopicCmd cmd) {
        return Result.ok(operationCurationService.createTopic(cmd, requireOps()));
    }

    @PutMapping("/admin/topics/{topicId}")
    public Result<OperationTopicDTO> updateTopic(@PathVariable Long topicId,
                                                 @Valid @RequestBody OperationTopicCmd cmd) {
        return Result.ok(operationCurationService.updateTopic(topicId, cmd, requireOps()));
    }

    @GetMapping("/admin/topics/{topicId}/preview")
    public Result<OperationTopicDTO> previewTopic(@PathVariable Long topicId) {
        requireOps();
        return Result.ok(operationCurationService.previewTopic(topicId));
    }

    @PostMapping("/admin/topics/{topicId}/preview")
    public Result<OperationTopicDTO> markPreview(@PathVariable Long topicId,
                                                 @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.markPreview(topicId, requireOps(), req == null ? null : req.getNote()));
    }

    @PostMapping("/admin/topics/{topicId}/publish")
    public Result<OperationTopicDTO> publishTopic(@PathVariable Long topicId,
                                                  @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.publishTopic(topicId, requireOpsMutation(), requireCriticalNote(req)));
    }

    @PostMapping("/admin/topics/{topicId}/offline")
    public Result<OperationTopicDTO> offlineTopic(@PathVariable Long topicId,
                                                  @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.offlineTopic(topicId, requireOpsMutation(), requireCriticalNote(req)));
    }

    @PostMapping("/admin/topics/{topicId}/rollback")
    public Result<OperationTopicDTO> rollbackTopic(@PathVariable Long topicId,
                                                   @Valid @RequestBody(required = false) NoteReq req) {
        return Result.ok(operationCurationService.rollbackTopic(topicId, requireOpsMutation(), requireCriticalNote(req)));
    }

    @PublicApi
    @GetMapping("/slots/{slotCode}")
    public Result<OperationSlotDTO> publicSlot(@PathVariable String slotCode,
                                               @RequestParam(defaultValue = "0") int limit) {
        return Result.ok(operationCurationService.getPublicSlot(slotCode, limit));
    }

    @PublicApi
    @GetMapping("/topics/{slug}")
    public Result<OperationTopicDTO> publicTopic(@PathVariable String slug) {
        return Result.ok(operationCurationService.getPublicTopic(slug));
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

    @Data
    public static class NoteReq {
        @Size(max = 500)
        private String note;
        @Size(max = 64)
        private String confirmationPhrase;
    }
}

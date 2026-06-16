package com.offerlab.community.question.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.utils.RiskConfirmation;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.question.api.dto.AiTaskDetailDTO;
import com.offerlab.community.question.api.dto.AiTaskDTO;
import com.offerlab.community.question.api.dto.AiTaskMetricsDTO;
import com.offerlab.community.question.api.dto.CompanyAliasCmd;
import com.offerlab.community.question.api.dto.CompanyAliasCandidateDTO;
import com.offerlab.community.question.api.dto.CompanyAliasDTO;
import com.offerlab.community.question.api.dto.QuestionAdminUpdateCmd;
import com.offerlab.community.question.api.dto.QuestionAdminQuery;
import com.offerlab.community.question.api.dto.QuestionDTO;
import com.offerlab.community.question.api.dto.QuestionDuplicateGroupDTO;
import com.offerlab.community.question.application.QuestionConstants;
import com.offerlab.community.question.application.QuestionFacade;
import com.offerlab.community.question.application.QuestionIndexTaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Validated
public class QuestionAdminController {
    private final QuestionFacade questionFacade;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;
    private final QuestionIndexTaskService questionIndexTaskService;

    @PostMapping("/posts/{postId}/extract-questions")
    public Result<Map<String, Long>> extractPostQuestions(@PathVariable @Positive Long postId,
                                                          @Valid @RequestBody(required = false) RemarkRequest request) {
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("postId must be positive");
        }
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        String remark = RiskConfirmation.requireHigh(request == null ? null : request.remark());
        adminAuditService.requireWritable("POST_QUESTION_EXTRACT_TASK", "AI_TASK", null);
        Long taskId = questionFacade.extractPostQuestions(postId, true);
        Map<String, Object> auditRequest = Map.of("postId", postId, "manual", true);
        Map<String, Object> result = Map.of("taskId", taskId, "postId", postId, "manual", true);
        adminAuditService.recordRequired(uid, "POST_QUESTION_EXTRACT_TASK", "AI_TASK", taskId, auditRequest, result, remark);
        return Result.ok(Map.of("taskId", taskId));
    }

    @GetMapping("/ai-tasks")
    public Result<List<AiTaskDTO>> listTasks(@RequestParam(required = false) Integer status,
                                             @RequestParam(defaultValue = "20") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionFacade.listTasks(status, limit));
    }

    @GetMapping("/ai-tasks/metrics")
    public Result<AiTaskMetricsDTO> getTaskMetrics(@RequestParam(defaultValue = "100") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionFacade.getTaskMetrics(limit));
    }

    @GetMapping("/ai-tasks/{id}")
    public Result<AiTaskDetailDTO> getTaskDetail(@PathVariable Long id) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionFacade.getTaskDetail(id));
    }

    @PostMapping("/ai-tasks/{id}/retry")
    public Result<AiTaskDTO> retryTask(@PathVariable Long id,
                                       @Valid @RequestBody(required = false) RemarkRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        String remark = RiskConfirmation.requireHigh(request == null ? null : request.remark());
        adminAuditService.requireWritable("AI_TASK_RETRY", "AI_TASK", id);
        AiTaskDTO task = questionFacade.retryTask(id);
        adminAuditService.recordRequired(uid, "AI_TASK_RETRY", "AI_TASK", id, null, task,
                remark);
        return Result.ok(task);
    }

    @PostMapping("/questions/rebuild")
    public Result<Map<String, Object>> rebuildQuestions(@RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit,
                                                        @Valid @RequestBody(required = false) RemarkRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        String remark = RiskConfirmation.requireCritical(request == null ? null : request.remark(),
                request == null ? null : request.confirmationPhrase());
        adminAuditService.requireWritable("QUESTION_REBUILD", "QUESTION", null);
        Map<String, Object> result = questionFacade.rebuildQuestions(limit);
        adminAuditService.recordRequired(uid, "QUESTION_REBUILD", "QUESTION", null, Map.of("limit", limit), result,
                remark);
        return Result.ok(result);
    }

    @PostMapping("/questions/rebuild-index")
    public Result<Map<String, Object>> rebuildQuestionIndex(@Valid @RequestBody(required = false) RemarkRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        String remark = RiskConfirmation.requireCritical(request == null ? null : request.remark(),
                request == null ? null : request.confirmationPhrase());
        adminAuditService.requireWritable("QUESTION_INDEX_REBUILD", "QUESTION_INDEX", null);
        Map<String, Object> result = questionFacade.rebuildQuestionIndex();
        adminAuditService.recordRequired(uid, "QUESTION_INDEX_REBUILD", "QUESTION_INDEX", null, null, result,
                remark);
        return Result.ok(result);
    }

    @PostMapping("/questions/rebuild-index-task")
    @RateLimit(key = "'question:index:rebuild:' + #uid", rate = 2, per = 3600, failOpen = false)
    public Result<QuestionIndexTaskService.QuestionIndexTask> rebuildQuestionIndexTask(
            @Valid @RequestBody(required = false) RemarkRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        String remark = RiskConfirmation.requireCritical(request == null ? null : request.remark(),
                request == null ? null : request.confirmationPhrase());
        adminAuditService.requireWritable("QUESTION_INDEX_REBUILD_TASK", "QUESTION_INDEX", null);
        QuestionIndexTaskService.QuestionIndexTask task = questionIndexTaskService.submitRebuildTask(uid);
        adminAuditService.recordRequired(uid, "QUESTION_INDEX_REBUILD_TASK", "QUESTION_INDEX", task.getTaskId(),
                null, task, remark);
        return Result.ok(task);
    }

    @GetMapping("/questions/index-tasks/{taskId}")
    public Result<QuestionIndexTaskService.QuestionIndexTask> getQuestionIndexTask(@PathVariable String taskId) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionIndexTaskService.getTask(taskId));
    }

    @PostMapping("/questions/index-tasks/{taskId}/retry")
    public Result<QuestionIndexTaskService.QuestionIndexTask> retryQuestionIndexTask(
            @PathVariable String taskId,
            @Valid @RequestBody(required = false) RemarkRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        String remark = RiskConfirmation.requireHigh(request == null ? null : request.remark());
        adminAuditService.requireWritable("QUESTION_INDEX_REBUILD_TASK_RETRY", "QUESTION_INDEX", taskId);
        QuestionIndexTaskService.QuestionIndexTask task = questionIndexTaskService.retryTask(taskId);
        adminAuditService.recordRequired(uid, "QUESTION_INDEX_REBUILD_TASK_RETRY", "QUESTION_INDEX", taskId,
                null, task, remark);
        return Result.ok(task);
    }

    @GetMapping("/questions/index-tasks")
    public Result<List<QuestionIndexTaskService.QuestionIndexTask>> listQuestionIndexTasks(@RequestParam(defaultValue = "10") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionIndexTaskService.listRecentTasks(limit));
    }

    @PostMapping("/questions/{id}/review")
    public Result<Map<String, Object>> reviewQuestion(@PathVariable @Positive Long id,
                                                      @RequestParam @Min(QuestionConstants.QUESTION_PENDING)
                                                      @Max(QuestionConstants.QUESTION_HIDDEN) int status,
                                                      @Valid @RequestBody(required = false) RemarkRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        String remark = RiskConfirmation.requireHigh(request == null ? null : request.remark());
        adminAuditService.requireWritable("QUESTION_REVIEW", "QUESTION", id);
        Map<String, Object> result = questionFacade.reviewQuestion(id, status);
        adminAuditService.recordRequired(uid, "QUESTION_REVIEW", "QUESTION", id, null, result,
                remark);
        return Result.ok(result);
    }

    @PostMapping("/questions/batch-review")
    public Result<Map<String, Object>> batchReviewQuestions(@Valid @RequestBody QuestionBatchReviewRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        List<Long> ids = request.ids().stream()
                .distinct()
                .toList();
        int status = request.status();
        String remark = RiskConfirmation.requireCritical(request.remark(), request.confirmationPhrase());
        adminAuditService.requireWritable("QUESTION_REVIEW_BATCH", "QUESTION", null);
        List<Map<String, Object>> reviewed = ids.stream()
                .map(id -> questionFacade.reviewQuestion(id, status))
                .toList();
        Map<String, Object> result = Map.of(
                "requested", ids.size(),
                "reviewed", reviewed.size(),
                "status", status
        );
        adminAuditService.recordRequired(uid, "QUESTION_REVIEW_BATCH", "QUESTION", null,
                Map.of("ids", ids, "status", status), result, remark);
        return Result.ok(result);
    }

    @GetMapping("/questions")
    public Result<List<QuestionDTO>> listQuestions(@RequestParam(required = false) Integer status,
                                                  @RequestParam(defaultValue = "30") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionFacade.listAdminQuestions(status, limit));
    }

    @GetMapping("/questions/page")
    public Result<PageResult<QuestionDTO>> pageQuestions(@RequestParam(required = false) Integer status,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String company,
                                                        @RequestParam(required = false) String position,
                                                        @RequestParam(required = false) Integer minQualityScore,
                                                        @RequestParam(required = false) Integer maxQualityScore,
                                                        @RequestParam(required = false) Long sourcePostId,
                                                        @RequestParam(required = false) Integer taskStatus,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "30") int pageSize) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        QuestionAdminQuery query = new QuestionAdminQuery();
        query.setStatus(status);
        query.setKeyword(keyword);
        query.setCompany(company);
        query.setPosition(position);
        query.setMinQualityScore(minQualityScore);
        query.setMaxQualityScore(maxQualityScore);
        query.setSourcePostId(sourcePostId);
        query.setTaskStatus(taskStatus);
        query.setPage(page);
        query.setPageSize(pageSize);
        return Result.ok(questionFacade.pageAdminQuestions(query));
    }

    @GetMapping("/questions/summary")
    public Result<Map<String, Long>> questionSummary() {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionFacade.questionAdminSummary());
    }

    @PostMapping("/questions/{id}")
    public Result<QuestionDTO> updateQuestion(@PathVariable @Positive Long id,
                                              @Valid @RequestBody(required = false) QuestionAdminUpdateCmd cmd) {
        if (cmd == null || !cmd.hasEditableField()) {
            throw new IllegalArgumentException("question update body must contain editable field");
        }
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        String remark = RiskConfirmation.requireHigh(cmd.getRemark());
        adminAuditService.requireWritable("QUESTION_UPDATE", "QUESTION", id);
        QuestionDTO dto = questionFacade.updateQuestionAdmin(id, cmd);
        adminAuditService.recordRequired(uid, "QUESTION_UPDATE", "QUESTION", id, null, dto,
                remark);
        return Result.ok(dto);
    }

    @GetMapping("/questions/{id}/duplicates")
    public Result<QuestionDuplicateGroupDTO> getQuestionDuplicateGroup(@PathVariable Long id) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionFacade.getDuplicateGroup(id));
    }

    @PostMapping("/questions/{id}/duplicates/canonical")
    public Result<QuestionDuplicateGroupDTO> setQuestionDuplicateCanonical(@PathVariable Long id,
                                                                           @Valid @RequestBody(required = false) QuestionCanonicalRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        Long canonicalQuestionId = request == null ? null : request.canonicalQuestionId();
        String remark = RiskConfirmation.requireCritical(request == null ? null : request.remark(),
                request == null ? null : request.confirmationPhrase());
        adminAuditService.requireWritable("QUESTION_DUPLICATE_CANONICAL", "QUESTION", id);
        QuestionDuplicateGroupDTO dto = questionFacade.setDuplicateCanonical(id, canonicalQuestionId);
        adminAuditService.recordRequired(uid, "QUESTION_DUPLICATE_CANONICAL", "QUESTION", id, null,
                duplicateAuditAfter("canonicalQuestionId", canonicalQuestionId, dto),
                remark);
        return Result.ok(dto);
    }

    @PostMapping("/questions/{id}/duplicates/merge-candidate")
    public Result<QuestionDuplicateGroupDTO> mergeQuestionDuplicateCandidate(@PathVariable Long id,
                                                                             @Valid @RequestBody(required = false) QuestionDuplicateCandidateMergeRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        Long candidateQuestionId = request == null ? null : request.candidateQuestionId();
        String remark = RiskConfirmation.requireCritical(request == null ? null : request.remark(),
                request == null ? null : request.confirmationPhrase());
        adminAuditService.requireWritable("QUESTION_DUPLICATE_MERGE_CANDIDATE", "QUESTION", id);
        QuestionDuplicateGroupDTO dto = questionFacade.mergeDuplicateCandidate(id, candidateQuestionId);
        adminAuditService.recordRequired(uid, "QUESTION_DUPLICATE_MERGE_CANDIDATE", "QUESTION", id, null,
                duplicateAuditAfter("candidateQuestionId", candidateQuestionId, dto),
                remark);
        return Result.ok(dto);
    }

    @PostMapping("/questions/{id}/duplicates/hide")
    public Result<QuestionDuplicateGroupDTO> hideQuestionDuplicates(@PathVariable Long id,
                                                                   @Valid @RequestBody(required = false) QuestionDuplicateHideRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        List<Long> ids = request == null || request.ids() == null ? List.of() : request.ids();
        String remark = RiskConfirmation.requireCritical(request == null ? null : request.remark(),
                request == null ? null : request.confirmationPhrase());
        adminAuditService.requireWritable("QUESTION_DUPLICATE_HIDE", "QUESTION", id);
        QuestionDuplicateGroupDTO dto = questionFacade.hideDuplicateQuestions(id, ids);
        adminAuditService.recordRequired(uid, "QUESTION_DUPLICATE_HIDE", "QUESTION", id, Map.of("ids", ids), dto,
                remark);
        return Result.ok(dto);
    }

    @GetMapping("/company-aliases")
    public Result<List<CompanyAliasDTO>> listCompanyAliases(@RequestParam(required = false) String keyword,
                                                            @RequestParam(defaultValue = "50") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionFacade.listCompanyAliases(keyword, limit));
    }

    @GetMapping("/company-aliases/candidates")
    public Result<List<CompanyAliasCandidateDTO>> listCompanyAliasCandidates(@RequestParam(defaultValue = "20") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionFacade.listCompanyAliasCandidates(limit));
    }

    @PostMapping("/company-aliases")
    public Result<CompanyAliasDTO> createCompanyAlias(@Valid @RequestBody(required = false) CompanyAliasCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        String remark = RiskConfirmation.requireHigh(cmd == null ? null : cmd.getRemark());
        adminAuditService.requireWritable("COMPANY_ALIAS_CREATE", "COMPANY_ALIAS", null);
        CompanyAliasDTO dto = questionFacade.saveCompanyAlias(null, cmd);
        adminAuditService.recordRequired(uid, "COMPANY_ALIAS_CREATE", "COMPANY_ALIAS", dto.getId(), null, dto,
                remark);
        return Result.ok(dto);
    }

    @PostMapping("/company-aliases/{id}")
    public Result<CompanyAliasDTO> updateCompanyAlias(@PathVariable Long id, @Valid @RequestBody(required = false) CompanyAliasCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        String remark = RiskConfirmation.requireHigh(cmd == null ? null : cmd.getRemark());
        adminAuditService.requireWritable("COMPANY_ALIAS_UPDATE", "COMPANY_ALIAS", id);
        CompanyAliasDTO dto = questionFacade.saveCompanyAlias(id, cmd);
        adminAuditService.recordRequired(uid, "COMPANY_ALIAS_UPDATE", "COMPANY_ALIAS", id, null, dto,
                remark);
        return Result.ok(dto);
    }

    @PostMapping("/company-aliases/{id}/status")
    public Result<Map<String, Object>> updateCompanyAliasStatus(@PathVariable Long id,
                                                                @RequestParam int status,
                                                                @Valid @RequestBody(required = false) RemarkRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        String remark = RiskConfirmation.requireHigh(request == null ? null : request.remark());
        adminAuditService.requireWritable("COMPANY_ALIAS_STATUS", "COMPANY_ALIAS", id);
        Map<String, Object> result = questionFacade.updateCompanyAliasStatus(id, status);
        adminAuditService.recordRequired(uid, "COMPANY_ALIAS_STATUS", "COMPANY_ALIAS", id, null, result,
                remark);
        return Result.ok(result);
    }

    public record QuestionBatchReviewRequest(
            @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> ids,
            @NotNull @Min(QuestionConstants.QUESTION_PENDING) @Max(QuestionConstants.QUESTION_HIDDEN) Integer status,
            @Size(max = 500) String remark,
            @Size(max = 32) String confirmationPhrase) {
    }

    public record QuestionCanonicalRequest(
            Long canonicalQuestionId,
            @Size(max = 500) String remark,
            @Size(max = 32) String confirmationPhrase) {
    }

    public record QuestionDuplicateCandidateMergeRequest(
            Long candidateQuestionId,
            @Size(max = 500) String remark,
            @Size(max = 32) String confirmationPhrase) {
    }

    public record QuestionDuplicateHideRequest(
            List<Long> ids,
            @Size(max = 500) String remark,
            @Size(max = 32) String confirmationPhrase) {
    }

    public record RemarkRequest(@Size(max = 500) String remark,
                                @Size(max = 32) String confirmationPhrase) {
    }

    private static Map<String, Object> duplicateAuditAfter(String idKey, Long idValue, QuestionDuplicateGroupDTO dto) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put(idKey, idValue);
        after.put("group", dto);
        return after;
    }

    private static String cleanRemark(String remark) {
        if (remark == null || remark.trim().isEmpty()) {
            return null;
        }
        String value = remark.trim();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}

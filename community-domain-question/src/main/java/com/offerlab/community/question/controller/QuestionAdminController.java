package com.offerlab.community.question.controller;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.question.api.dto.AiTaskDTO;
import com.offerlab.community.question.api.dto.CompanyAliasCmd;
import com.offerlab.community.question.api.dto.CompanyAliasDTO;
import com.offerlab.community.question.api.dto.QuestionAdminUpdateCmd;
import com.offerlab.community.question.api.dto.QuestionDTO;
import com.offerlab.community.question.application.QuestionFacade;
import com.offerlab.community.question.application.QuestionIndexTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class QuestionAdminController {
    private final QuestionFacade questionFacade;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;
    private final QuestionIndexTaskService questionIndexTaskService;

    @PostMapping("/posts/{postId}/extract-questions")
    public Result<Map<String, Long>> extractPostQuestions(@PathVariable Long postId) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(Map.of("taskId", questionFacade.extractPostQuestions(postId, true)));
    }

    @GetMapping("/ai-tasks")
    public Result<List<AiTaskDTO>> listTasks(@RequestParam(required = false) Integer status,
                                             @RequestParam(defaultValue = "20") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionFacade.listTasks(status, limit));
    }

    @PostMapping("/ai-tasks/{id}/retry")
    public Result<AiTaskDTO> retryTask(@PathVariable Long id) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        AiTaskDTO task = questionFacade.retryTask(id);
        adminAuditService.record(uid, "AI_TASK_RETRY", "AI_TASK", id, null, task, null);
        return Result.ok(task);
    }

    @PostMapping("/questions/rebuild")
    public Result<Map<String, Object>> rebuildQuestions(@RequestParam(defaultValue = "100") int limit) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        Map<String, Object> result = questionFacade.rebuildQuestions(limit);
        adminAuditService.record(uid, "QUESTION_REBUILD", "QUESTION", null, Map.of("limit", limit), result, null);
        return Result.ok(result);
    }

    @PostMapping("/questions/rebuild-index")
    public Result<Map<String, Object>> rebuildQuestionIndex() {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        Map<String, Object> result = questionFacade.rebuildQuestionIndex();
        adminAuditService.record(uid, "QUESTION_INDEX_REBUILD", "QUESTION_INDEX", null, null, result, null);
        return Result.ok(result);
    }

    @PostMapping("/questions/rebuild-index-task")
    public Result<QuestionIndexTaskService.QuestionIndexTask> rebuildQuestionIndexTask() {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        QuestionIndexTaskService.QuestionIndexTask task = questionIndexTaskService.submitRebuildTask(uid);
        adminAuditService.record(uid, "QUESTION_INDEX_REBUILD_TASK", "QUESTION_INDEX", task.getTaskId(), null, task, null);
        return Result.ok(task);
    }

    @GetMapping("/questions/index-tasks/{taskId}")
    public Result<QuestionIndexTaskService.QuestionIndexTask> getQuestionIndexTask(@PathVariable String taskId) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionIndexTaskService.getTask(taskId));
    }

    @GetMapping("/questions/index-tasks")
    public Result<List<QuestionIndexTaskService.QuestionIndexTask>> listQuestionIndexTasks(@RequestParam(defaultValue = "10") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionIndexTaskService.listRecentTasks(limit));
    }

    @PostMapping("/questions/{id}/review")
    public Result<Map<String, Object>> reviewQuestion(@PathVariable Long id, @RequestParam int status) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        Map<String, Object> result = questionFacade.reviewQuestion(id, status);
        adminAuditService.record(uid, "QUESTION_REVIEW", "QUESTION", id, null, result, null);
        return Result.ok(result);
    }

    @PostMapping("/questions/batch-review")
    public Result<Map<String, Object>> batchReviewQuestions(@RequestBody QuestionBatchReviewRequest request) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        List<Long> ids = request == null || request.ids() == null ? List.of() : request.ids().stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .limit(100)
                .toList();
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int status = request.status();
        List<Map<String, Object>> reviewed = ids.stream()
                .map(id -> questionFacade.reviewQuestion(id, status))
                .toList();
        Map<String, Object> result = Map.of(
                "requested", ids.size(),
                "reviewed", reviewed.size(),
                "status", status
        );
        adminAuditService.record(uid, "QUESTION_REVIEW_BATCH", "QUESTION", null,
                Map.of("ids", ids, "status", status), result, null);
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
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "30") int pageSize) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionFacade.pageAdminQuestions(status, page, pageSize));
    }

    @GetMapping("/questions/summary")
    public Result<Map<String, Long>> questionSummary() {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionFacade.questionAdminSummary());
    }

    @PostMapping("/questions/{id}")
    public Result<QuestionDTO> updateQuestion(@PathVariable Long id, @RequestBody QuestionAdminUpdateCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        QuestionDTO dto = questionFacade.updateQuestionAdmin(id, cmd);
        adminAuditService.record(uid, "QUESTION_UPDATE", "QUESTION", id, null, dto, null);
        return Result.ok(dto);
    }

    @GetMapping("/company-aliases")
    public Result<List<CompanyAliasDTO>> listCompanyAliases(@RequestParam(required = false) String keyword,
                                                            @RequestParam(defaultValue = "50") int limit) {
        adminPermissionService.requireScope(UserContext.require(), AdminPermissionService.ROLE_QUESTION_OPERATOR);
        return Result.ok(questionFacade.listCompanyAliases(keyword, limit));
    }

    @PostMapping("/company-aliases")
    public Result<CompanyAliasDTO> createCompanyAlias(@RequestBody CompanyAliasCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        CompanyAliasDTO dto = questionFacade.saveCompanyAlias(null, cmd);
        adminAuditService.record(uid, "COMPANY_ALIAS_CREATE", "COMPANY_ALIAS", dto.getId(), null, dto, null);
        return Result.ok(dto);
    }

    @PostMapping("/company-aliases/{id}")
    public Result<CompanyAliasDTO> updateCompanyAlias(@PathVariable Long id, @RequestBody CompanyAliasCmd cmd) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        CompanyAliasDTO dto = questionFacade.saveCompanyAlias(id, cmd);
        adminAuditService.record(uid, "COMPANY_ALIAS_UPDATE", "COMPANY_ALIAS", id, null, dto, null);
        return Result.ok(dto);
    }

    @PostMapping("/company-aliases/{id}/status")
    public Result<Map<String, Object>> updateCompanyAliasStatus(@PathVariable Long id, @RequestParam int status) {
        Long uid = UserContext.require();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_QUESTION_OPERATOR);
        Map<String, Object> result = questionFacade.updateCompanyAliasStatus(id, status);
        adminAuditService.record(uid, "COMPANY_ALIAS_STATUS", "COMPANY_ALIAS", id, null, result, null);
        return Result.ok(result);
    }

    public record QuestionBatchReviewRequest(List<Long> ids, int status) {
    }
}

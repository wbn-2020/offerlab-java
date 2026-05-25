package com.offerlab.community.question.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.question.api.dto.CompanyPrepDTO;
import com.offerlab.community.question.api.dto.PrepTargetCmd;
import com.offerlab.community.question.api.dto.PrepTargetDTO;
import com.offerlab.community.question.api.dto.QuestionDTO;
import com.offerlab.community.question.api.dto.QuestionDetailDTO;
import com.offerlab.community.question.api.dto.QuestionQuery;
import com.offerlab.community.question.api.dto.UserPrepOverviewDTO;
import com.offerlab.community.question.application.QuestionFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QuestionController {
    private final QuestionFacade questionFacade;

    @PublicApi
    @GetMapping("/questions")
    public Result<PageResult<QuestionDTO>> list(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String company,
                                                @RequestParam(required = false) String position,
                                                @RequestParam(required = false) String difficulty,
                                                @RequestParam(required = false) String round,
                                                @RequestParam(required = false) List<Long> tagIds,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                @RequestParam(required = false) String sort,
                                                @RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "20") Integer pageSize) {
        QuestionQuery query = new QuestionQuery();
        query.setKeyword(keyword);
        query.setCompany(company);
        query.setPosition(position);
        query.setDifficulty(difficulty);
        query.setRound(round);
        query.setTagIds(tagIds);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setSort(sort);
        query.setPage(page);
        query.setPageSize(pageSize);
        return Result.ok(questionFacade.searchQuestions(query, UserContext.get()));
    }

    @PublicApi
    @GetMapping("/questions/{id}")
    public Result<QuestionDetailDTO> detail(@PathVariable Long id) {
        return Result.ok(questionFacade.getQuestionDetail(id, UserContext.get(), false));
    }

    @PostMapping("/questions/{id}/favorite")
    @RateLimit(key = "'question:favorite:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> favorite(@PathVariable Long id) {
        return Result.ok(questionFacade.favorite(id, UserContext.require(), true));
    }

    @DeleteMapping("/questions/{id}/favorite")
    @RateLimit(key = "'question:unfavorite:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> unfavorite(@PathVariable Long id) {
        return Result.ok(questionFacade.favorite(id, UserContext.require(), false));
    }

    @PutMapping("/questions/{id}/progress")
    @RateLimit(key = "'question:progress:' + #uid", rate = 120, per = 60)
    public Result<Map<String, Object>> progress(@PathVariable Long id, @RequestBody ProgressReq req) {
        return Result.ok(questionFacade.updateProgress(id, UserContext.require(), req.getStatus()));
    }

    @PublicApi
    @GetMapping("/questions/{id}/related-posts")
    public Result<List<PostBriefDTO>> relatedPosts(@PathVariable Long id) {
        return Result.ok(questionFacade.getRelatedPosts(id));
    }

    @PublicApi
    @GetMapping("/companies/{company}/prep-pack")
    public Result<CompanyPrepDTO> companyPrep(@PathVariable String company) {
        return Result.ok(questionFacade.getCompanyPrep(company, UserContext.get()));
    }

    @PublicApi
    @GetMapping("/companies/suggest")
    public Result<List<String>> suggestCompanies(@RequestParam(required = false, name = "q") String q,
                                                 @RequestParam(defaultValue = "10") int size) {
        return Result.ok(questionFacade.suggestCompanies(q, size));
    }

    @GetMapping("/me/prep/overview")
    public Result<UserPrepOverviewDTO> myPrepOverview() {
        return Result.ok(questionFacade.getMyPrepOverview(UserContext.require()));
    }

    @GetMapping("/me/prep/targets")
    public Result<List<PrepTargetDTO>> myPrepTargets() {
        return Result.ok(questionFacade.listPrepTargets(UserContext.require()));
    }

    @PostMapping("/me/prep/targets")
    @RateLimit(key = "'prep:target:add:' + #uid", rate = 30, per = 60)
    public Result<PrepTargetDTO> addPrepTarget(@RequestBody PrepTargetCmd cmd) {
        return Result.ok(questionFacade.addPrepTarget(UserContext.require(), cmd));
    }

    @DeleteMapping("/me/prep/targets/{id}")
    @RateLimit(key = "'prep:target:delete:' + #uid", rate = 30, per = 60)
    public Result<Map<String, Object>> deletePrepTarget(@PathVariable Long id) {
        return Result.ok(questionFacade.deletePrepTarget(UserContext.require(), id));
    }

    @Data
    public static class ProgressReq {
        private String status;
    }
}

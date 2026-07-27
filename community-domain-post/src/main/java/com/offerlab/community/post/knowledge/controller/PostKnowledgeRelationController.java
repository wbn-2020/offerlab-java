package com.offerlab.community.post.knowledge.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.knowledge.api.KnowledgeThreadDTO;
import com.offerlab.community.post.knowledge.api.PostKnowledgeRelationCreateCmd;
import com.offerlab.community.post.knowledge.api.PostKnowledgeRelationDTO;
import com.offerlab.community.post.knowledge.application.PostKnowledgeRelationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts/{postId}/knowledge-relations")
@RequiredArgsConstructor
@Validated
public class PostKnowledgeRelationController {

    private final PostKnowledgeRelationService service;

    @PostMapping
    @RateLimit(key = "'post:knowledge-relation:propose:' + #uid + ':' + #postId",
            rate = 20, per = 300, failOpen = false)
    public Result<PostKnowledgeRelationDTO> propose(
            @PathVariable @Positive Long postId,
            @Valid @RequestBody PostKnowledgeRelationCreateCmd cmd) {
        return Result.ok(service.propose(postId, cmd, UserContext.require()));
    }

    @PublicApi
    @GetMapping
    @RateLimit(key = "'public:post:knowledge-relations:' + #postId + ':' + #request.remoteAddr",
            rate = 120, per = 60, failOpen = false)
    public Result<List<PostKnowledgeRelationDTO>> listPublic(
            @PathVariable @Positive Long postId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            HttpServletRequest request) {
        return Result.ok(service.listPublic(postId, limit));
    }

    @PublicApi
    @GetMapping("/thread")
    @RateLimit(key = "'public:post:knowledge-thread:' + #postId + ':' + #request.remoteAddr",
            rate = 120, per = 60, failOpen = false)
    public Result<KnowledgeThreadDTO> readingThread(
            @PathVariable @Positive Long postId,
            HttpServletRequest request) {
        return Result.ok(service.readingThread(postId));
    }
}

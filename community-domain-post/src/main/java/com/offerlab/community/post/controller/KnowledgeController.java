package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.post.api.dto.KnowledgeRelationGraphDTO;
import com.offerlab.community.post.application.KnowledgeRelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeRelationService knowledgeRelationService;

    @PublicApi
    @GetMapping("/relations")
    public Result<KnowledgeRelationGraphDTO> relations(@RequestParam(required = false) Long postId,
                                                       @RequestParam(required = false) Long tagId,
                                                       @RequestParam(required = false) Long topicId,
                                                       @RequestParam(required = false) Integer domain,
                                                       @RequestParam(defaultValue = "8") int limit) {
        return Result.ok(knowledgeRelationService.explore(postId, tagId, topicId, domain, limit));
    }
}

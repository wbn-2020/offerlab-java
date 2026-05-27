package com.offerlab.community.question.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.question.api.dto.PostQuestionBlockDTO;
import com.offerlab.community.question.application.QuestionFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostQuestionController {
    private final QuestionFacade questionFacade;
    private final AdminPermissionService adminPermissionService;

    @PublicApi
    @GetMapping("/{postId}/questions")
    public Result<PostQuestionBlockDTO> postQuestions(@PathVariable Long postId) {
        Long viewerUid = UserContext.get();
        boolean admin = viewerUid != null && adminPermissionService.isAdmin(viewerUid);
        return Result.ok(questionFacade.getPostQuestionBlock(postId, viewerUid, admin));
    }
}

package com.offerlab.community.post.reference.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.dto.PostReferenceCreateCmd;
import com.offerlab.community.post.api.dto.PostReferenceDTO;
import com.offerlab.community.post.api.dto.PostReferenceReorderCmd;
import com.offerlab.community.post.api.dto.PostReferenceUpdateCmd;
import com.offerlab.community.post.reference.application.PostReferenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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

@RestController
@RequestMapping("/api/v1/posts/{postId}/references")
@RequiredArgsConstructor
@Validated
public class PostReferenceController {

    private final PostReferenceService referenceService;

    @PublicApi
    @GetMapping
    @RateLimit(key = "'public:post-references:list:' + #postId + ':' + #request.remoteAddr",
            rate = 180, per = 60, failOpen = false)
    public Result<List<PostReferenceDTO>> list(@PathVariable @Positive Long postId,
                                               HttpServletRequest request) {
        return Result.ok(referenceService.listPublic(postId));
    }

    @PostMapping
    @RateLimit(key = "'post-references:create:' + #uid", rate = 60, per = 300, failOpen = false)
    public Result<PostReferenceDTO> create(@PathVariable @Positive Long postId,
                                           @Valid @RequestBody PostReferenceCreateCmd cmd) {
        return Result.ok(referenceService.create(postId, cmd, UserContext.require()));
    }

    @PutMapping("/{referenceId}")
    @RateLimit(key = "'post-references:update:' + #uid", rate = 60, per = 300, failOpen = false)
    public Result<PostReferenceDTO> update(@PathVariable @Positive Long postId,
                                           @PathVariable @Positive Long referenceId,
                                           @Valid @RequestBody PostReferenceUpdateCmd cmd) {
        return Result.ok(referenceService.update(postId, referenceId, cmd, UserContext.require()));
    }

    @DeleteMapping("/{referenceId}")
    @RateLimit(key = "'post-references:delete:' + #uid", rate = 60, per = 300, failOpen = false)
    public Result<Void> delete(@PathVariable @Positive Long postId,
                               @PathVariable @Positive Long referenceId,
                               @RequestParam @Positive Integer revision) {
        referenceService.delete(postId, referenceId, revision, UserContext.require());
        return Result.ok();
    }

    @PutMapping("/reorder")
    @RateLimit(key = "'post-references:reorder:' + #uid", rate = 30, per = 300, failOpen = false)
    public Result<List<PostReferenceDTO>> reorder(@PathVariable @Positive Long postId,
                                                  @Valid @RequestBody PostReferenceReorderCmd cmd) {
        return Result.ok(referenceService.reorder(postId, cmd, UserContext.require()));
    }
}

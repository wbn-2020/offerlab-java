package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostUpdateCmd;
import com.offerlab.community.post.application.PostApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

import java.util.Map;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostFacade postFacade;
    private final PostApplicationService postService;

    @PostMapping
    @RateLimit(key = "'post:create:' + #uid", rate = 20, per = 86400)
    public Result<Map<String, Long>> publish(@Valid @RequestBody PublishReq req) {
        Long uid = UserContext.require();
        Long id = postFacade.publishPost(PostCreateCmd.builder()
                .authorId(uid)
                .postType(req.getPostType())
                .title(req.getTitle())
                .content(req.getContent())
                .coverUrl(req.getCoverUrl())
                .visibility(req.getVisibility())
                .extJson(req.getExtJson())
                .build());
        return Result.ok(Map.of("postId", id));
    }

    @PutMapping("/{postId}")
    public Result<Void> update(@PathVariable Long postId, @RequestBody UpdateReq req) {
        Long uid = UserContext.require();
        postFacade.updatePost(PostUpdateCmd.builder()
                .postId(postId)
                .operatorUid(uid)
                .title(req.getTitle())
                .content(req.getContent())
                .coverUrl(req.getCoverUrl())
                .visibility(req.getVisibility())
                .extJson(req.getExtJson())
                .build());
        return Result.ok();
    }

    @DeleteMapping("/{postId}")
    public Result<Void> delete(@PathVariable Long postId) {
        postFacade.deletePost(postId, UserContext.require());
        return Result.ok();
    }

    @PublicApi
    @GetMapping("/{postId}")
    public Result<PostDTO> get(@PathVariable Long postId) {
        PostDTO p = postFacade.getPost(postId);
        if (p != null) {
            postService.incrView(postId);
        }
        return Result.ok(p);
    }

    @PublicApi
    @GetMapping
    public Result<PageResult<PostBriefDTO>> list(@RequestParam(required = false) Long authorId,
                                                 @RequestParam(defaultValue = "0") long cursor,
                                                 @RequestParam(defaultValue = "20") int size) {
        if (authorId != null) {
            return Result.ok(postFacade.getPostsByAuthor(authorId, cursor, size));
        }
        return Result.ok(postFacade.getLatest(cursor, size));
    }

    @Data
    public static class PublishReq {
        @NotNull
        private Integer postType;
        @NotBlank
        @Size(max = 255)
        private String title;
        @NotBlank
        private String content;
        private String coverUrl;
        private Integer visibility;
        private String extJson;
    }

    @Data
    public static class UpdateReq {
        private String title;
        private String content;
        private String coverUrl;
        private Integer visibility;
        private String extJson;
    }
}

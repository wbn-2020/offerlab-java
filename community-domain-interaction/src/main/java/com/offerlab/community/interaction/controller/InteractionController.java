package com.offerlab.community.interaction.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.interaction.api.InteractionFacade;
import com.offerlab.community.interaction.api.dto.CommentCreateCmd;
import com.offerlab.community.interaction.api.dto.CommentDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class InteractionController {

    private final InteractionFacade facade;

    @PostMapping("/posts/{postId}/like")
    @RateLimit(key = "'like:' + #uid", rate = 60, per = 60)
    public Result<Map<String, Object>> like(@PathVariable Long postId) {
        Long uid = UserContext.require();
        facade.like(uid, postId);
        return Result.ok(Map.of("liked", true));
    }

    @DeleteMapping("/posts/{postId}/like")
    public Result<Map<String, Object>> unlike(@PathVariable Long postId) {
        Long uid = UserContext.require();
        facade.unlike(uid, postId);
        return Result.ok(Map.of("liked", false));
    }

    @GetMapping("/users/me/liked-posts")
    public Result<PageResult<PostBriefDTO>> likedPosts(@RequestParam(defaultValue = "0") long cursor,
                                                       @RequestParam(defaultValue = "20") int size) {
        return Result.ok(facade.listLikedPosts(UserContext.require(), cursor, size));
    }

    @PostMapping("/posts/{postId}/favorite")
    public Result<Void> favorite(@PathVariable Long postId) {
        facade.favorite(UserContext.require(), postId);
        return Result.ok();
    }

    @DeleteMapping("/posts/{postId}/favorite")
    public Result<Void> unfavorite(@PathVariable Long postId) {
        facade.unfavorite(UserContext.require(), postId);
        return Result.ok();
    }

    @GetMapping("/users/me/favorite-posts")
    public Result<PageResult<PostBriefDTO>> favoritePosts(@RequestParam(defaultValue = "0") long cursor,
                                                         @RequestParam(defaultValue = "20") int size) {
        return Result.ok(facade.listFavoritePosts(UserContext.require(), cursor, size));
    }

    @PostMapping("/posts/{postId}/comments")
    @RateLimit(key = "'comment:' + #uid", rate = 30, per = 60)
    public Result<Map<String, Long>> comment(@PathVariable Long postId, @Valid @RequestBody CommentReq req) {
        Long uid = UserContext.require();
        Long id = facade.addComment(CommentCreateCmd.builder()
                .postId(postId)
                .authorUid(uid)
                .parentId(req.getParentId())
                .replyToUid(req.getReplyToUid())
                .content(req.getContent())
                .build());
        return Result.ok(Map.of("commentId", id));
    }

    @PublicApi
    @GetMapping("/posts/{postId}/comments")
    public Result<PageResult<CommentDTO>> comments(@PathVariable Long postId,
                                                   @RequestParam(defaultValue = "0") long cursor,
                                                   @RequestParam(defaultValue = "20") int size) {
        return Result.ok(facade.listComments(postId, cursor, size));
    }

    @DeleteMapping("/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        facade.deleteComment(commentId, UserContext.require());
        return Result.ok();
    }

    @PostMapping("/comments/{commentId}/like")
    public Result<Map<String, Object>> likeComment(@PathVariable Long commentId) {
        facade.likeComment(UserContext.require(), commentId);
        return Result.ok(Map.of("liked", true));
    }

    @DeleteMapping("/comments/{commentId}/like")
    public Result<Map<String, Object>> unlikeComment(@PathVariable Long commentId) {
        facade.unlikeComment(UserContext.require(), commentId);
        return Result.ok(Map.of("liked", false));
    }

    @Data
    public static class CommentReq {
        private Long parentId;
        private Long replyToUid;
        @NotBlank
        @Size(max = 2000)
        private String content;
    }
}

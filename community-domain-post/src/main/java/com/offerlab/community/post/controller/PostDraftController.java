package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.dto.PostDraftCmd;
import com.offerlab.community.post.api.dto.PostDraftDTO;
import com.offerlab.community.post.application.PostDraftService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
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
import java.util.Map;

@RestController
@RequestMapping("/api/v1/post-drafts")
@RequiredArgsConstructor
@Validated
public class PostDraftController {
    private final PostDraftService draftService;

    @GetMapping
    public Result<List<PostDraftDTO>> list(@RequestParam(defaultValue = "10") @Min(1) @Max(30) int limit) {
        return Result.ok(draftService.listRecent(UserContext.require(), limit));
    }

    @GetMapping("/{id}")
    public Result<PostDraftDTO> get(@PathVariable Long id) {
        return Result.ok(draftService.get(UserContext.require(), id));
    }

    @GetMapping("/latest")
    public Result<PostDraftDTO> latestBySource(@RequestParam Long sourcePostId) {
        return Result.ok(draftService.latestBySourcePost(UserContext.require(), sourcePostId));
    }

    @PostMapping
    @RateLimit(key = "'post-draft:save:' + #uid", rate = 120, per = 3600)
    public Result<PostDraftDTO> create(@Valid @RequestBody DraftReq req) {
        Long uid = UserContext.require();
        return Result.ok(draftService.save(toCmd(req, null, uid)));
    }

    @PutMapping("/{id}")
    @RateLimit(key = "'post-draft:update:' + #uid", rate = 120, per = 3600)
    public Result<PostDraftDTO> update(@PathVariable Long id, @Valid @RequestBody DraftReq req) {
        Long uid = UserContext.require();
        return Result.ok(draftService.save(toCmd(req, id, uid)));
    }

    @DeleteMapping("/{id}")
    @RateLimit(key = "'post-draft:delete:' + #uid", rate = 60, per = 3600)
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        boolean deleted = draftService.delete(UserContext.require(), id);
        return Result.ok(Map.of("id", id, "deleted", deleted));
    }

    private PostDraftCmd toCmd(DraftReq req, Long id, Long uid) {
        return PostDraftCmd.builder()
                .id(id == null ? req.getId() : id)
                .uid(uid)
                .sourcePostId(req.getSourcePostId())
                .postType(req.getPostType())
                .title(req.getTitle())
                .content(req.getContent())
                .coverUrl(req.getCoverUrl())
                .visibility(req.getVisibility())
                .extJson(req.getExtJson())
                .tagIds(req.getTagIds() != null ? req.getTagIds() : req.getTags())
                .tagNames(req.getTagNames())
                .build();
    }

    @Data
    public static class DraftReq {
        private Long id;
        private Long sourcePostId;
        private Integer postType;
        @Size(max = 255)
        private String title;
        @Size(max = 20000)
        private String content;
        @Size(max = 512)
        private String coverUrl;
        private Integer visibility;
        @Size(max = 20000)
        private String extJson;
        private List<Long> tags;
        private List<Long> tagIds;
        private List<String> tagNames;
    }
}

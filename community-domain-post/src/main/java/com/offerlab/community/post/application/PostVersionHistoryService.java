package com.offerlab.community.post.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.dto.PostVersionHistoryDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostVersionHistoryMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostVersionHistoryPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostVersionHistoryService {
    private static final int CONTENT_SUMMARY_LEN = 180;
    private static final TypeReference<List<TagDTO>> TAG_LIST_TYPE = new TypeReference<>() {};

    private final PostVersionHistoryMapper versionMapper;
    private final SnowflakeIdGenerator idGen;
    private final ObjectMapper objectMapper;

    public void snapshotBeforeUpdate(Post current, Long editorUid, List<TagDTO> currentTags, Integer baseVersion,
                                     String nextTitle, String nextContent, String nextCoverUrl, Integer nextVisibility,
                                     String nextExtJson, List<Long> nextTagIds, boolean tagsProvided) {
        if (current == null || current.getId() == null) {
            return;
        }
        try {
            if (versionMapper.tableExists() <= 0) {
                log.warn("post version history table missing, skip snapshot postId={}", current.getId());
                return;
            }
            String changeSummary = changeSummary(current, nextTitle, nextContent, nextCoverUrl, nextVisibility,
                    nextExtJson, nextTagIds, tagsProvided);
            if ("no-op".equals(changeSummary)) {
                return;
            }

            PostVersionHistoryPO po = new PostVersionHistoryPO();
            po.setId(idGen.nextId());
            po.setPostId(current.getId());
            po.setAuthorId(current.getAuthorId());
            po.setEditorUid(editorUid);
            po.setBaseVersion(baseVersion == null ? 0 : baseVersion);
            po.setPostType(current.getPostType());
            po.setTitle(current.getTitle());
            po.setContent(current.getContent());
            po.setCoverUrl(current.getCoverUrl());
            po.setVisibility(current.getVisibility());
            po.setPostStatus(current.getPostStatus());
            po.setExtJson(current.getExtJson());
            po.setTagSnapshotJson(writeTags(currentTags));
            po.setChangeSummary(changeSummary);
            versionMapper.insert(po);
        } catch (Exception e) {
            log.warn("post version history snapshot failed, postId={}", current.getId(), e);
        }
    }

    public List<PostVersionHistoryDTO> listRecent(Long postId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 10 : limit, 30));
        return versionMapper.selectRecentByPost(postId, safeLimit).stream().map(this::toDto).toList();
    }

    private String writeTags(List<TagDTO> tags) {
        try {
            return objectMapper.writeValueAsString(tags == null ? List.of() : tags);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<TagDTO> readTags(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, TAG_LIST_TYPE);
        } catch (Exception e) {
            log.warn("post version tag snapshot parse failed", e);
            return List.of();
        }
    }

    private PostVersionHistoryDTO toDto(PostVersionHistoryPO po) {
        return PostVersionHistoryDTO.builder()
                .id(po.getId())
                .postId(po.getPostId())
                .authorId(po.getAuthorId())
                .editorUid(po.getEditorUid())
                .baseVersion(po.getBaseVersion())
                .title(po.getTitle())
                .content(po.getContent())
                .contentSummary(summary(po.getContent()))
                .coverUrl(po.getCoverUrl())
                .visibility(po.getVisibility())
                .postStatus(po.getPostStatus())
                .extJson(po.getExtJson())
                .tags(readTags(po.getTagSnapshotJson()))
                .changeSummary(po.getChangeSummary())
                .createTime(po.getCreateTime())
                .build();
    }

    private String changeSummary(Post current, String nextTitle, String nextContent, String nextCoverUrl,
                                 Integer nextVisibility, String nextExtJson, List<Long> nextTagIds, boolean tagsProvided) {
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(current.getTitle(), nextTitle)) fields.add("title");
        if (!Objects.equals(current.getContent(), nextContent)) fields.add("content");
        if (!Objects.equals(current.getCoverUrl(), nextCoverUrl)) fields.add("coverUrl");
        if (!Objects.equals(current.getVisibility(), nextVisibility)) fields.add("visibility");
        if (!Objects.equals(current.getExtJson(), nextExtJson)) fields.add("extension");
        if (tagsProvided) fields.add("tags");
        if (fields.isEmpty()) {
            return "no-op";
        }
        return String.join(",", fields);
    }

    private String summary(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= CONTENT_SUMMARY_LEN ? normalized : normalized.substring(0, CONTENT_SUMMARY_LEN) + "...";
    }
}

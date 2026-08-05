package com.offerlab.community.post.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.dto.PostVersionHistoryDTO;
import com.offerlab.community.post.api.dto.PublicPostUpdateDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostVersionHistoryMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostVersionHistoryPO;
import com.offerlab.community.post.infrastructure.persistence.projection.PostContentRevisionCandidateRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostVersionHistoryService {
    private static final int CONTENT_SUMMARY_LEN = 180;
    private static final int MAX_PUBLIC_UPDATE_SUMMARY_LEN = 500;
    private static final int MAX_IMPACT_SCOPE_LEN = 255;
    private static final int MAX_HISTORY_LIMIT = 30;
    private static final TypeReference<List<TagDTO>> TAG_LIST_TYPE = new TypeReference<>() {};

    private final PostVersionHistoryMapper versionMapper;
    private final SnowflakeIdGenerator idGen;
    private final ObjectMapper objectMapper;

    public ContentRevisionCandidate snapshotBeforeUpdate(Post current, Long editorUid, List<TagDTO> currentTags,
                                                         Integer baseVersion, String nextTitle, String nextContent,
                                                         String nextCoverUrl, Integer nextVisibility, String nextExtJson,
                                                         List<Long> nextTagIds, boolean tagsProvided,
                                                         String publicUpdateSummary, String impactScope,
                                                         boolean forceSnapshot, boolean qualityRevisionCandidate) {
        if (current == null || current.getId() == null) {
            return null;
        }
        boolean requiredHistory = qualityRevisionCandidate;
        if (forceSnapshot) {
            requiredHistory = true;
        }
        try {
            if (versionMapper.tableExists() <= 0) {
                if (requiredHistory) {
                    throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                            "post version history is required for trusted content updates");
                }
                log.warn("post version history table missing, skip snapshot postId={}", current.getId());
                return null;
            }
            if (qualityRevisionCandidate) {
                requireQualitySignalSchema();
            }
            String changeSummary = changeSummary(current, nextTitle, nextContent, nextCoverUrl, nextVisibility,
                    nextExtJson, nextTagIds, tagsProvided);
            if ("no-op".equals(changeSummary) && !requiredHistory) {
                return null;
            }
            if ("no-op".equals(changeSummary)) {
                changeSummary = qualityRevisionCandidate ? "quality-content-revision" : "trusted-content";
            }

            PostVersionHistoryPO po = new PostVersionHistoryPO();
            po.setId(idGen.nextId());
            po.setPostId(current.getId());
            po.setAuthorId(current.getAuthorId());
            po.setEditorUid(editorUid);
            po.setBaseVersion(baseVersion == null ? 0 : baseVersion);
            po.setResultVersion((baseVersion == null ? 0 : baseVersion) + 1);
            po.setPostType(current.getPostType());
            po.setTitle(current.getTitle());
            po.setContent(current.getContent());
            po.setCoverUrl(current.getCoverUrl());
            po.setVisibility(current.getVisibility());
            po.setPostStatus(current.getPostStatus());
            po.setExtJson(current.getExtJson());
            po.setTagSnapshotJson(writeTags(currentTags));
            po.setChangeSummary(changeSummary);
            po.setPublicUpdateSummary(boundedOptional(publicUpdateSummary, MAX_PUBLIC_UPDATE_SUMMARY_LEN));
            po.setImpactScope(boundedOptional(impactScope, MAX_IMPACT_SCOPE_LEN));
            ContentRevisionCandidate candidate = null;
            if (qualityRevisionCandidate) {
                String revisionToken = UUID.randomUUID().toString();
                po.setQualitySignalRevision(po.getResultVersion());
                po.setQualitySignalRevisionState("CANDIDATE");
                po.setQualitySignalRevisionToken(revisionToken);
                candidate = new ContentRevisionCandidate(po.getPostId(), po.getResultVersion(), revisionToken);
            }
            versionMapper.insert(po);
            if (candidate != null) {
                versionMapper.supersedeOtherPendingQualityRevisions(candidate.postId(), candidate.resultVersion());
            }
            return candidate;
        } catch (Exception e) {
            if (requiredHistory) {
                if (e instanceof BizException bizException) {
                    throw bizException;
                }
                throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                        qualityRevisionCandidate
                                ? "effective content revision boundary is unavailable"
                                : "post version history snapshot is unavailable");
            }
            log.warn("post version history snapshot failed, postId={}", current.getId(), e);
            return null;
        }
    }

    public boolean isEligiblePublicTextRevision(Post current, String nextTitle, String nextContent,
                                                Integer nextVisibility) {
        if (current == null
                || !Objects.equals(current.getPostStatus(), Post.STATUS_PUBLISHED)
                || !isPublicVisibility(current.getVisibility())
                || !isPublicVisibility(nextVisibility)) {
            return false;
        }
        return !Objects.equals(normalizePublicText(current.getTitle()), normalizePublicText(nextTitle))
                || !Objects.equals(normalizePublicText(current.getContent()), normalizePublicText(nextContent));
    }

    public void activateDirectPublicRevision(ContentRevisionCandidate candidate) {
        if (candidate == null) {
            return;
        }
        activateCandidate(candidate);
    }

    public void resolvePendingPublicRevision(Long postId, Integer resultVersion, boolean approved) {
        if (postId == null || postId <= 0 || resultVersion == null || resultVersion < 0) {
            return;
        }
        if (!qualitySignalSchemaReady()) {
            // A candidate could not have been written without the same readiness check.
            return;
        }
        PostContentRevisionCandidateRow row = versionMapper.selectPendingQualityRevision(postId, resultVersion);
        if (row == null || !StringUtils.hasText(row.getRevisionToken())) {
            return;
        }
        ContentRevisionCandidate candidate = new ContentRevisionCandidate(
                row.getPostId(), row.getResultVersion(), row.getRevisionToken());
        if (approved) {
            activateCandidate(candidate);
            return;
        }
        if (versionMapper.rejectQualityRevision(candidate.postId(), candidate.resultVersion(),
                candidate.revisionToken()) != 1) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                    "effective content revision rejection is unavailable");
        }
    }

    public List<PostVersionHistoryDTO> listRecent(Long postId, int limit) {
        int safeLimit = boundedLimit(limit);
        return versionMapper.selectRecentByPost(postId, safeLimit).stream().map(this::toDto).toList();
    }

    public List<PublicPostUpdateDTO> listPublicUpdates(Long postId, int limit) {
        int safeLimit = boundedLimit(limit);
        try {
            if (versionMapper.tableExists() <= 0) {
                return List.of();
            }
            List<PublicPostUpdateDTO> updates = versionMapper.listPublicUpdates(postId, safeLimit);
            if (updates == null || updates.isEmpty()) {
                return List.of();
            }
            return updates.stream()
                    .map(this::sanitizePublicUpdate)
                    .filter(Objects::nonNull)
                    .limit(safeLimit)
                    .toList();
        } catch (Exception e) {
            log.warn("public post update history query failed, postId={}", postId, e);
            return List.of();
        }
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
                .resultVersion(po.getResultVersion())
                .title(po.getTitle())
                .content(po.getContent())
                .contentSummary(summary(po.getContent()))
                .coverUrl(po.getCoverUrl())
                .visibility(po.getVisibility())
                .postStatus(po.getPostStatus())
                .extJson(po.getExtJson())
                .tags(readTags(po.getTagSnapshotJson()))
                .changeSummary(po.getChangeSummary())
                .publicUpdateSummary(po.getPublicUpdateSummary())
                .impactScope(po.getImpactScope())
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

    private void activateCandidate(ContentRevisionCandidate candidate) {
        requireQualitySignalSchema();
        LocalDateTime effectiveAt = LocalDateTime.now();
        if (versionMapper.activateQualityRevision(candidate.postId(), candidate.resultVersion(),
                candidate.revisionToken(), effectiveAt) != 1) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                    "effective content revision activation is unavailable");
        }
        if (versionMapper.updateLatestEffectiveContentRevision(candidate.postId(), candidate.revisionToken(),
                effectiveAt) != 1) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                    "effective content revision boundary is unavailable");
        }
    }

    private void requireQualitySignalSchema() {
        if (!qualitySignalSchemaReady()) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(),
                    "effective content revision schema is unavailable");
        }
    }

    private boolean qualitySignalSchemaReady() {
        return versionMapper.qualitySignalSchemaColumnCount() == 6;
    }

    private static boolean isPublicVisibility(Integer visibility) {
        return visibility == null || Objects.equals(visibility, Post.VIS_PUBLIC);
    }

    private static String normalizePublicText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private PublicPostUpdateDTO sanitizePublicUpdate(PublicPostUpdateDTO update) {
        if (update == null || update.getResultVersion() == null || update.getResultVersion() <= 0) {
            return null;
        }
        String summary = boundedOptional(update.getPublicUpdateSummary(), MAX_PUBLIC_UPDATE_SUMMARY_LEN);
        if (summary == null) {
            return null;
        }
        return PublicPostUpdateDTO.builder()
                .resultVersion(update.getResultVersion())
                .publicUpdateSummary(summary)
                .impactScope(boundedOptional(update.getImpactScope(), MAX_IMPACT_SCOPE_LEN))
                .createTime(update.getCreateTime())
                .build();
    }

    private int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 10 : limit, MAX_HISTORY_LIMIT));
    }

    private String boundedOptional(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String summary(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= CONTENT_SUMMARY_LEN ? normalized : normalized.substring(0, CONTENT_SUMMARY_LEN) + "...";
    }

    public record ContentRevisionCandidate(Long postId, Integer resultVersion, String revisionToken) {
        public ContentRevisionCandidate {
            if (postId == null || postId <= 0 || resultVersion == null || resultVersion < 0
                    || !StringUtils.hasText(revisionToken)) {
                throw new IllegalArgumentException("invalid content revision candidate");
            }
        }
    }
}

package com.offerlab.community.question.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import com.offerlab.community.question.api.dto.InterviewMaterialPackDTO;
import com.offerlab.community.question.api.dto.InterviewMaterialUpdateCmd;
import com.offerlab.community.question.api.dto.QuestionDTO;
import com.offerlab.community.question.api.dto.UserKnowledgeDTO;
import com.offerlab.community.question.api.dto.UserPrepOverviewDTO;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewMaterialPackMapper;
import com.offerlab.community.question.infrastructure.persistence.po.InterviewMaterialPackPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewMaterialService {
    private static final int MAX_TEXT = 3000;
    private static final int MAX_LIST_ITEMS = 12;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final InterviewMaterialPackMapper materialMapper;
    private final InterviewMaterialGenerator generator;
    private final QuestionFacade questionFacade;
    private final PostFacade postFacade;
    private final PostMapper postMapper;
    private final SnowflakeIdGenerator idGen;
    private final ObjectMapper objectMapper;

    @Transactional
    public InterviewMaterialPackDTO generate(Long uid, Long postId) {
        PostDTO post = requireVisiblePost(uid, postId);
        InterviewMaterialGenerator.GeneratedMaterial material = generator.generate(post);
        PostPO raw = postMapper.selectById(postId);
        InterviewMaterialPackPO po = new InterviewMaterialPackPO();
        po.setId(idGen.nextId());
        po.setUid(uid);
        po.setPostId(postId);
        po.setSourcePostVersion(raw == null || raw.getVersion() == null ? 0 : raw.getVersion());
        po.setGenerationStatus("SUCCEEDED");
        po.setStarSituation(limit(material.starSituation(), 2000));
        po.setStarTask(limit(material.starTask(), 2000));
        po.setStarAction(limit(material.starAction(), MAX_TEXT));
        po.setStarResult(limit(material.starResult(), 2000));
        po.setResumeBulletJson(toJson(material.resumeBullets()));
        po.setFollowUpQuestionJson(toJson(material.followUpQuestions()));
        po.setTechnicalHighlightJson(toJson(material.technicalHighlights()));
        po.setMissingHintJson(toJson(material.missingHints()));
        po.setUserNote(null);
        po.setSavedToPrep(0);
        po.setProvider("rules");
        po.setFallbackUsed(1);
        materialMapper.upsertGenerated(po);
        InterviewMaterialPackPO saved = materialMapper.selectByUserAndPost(uid, postId);
        return toDto(saved, postBriefs(uid, saved == null ? List.of() : List.of(saved)));
    }

    public InterviewMaterialPackDTO getForPost(Long uid, Long postId) {
        requireVisiblePost(uid, postId);
        InterviewMaterialPackPO po = materialMapper.selectByUserAndPost(uid, postId);
        return toDto(po, postBriefs(uid, po == null ? List.of() : List.of(po)));
    }

    @Transactional
    public InterviewMaterialPackDTO update(Long uid, Long id, InterviewMaterialUpdateCmd cmd) {
        InterviewMaterialPackPO po = requireOwned(uid, id);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        po.setStarSituation(pickText(cmd.getStarSituation(), po.getStarSituation(), 2000));
        po.setStarTask(pickText(cmd.getStarTask(), po.getStarTask(), 2000));
        po.setStarAction(pickText(cmd.getStarAction(), po.getStarAction(), MAX_TEXT));
        po.setStarResult(pickText(cmd.getStarResult(), po.getStarResult(), 2000));
        if (cmd.getResumeBullets() != null) {
            po.setResumeBulletJson(toJson(cmd.getResumeBullets()));
        }
        if (cmd.getFollowUpQuestions() != null) {
            po.setFollowUpQuestionJson(toJson(cmd.getFollowUpQuestions()));
        }
        if (cmd.getTechnicalHighlights() != null) {
            po.setTechnicalHighlightJson(toJson(cmd.getTechnicalHighlights()));
        }
        if (cmd.getMissingHints() != null) {
            po.setMissingHintJson(toJson(cmd.getMissingHints()));
        }
        po.setUserNote(pickText(cmd.getUserNote(), po.getUserNote(), 1000));
        materialMapper.updateEditable(po);
        InterviewMaterialPackPO updated = materialMapper.selectByUserAndId(uid, id);
        return toDto(updated, postBriefs(uid, List.of(updated)));
    }

    @Transactional
    public InterviewMaterialPackDTO saveToPrep(Long uid, Long id) {
        InterviewMaterialPackPO po = requireOwned(uid, id);
        materialMapper.markSavedToPrep(uid, id);
        InterviewMaterialPackPO updated = materialMapper.selectByUserAndId(uid, id);
        return toDto(updated == null ? po : updated, postBriefs(uid, List.of(updated == null ? po : updated)));
    }

    public UserKnowledgeDTO myKnowledge(Long uid,
                                        String company,
                                        String position,
                                        String techStack,
                                        String interviewRound,
                                        Integer postType,
                                        boolean savedOnly,
                                        int limit) {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 12 : limit, 50));
        String companyFilter = clean(company);
        String positionFilter = clean(position);
        String techStackFilter = clean(techStack);
        String roundFilter = clean(interviewRound);
        List<InterviewMaterialPackPO> packs = materialMapper.selectByUserFiltered(uid, companyFilter, positionFilter,
                techStackFilter, roundFilter, postType, savedOnly, safeLimit);
        Map<Long, PostBriefDTO> packPosts = postBriefs(uid, packs);
        List<InterviewMaterialPackDTO> materialPacks = packs.stream()
                .map(pack -> toDto(pack, packPosts))
                .filter(Objects::nonNull)
                .filter(pack -> pack.getSourcePost() != null)
                .toList();

        List<Long> favoritePostIds = savedOnly ? List.of() : materialMapper.selectFavoritePostIdsFiltered(uid,
                companyFilter, positionFilter, techStackFilter, roundFilter, postType, 8);
        Map<Long, PostBriefDTO> favoritePostMap = postFacade.batchGetPosts(favoritePostIds, uid);
        List<PostBriefDTO> favoritePosts = favoritePostIds.stream()
                .map(favoritePostMap::get)
                .filter(Objects::nonNull)
                .toList();
        UserPrepOverviewDTO overview = questionFacade.getMyPrepOverview(uid);
        List<Long> favoriteQuestionIds = savedOnly ? List.of() : materialMapper.selectFavoriteQuestionIdsFiltered(uid,
                companyFilter, positionFilter, techStackFilter, roundFilter, postType, 8);
        List<QuestionDTO> favoriteQuestions = savedOnly
                ? List.of()
                : questionFacade.getVisibleQuestionsByIds(favoriteQuestionIds, uid);
        Map<String, Object> counts = Objects.requireNonNullElse(materialMapper.countByUserFiltered(uid,
                companyFilter, positionFilter, techStackFilter, roundFilter, postType), Map.of());
        return UserKnowledgeDTO.builder()
                .materialPackCount(asLong(counts.get("materialPackCount")))
                .savedMaterialPackCount(asLong(counts.get("savedMaterialPackCount")))
                .favoritePostCount(savedOnly ? 0L : materialMapper.countFavoritePostsFiltered(uid,
                        companyFilter, positionFilter, techStackFilter, roundFilter, postType))
                .favoriteQuestionCount(savedOnly ? 0L : materialMapper.countFavoriteQuestionsFiltered(uid,
                        companyFilter, positionFilter, techStackFilter, roundFilter, postType))
                .materialPacks(materialPacks)
                .favoritePosts(favoritePosts)
                .favoriteQuestions(favoriteQuestions)
                .targets(overview == null ? List.of() : safeList(overview.getTargets()))
                .weakTags(overview == null ? List.of() : safeList(overview.getFocusTagCounts()))
                .materialGapHints(materialGapHints(materialPacks))
                .build();
    }

    private InterviewMaterialPackPO requireOwned(Long uid, Long id) {
        if (uid == null || id == null || id <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        InterviewMaterialPackPO po = materialMapper.selectByUserAndId(uid, id);
        if (po == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return po;
    }

    private PostDTO requireVisiblePost(Long uid, Long postId) {
        if (uid == null || postId == null || postId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        PostDTO post = postFacade.getPost(postId, uid);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private Map<Long, PostBriefDTO> postBriefs(Long uid, Collection<InterviewMaterialPackPO> packs) {
        if (packs == null || packs.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = packs.stream()
                .filter(Objects::nonNull)
                .map(InterviewMaterialPackPO::getPostId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return postFacade.batchGetPosts(ids, uid);
    }

    private InterviewMaterialPackDTO toDto(InterviewMaterialPackPO po, Map<Long, PostBriefDTO> posts) {
        if (po == null) {
            return null;
        }
        return InterviewMaterialPackDTO.builder()
                .id(po.getId())
                .uid(po.getUid())
                .postId(po.getPostId())
                .sourcePostVersion(po.getSourcePostVersion())
                .generationStatus(po.getGenerationStatus())
                .starSituation(po.getStarSituation())
                .starTask(po.getStarTask())
                .starAction(po.getStarAction())
                .starResult(po.getStarResult())
                .resumeBullets(fromJson(po.getResumeBulletJson()))
                .followUpQuestions(fromJson(po.getFollowUpQuestionJson()))
                .technicalHighlights(fromJson(po.getTechnicalHighlightJson()))
                .missingHints(fromJson(po.getMissingHintJson()))
                .userNote(po.getUserNote())
                .savedToPrep(Objects.equals(po.getSavedToPrep(), 1))
                .provider(po.getProvider())
                .fallbackUsed(Objects.equals(po.getFallbackUsed(), 1))
                .sourcePost(posts == null ? null : posts.get(po.getPostId()))
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private List<String> materialGapHints(List<InterviewMaterialPackDTO> packs) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        for (InterviewMaterialPackDTO pack : packs == null ? List.<InterviewMaterialPackDTO>of() : packs) {
            if (pack.getMissingHints() != null) {
                hints.addAll(pack.getMissingHints());
            }
            if (hints.size() >= 6) {
                break;
            }
        }
        if (hints.isEmpty()) {
            hints.add("素材包已覆盖主要面试表达字段，建议继续补充量化结果和技术取舍。");
        }
        return hints.stream().limit(6).toList();
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(normalizeList(values));
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return normalizeList(objectMapper.readValue(json, STRING_LIST));
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::clean)
                .filter(value -> !value.isBlank())
                .map(value -> limit(value, 300))
                .distinct()
                .limit(MAX_LIST_ITEMS)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String pickText(String next, String current, int max) {
        return next == null ? current : limit(clean(next), max);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(String.valueOf(value)));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private long safeLong(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private String limit(String value, int max) {
        String text = clean(value);
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

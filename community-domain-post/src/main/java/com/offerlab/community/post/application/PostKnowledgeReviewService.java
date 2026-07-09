package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.redis.cache.CacheKeyBuilder;
import com.offerlab.community.infra.redis.cache.MultiLevelCache;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostKnowledgeReviewService {

    private static final int MAX_SUMMARY_LEN = 500;
    private static final int MAX_JSON_FIELD_LEN = 10000;
    private static final int MAX_NOTE_LEN = 500;
    private static final int MAX_LIST_SIZE = 20;
    private static final int MAX_LIST_ITEM_LEN = 64;

    private final PostRepository postRepo;
    private final ObjectMapper objectMapper;
    private final AdminAuditService adminAuditService;
    private final MultiLevelCache<PostDTO> postDetailCache;
    private final DomainModeratorService domainModeratorService;

    @Transactional
    public Map<String, Object> applyReview(Long postId, Long operatorUid, KnowledgeReviewCmd cmd) {
        if (operatorUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        domainModeratorService.requireModerateDomain(operatorUid, post.getDomain());
        String beforeExtJson = post.getExtJson();
        String afterExtJson = writeKnowledgeExt(beforeExtJson, operatorUid, cmd);
        post.setExtJson(afterExtJson);
        if (!postRepo.update(post)) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        postDetailCache.evict(CacheKeyBuilder.postDetail(postId));
        postDetailCache.evict(CacheKeyBuilder.postDetailRaw(postId));
        adminAuditService.recordRequired(operatorUid, "POST_KNOWLEDGE_REVIEW_APPLY", "POST", postId,
                Map.of("extJson", beforeExtJson == null ? "" : beforeExtJson),
                Map.of("knowledgeReviewed", true, "extJson", afterExtJson),
                cleanText(cmd.note(), MAX_NOTE_LEN));
        return Map.of(
                "postId", postId,
                "knowledgeReviewed", true,
                "extJson", afterExtJson
        );
    }

    private String writeKnowledgeExt(String extJson, Long operatorUid, KnowledgeReviewCmd cmd) {
        try {
            ObjectNode object = readObjectNode(extJson);
            putOptionalText(object, "summary", cmd.summary(), MAX_SUMMARY_LEN);
            putJsonText(object, "faqJson", cmd.faqJson(), MAX_JSON_FIELD_LEN);
            putJsonText(object, "knowledgeCardJson", cmd.knowledgeCardJson(), MAX_JSON_FIELD_LEN);
            putStringArray(object, "techStacks", cmd.techStacks());
            putStringArray(object, "suggestedTags", cmd.suggestedTags());
            object.put("knowledgeReviewed", true);
            object.put("knowledgeReviewedAt", LocalDateTime.now().toString());
            object.put("knowledgeReviewedBy", operatorUid);
            object.put("aiAssisted", true);
            if (StringUtils.hasText(cmd.note())) {
                object.put("knowledgeReviewNote", cleanText(cmd.note(), MAX_NOTE_LEN));
            }
            return objectMapper.writeValueAsString(object);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "知识沉淀扩展字段更新失败");
        }
    }

    private void putOptionalText(ObjectNode object, String field, String value, int maxLength) {
        String text = cleanText(value, maxLength);
        if (text == null) {
            object.remove(field);
            return;
        }
        object.put(field, text);
    }

    private void putJsonText(ObjectNode object, String field, String value, int maxLength) {
        String text = cleanText(value, maxLength);
        if (text == null) {
            object.remove(field);
            return;
        }
        try {
            objectMapper.readTree(text);
            object.put(field, text);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), field + " 必须是合法 JSON");
        }
    }

    private void putStringArray(ObjectNode object, String field, List<String> values) {
        List<String> cleaned = cleanList(values);
        if (cleaned.isEmpty()) {
            object.remove(field);
            return;
        }
        ArrayNode array = objectMapper.createArrayNode();
        cleaned.forEach(array::add);
        object.set(field, array);
    }

    private List<String> cleanList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(value -> cleanText(value, MAX_LIST_ITEM_LEN))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(MAX_LIST_SIZE)
                .toList();
    }

    private ObjectNode readObjectNode(String extJson) throws Exception {
        if (!StringUtils.hasText(extJson)) {
            return objectMapper.createObjectNode();
        }
        JsonNode node = objectMapper.readTree(extJson);
        return node != null && node.isObject()
                ? (ObjectNode) node
                : objectMapper.createObjectNode();
    }

    private String cleanText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    public record KnowledgeReviewCmd(
            String summary,
            String faqJson,
            String knowledgeCardJson,
            List<String> techStacks,
            List<String> suggestedTags,
            String note
    ) {
    }
}

package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostFeaturedService {

    private static final int MAX_NOTE_LEN = 500;

    private final PostRepository postRepo;
    private final ObjectMapper objectMapper;
    private final AdminAuditService adminAuditService;
    private final MultiLevelCache<PostDTO> postDetailCache;
    private final DomainModeratorService domainModeratorService;

    @Transactional
    public Map<String, Object> updateFeatured(Long postId, boolean featured, Long operatorUid, String note) {
        if (operatorUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        domainModeratorService.requireModerateDomain(operatorUid, post.getDomain());
        String beforeExtJson = post.getExtJson();
        String afterExtJson = writeFeaturedExt(beforeExtJson, featured, operatorUid, note);
        post.setExtJson(afterExtJson);
        if (!postRepo.update(post)) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        postDetailCache.evict(CacheKeyBuilder.postDetail(postId));
        postDetailCache.evict(CacheKeyBuilder.postDetailRaw(postId));
        String action = featured ? "POST_FEATURE_SET" : "POST_FEATURE_UNSET";
        adminAuditService.recordRequired(operatorUid, action, "POST", postId,
                Map.of("extJson", beforeExtJson == null ? "" : beforeExtJson),
                Map.of("featured", featured, "extJson", afterExtJson),
                cleanNote(note));
        return Map.of(
                "postId", postId,
                "featured", featured,
                "extJson", afterExtJson
        );
    }

    private String writeFeaturedExt(String extJson, boolean featured, Long operatorUid, String note) {
        try {
            ObjectNode object = readObjectNode(extJson);
            object.put("featured", featured);
            object.put("featuredAt", LocalDateTime.now().toString());
            object.put("featuredBy", operatorUid);
            if (StringUtils.hasText(note)) {
                object.put("featuredNote", cleanNote(note));
            } else if (!featured) {
                object.remove("featuredNote");
            }
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "精选扩展字段更新失败");
        }
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

    private String cleanNote(String note) {
        if (!StringUtils.hasText(note)) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.length() <= MAX_NOTE_LEN ? trimmed : trimmed.substring(0, MAX_NOTE_LEN);
    }
}

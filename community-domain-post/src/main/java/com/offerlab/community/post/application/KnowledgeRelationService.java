package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.KnowledgeRelationEdgeDTO;
import com.offerlab.community.post.api.dto.KnowledgeRelationGraphDTO;
import com.offerlab.community.post.api.dto.KnowledgeRelationNodeDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.domain.model.PostDomain;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicTagMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.po.CommunityTopicPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KnowledgeRelationService {

    private static final int MAX_LIMIT = 20;

    private final PostFacade postFacade;
    private final PostMapper postMapper;
    private final CommunityTopicMapper topicMapper;
    private final CommunityTopicTagMapper topicTagMapper;

    public KnowledgeRelationGraphDTO explore(Long postId, Long tagId, Long topicId, Integer domain, int limit) {
        if (postId == null && tagId == null && topicId == null && domain == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Integer activeDomain = domain == null ? null : requireDomain(domain);
        int safeLimit = safeLimit(limit);

        LinkedHashSet<Long> postIds = new LinkedHashSet<>();
        if (postId != null && postId > 0) {
            postIds.add(postId);
        }
        if (tagId != null && tagId > 0) {
            postIds.addAll(selectPostIds(postMapper.selectPublicPosts(null, tagId, null, null, null, null, Long.MAX_VALUE, safeLimit)));
        }
        if (topicId != null && topicId > 0) {
            CommunityTopicPO topic = topicMapper.selectById(topicId);
            if (isOnlineTopic(topic)) {
                List<Long> topicTagIds = topicTagMapper.selectTagsByTopicId(topicId).stream()
                        .map(TagPO::getId)
                        .filter(Objects::nonNull)
                        .toList();
                postIds.addAll(selectPostIds(postMapper.selectPublicPostsByTopic(topicId, topicTagIds,
                        topic.getTopicName(), null, null, null, Long.MAX_VALUE, safeLimit)));
            }
        }
        if (activeDomain != null) {
            postIds.addAll(selectPostIds(postMapper.selectPublicPosts(null, null, null, null, activeDomain, null, Long.MAX_VALUE, safeLimit)));
        }
        Map<Long, PostBriefDTO> posts = postFacade.batchGetPosts(postIds, null, false);
        LinkedHashMap<String, KnowledgeRelationNodeDTO> nodes = new LinkedHashMap<>();
        LinkedHashMap<String, KnowledgeRelationEdgeDTO> edges = new LinkedHashMap<>();
        LinkedHashSet<Long> tagIds = new LinkedHashSet<>();

        for (PostBriefDTO post : posts.values()) {
            if (post == null || post.getId() == null) {
                continue;
            }
            addNode(nodes, KnowledgeRelationNodeDTO.builder()
                    .key("post:" + post.getId())
                    .type("post")
                    .label(post.getTitle())
                    .domain(post.getDomain())
                    .build());
            Integer postDomain = post.getDomain() == null ? PostDomain.TECH.getCode() : post.getDomain();
            addNode(nodes, KnowledgeRelationNodeDTO.builder()
                    .key("domain:" + postDomain)
                    .type("domain")
                    .label(PostDomain.fromCode(postDomain).name().toLowerCase())
                    .domain(postDomain)
                    .build());
            addEdge(edges, "domain:" + postDomain, "post:" + post.getId(), "domain_post");
            for (TagDTO tag : post.getTags() == null ? List.<TagDTO>of() : post.getTags()) {
                if (!isPublicTag(tag)) {
                    continue;
                }
                tagIds.add(tag.getId());
                addNode(nodes, KnowledgeRelationNodeDTO.builder()
                        .key("tag:" + tag.getId())
                        .type("tag")
                        .label(tag.getName())
                        .build());
                addEdge(edges, "post:" + post.getId(), "tag:" + tag.getId(), "post_tag");
            }
        }

        if (!tagIds.isEmpty()) {
            for (CommunityTopicPO topic : safeTopicsByTagIds(tagIds, safeLimit)) {
                if (!isOnlineTopic(topic)) {
                    continue;
                }
                addNode(nodes, KnowledgeRelationNodeDTO.builder()
                        .key("topic:" + topic.getId())
                        .type("topic")
                        .label(topic.getTopicName())
                        .build());
                for (TagPO tag : topicTagMapper.selectTagsByTopicId(topic.getId())) {
                    if (!isPublicTag(tag)) {
                        continue;
                    }
                    addNode(nodes, KnowledgeRelationNodeDTO.builder()
                            .key("tag:" + tag.getId())
                            .type("tag")
                            .label(tag.getTagName())
                            .build());
                    addEdge(edges, "topic:" + topic.getId(), "tag:" + tag.getId(), "topic_tag");
                }
            }
        }

        return KnowledgeRelationGraphDTO.builder()
                .limit(safeLimit)
                .nodes(List.copyOf(nodes.values()))
                .edges(List.copyOf(edges.values()))
                .build();
    }

    private List<CommunityTopicPO> safeTopicsByTagIds(Collection<Long> tagIds, int limit) {
        try {
            return topicMapper.selectOnlineTopicsByTagIds(tagIds, limit);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static boolean isOnlineTopic(CommunityTopicPO topic) {
        return topic != null
                && topic.getId() != null
                && !Integer.valueOf(1).equals(topic.getIsDeleted())
                && Integer.valueOf(1).equals(topic.getTopicStatus())
                && !PublicContentFilter.isSyntheticText(topic.getTopicName())
                && !PublicContentFilter.isUnsafeSuggestionText(topic.getTopicName());
    }

    private static boolean isPublicTag(TagDTO tag) {
        return tag != null
                && tag.getId() != null
                && (tag.getStatus() == null || Integer.valueOf(1).equals(tag.getStatus()))
                && tag.getMergeTargetId() == null
                && !PublicContentFilter.isSyntheticText(tag.getName())
                && !PublicContentFilter.isUnsafeSuggestionText(tag.getName());
    }

    private static boolean isPublicTag(TagPO tag) {
        return tag != null
                && tag.getId() != null
                && (tag.getTagStatus() == null || Integer.valueOf(1).equals(tag.getTagStatus()))
                && tag.getMergeTargetId() == null
                && !PublicContentFilter.isSyntheticText(tag.getTagName())
                && !PublicContentFilter.isUnsafeSuggestionText(tag.getTagName());
    }

    private static void addNode(Map<String, KnowledgeRelationNodeDTO> nodes, KnowledgeRelationNodeDTO node) {
        nodes.putIfAbsent(node.getKey(), node);
    }

    private static void addEdge(Map<String, KnowledgeRelationEdgeDTO> edges, String source, String target, String relation) {
        String key = source + "|" + target + "|" + relation;
        edges.putIfAbsent(key, KnowledgeRelationEdgeDTO.builder()
                .source(source)
                .target(target)
                .relation(relation)
                .weight(1)
                .build());
    }

    private static List<Long> selectPostIds(List<PostPO> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        return posts.stream()
                .map(PostPO::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Integer requireDomain(Integer domain) {
        if (!PostDomain.isValid(domain)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return domain;
    }

    private static int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 8 : limit, MAX_LIMIT));
    }
}

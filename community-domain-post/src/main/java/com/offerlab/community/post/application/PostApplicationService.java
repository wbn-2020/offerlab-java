package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.tx.AfterCommitExecutor;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostUpdateCmd;
import com.offerlab.community.post.api.event.PostDeletedEvent;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.api.event.PostUpdatedEvent;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.model.PostDomain;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostTagRefMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import com.offerlab.community.post.infrastructure.persistence.projection.PostTagView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostApplicationService {

    private final PostRepository postRepo;
    private final PostCounterMapper counterMapper;
    private final PostTagRefMapper postTagRefMapper;
    private final TagMapper tagMapper;
    private final PostCounterRedis postCounterRedis;
    private final SnowflakeIdGenerator idGen;
    private final EventPublisher events;
    private final PostVersionHistoryService versionHistoryService;
    private final PostPublishQualityValidator qualityValidator;
    private final AfterCommitExecutor afterCommit;
    private final CommunityTopicService communityTopicService;
    private final MigrationCheckService migrationCheckService;
    private final DomainConfigService domainConfigService;

    @Transactional
    public Long publish(PostCreateCmd cmd) {
        Integer domain = resolveRequestedDomain(cmd.getDomain(), cmd.getExtJson(), Post.DOMAIN_TECH);
        domainConfigService.requireDomainEnabled(domain);
        PostPublishQualityValidator.ValidatedPostInput input = qualityValidator.validate(
                cmd.getPostType(), cmd.getTitle(), cmd.getContent(), cmd.getExtJson(), cmd.getTagIds(), cmd.getTagNames());
        long id = idGen.nextId();
        List<Long> resolvedTagIds = resolveTagIds(input.tagIds(), input.tagNames());
        requireResolvedTagCount(input.postType(), resolvedTagIds);
        String enrichedExtJson = mergeAnonymousToExtJson(
                mergeDomainToExtJson(input.extJson(), domain), domain, cmd.getAnonymous());
        boolean reviewRequired = Boolean.TRUE.equals(cmd.getReviewRequired())
                || domainConfigService.reviewRequiredForPublish(domain);
        Post post = Post.builder()
                .id(id)
                .authorId(cmd.getAuthorId())
                .postType(input.postType())
                .title(input.title())
                .content(input.content())
                .coverUrl(cmd.getCoverUrl())
                .visibility(cmd.getVisibility() == null ? Post.VIS_PUBLIC : cmd.getVisibility())
                .postStatus(reviewRequired ? Post.STATUS_REVIEWING : Post.STATUS_PUBLISHED)
                .extJson(enrichedExtJson)
                .tagIds(resolvedTagIds)
                .domain(domain)
                .build();
        postRepo.save(post);
        counterMapper.initIfAbsent(id);
        syncTags(id, post.getTagIds());

        if (!reviewRequired) {
            events.publish(PostPublishedEvent.builder()
                    .postId(id)
                    .authorId(cmd.getAuthorId())
                    .title(input.title())
                    .content(input.content())
                    .visibility(post.getVisibility())
                    .postStatus(post.getPostStatus())
                    .domain(post.getDomain())
                    .timestamp(Instant.now().toEpochMilli())
                    .tagIds(resolvedTagIds)
                    .topicNotificationTargets(topicNotificationTargets(post, resolvedTagIds))
                    .build());
        }
        return id;
    }

    @Transactional
    public void update(PostUpdateCmd cmd) {
        Post post = postRepo.findById(cmd.getPostId())
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (!post.getAuthorId().equals(cmd.getOperatorUid())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        Integer nextDomain = resolveRequestedDomain(cmd.getDomain(), cmd.getExtJson(), post.getDomain());
        domainConfigService.requireDomainEnabled(nextDomain);
        boolean tagsProvided = cmd.getTagIds() != null || cmd.getTagNames() != null;
        List<Long> existingTagIds = currentTagIds(post.getId());
        List<Long> validationTagIds = tagsProvided ? cmd.getTagIds() : existingTagIds;
        List<String> validationTagNames = tagsProvided ? cmd.getTagNames() : List.of();
        PostPublishQualityValidator.ValidatedPostInput input = qualityValidator.validate(
                post.getPostType(),
                cmd.getTitle() == null ? post.getTitle() : cmd.getTitle(),
                cmd.getContent() == null ? post.getContent() : cmd.getContent(),
                cmd.getExtJson() == null ? post.getExtJson() : cmd.getExtJson(),
                validationTagIds,
                validationTagNames);
        List<Long> resolvedTagIds = tagsProvided ? resolveTagIds(input.tagIds(), input.tagNames()) : validationTagIds;
        if (tagsProvided) {
            requireResolvedTagCount(input.postType(), resolvedTagIds);
        }
        String enrichedExtJson = mergeAnonymousToExtJson(
                mergeDomainToExtJson(input.extJson(), nextDomain), nextDomain, cmd.getAnonymous());
        String nextCoverUrl = cmd.getCoverUrl() == null ? post.getCoverUrl() : cmd.getCoverUrl();
        Integer nextVisibility = cmd.getVisibility() == null ? post.getVisibility() : cmd.getVisibility();
        versionHistoryService.snapshotBeforeUpdate(post, cmd.getOperatorUid(), tagsByIds(existingTagIds), post.getVersion(),
                input.title(), input.content(), nextCoverUrl, nextVisibility, enrichedExtJson, resolvedTagIds, tagsProvided);

        post.setVisibility(nextVisibility);
        post.setExtJson(enrichedExtJson);
        post.setDomain(nextDomain);
        post.setTitle(input.title());
        post.setContent(input.content());
        post.setCoverUrl(nextCoverUrl);
        if (Boolean.TRUE.equals(cmd.getReviewRequired())
                || domainConfigService.reviewRequiredForPublish(nextDomain)) {
            post.setPostStatus(Post.STATUS_REVIEWING);
        }
        postRepo.update(post);
        if (tagsProvided) {
            syncTags(post.getId(), resolvedTagIds);
        }
        events.publish(PostUpdatedEvent.builder()
                .postId(post.getId())
                .authorId(post.getAuthorId())
                .title(post.getTitle())
                .content(post.getContent())
                .visibility(post.getVisibility())
                .postStatus(post.getPostStatus())
                .timestamp(Instant.now().toEpochMilli())
                .build());
    }

    @Transactional
    public void delete(Long postId, Long operatorUid) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (!post.getAuthorId().equals(operatorUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        postRepo.softDelete(postId);
        events.publish(PostDeletedEvent.builder()
                .postId(postId)
                .authorId(post.getAuthorId())
                .timestamp(Instant.now().toEpochMilli())
                .build());
    }

    public Post getOrThrow(Long postId) {
        return postRepo.findById(postId).orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
    }

    public void incrView(Long postId) {
        counterMapper.incrView(postId, 1);
        afterCommit.execute(() -> postCounterRedis.incrView(postId, 1), "post view counter:" + postId);
    }

    private String mergeDomainToExtJson(String extJson, Integer domain) {
        if (domain == null) return extJson;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root;
            if (extJson != null && !extJson.isBlank()) {
                root = mapper.readTree(extJson);
                if (root instanceof ObjectNode obj) {
                    obj.put("domain", domain);
                    return mapper.writeValueAsString(obj);
                }
            }
            // No existing extJson, create new
            ObjectNode obj = mapper.createObjectNode();
            obj.put("domain", domain);
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            // If can't parse, return original
            return extJson;
        }
    }

    private String mergeAnonymousToExtJson(String extJson, Integer domain, Boolean anonymous) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode object = readObjectExtJson(mapper, extJson);
            Integer effectiveDomain = domain != null ? domain : readDomain(object);
            boolean hasAnonymous = object.has("anonymous");
            if (anonymous == null && !hasAnonymous) {
                return extJson;
            }
            boolean enabled = Objects.equals(effectiveDomain, Post.DOMAIN_CAREER) && Boolean.TRUE.equals(
                    anonymous == null ? object.path("anonymous").asBoolean(false) : anonymous);
            object.put("anonymous", enabled);
            return mapper.writeValueAsString(object);
        } catch (Exception e) {
            return extJson;
        }
    }

    private ObjectNode readObjectExtJson(ObjectMapper mapper, String extJson) throws Exception {
        if (extJson != null && !extJson.isBlank()) {
            JsonNode root = mapper.readTree(extJson);
            if (root instanceof ObjectNode obj) {
                return obj;
            }
        }
        return mapper.createObjectNode();
    }

    private Integer readDomain(ObjectNode object) {
        if (object != null && object.has("domain") && object.get("domain").canConvertToInt()) {
            return object.get("domain").asInt();
        }
        return null;
    }

    private Integer resolveRequestedDomain(Integer explicitDomain, String extJson, Integer fallbackDomain) {
        if (explicitDomain != null) {
            return requireDomain(explicitDomain);
        }
        Integer extDomain = readDomainFromExtJson(extJson);
        if (PostDomain.isValid(extDomain)) {
            return extDomain;
        }
        return defaultDomain(fallbackDomain);
    }

    private Integer readDomainFromExtJson(String extJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return readDomain(readObjectExtJson(mapper, extJson));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer defaultDomain(Integer domain) {
        if (domain == null) {
            return Post.DOMAIN_TECH;
        }
        return requireDomain(domain);
    }

    private Integer requireDomain(Integer domain) {
        if (PostDomain.isValid(domain)) {
            return domain;
        }
        throw PostPublishQualityValidator.fieldError("domain", "领域不存在或已下线");
    }

    private List<Long> resolveTagIds(List<Long> tagIds, List<String> tagNames) {
        Set<Long> ids = new LinkedHashSet<>();
        if (tagIds != null) {
            List<Long> requestedIds = tagIds.stream()
                    .filter(id -> id != null && id > 0)
                    .limit(20)
                    .toList();
            if (!requestedIds.isEmpty()) {
                Set<Long> existingIds = selectTagsByIds(requestedIds).stream()
                        .filter(tag -> !java.util.Objects.equals(tag.getTagStatus(), 0))
                        .map(TagPO::getId)
                        .collect(Collectors.toCollection(HashSet::new));
                for (Long id : requestedIds) {
                    if (!existingIds.contains(id)) {
                        throw PostPublishQualityValidator.fieldError("tags", "标签不存在、已删除或已被禁用");
                    }
                    ids.add(id);
                }
            }
        }
        if (tagNames != null && !tagNames.isEmpty()) {
            List<String> names = tagNames.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::trim)
                    .distinct()
                    .limit(20)
                    .toList();
            if (!names.isEmpty()) {
                Set<String> existingNames = selectTagsByNames(names).stream()
                        .map(TagPO::getTagName)
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());
                for (String name : names) {
                    if (!existingNames.contains(name.toLowerCase())) {
                        insertIgnoreName(idGen.nextId(), name, 4);
                    }
                }
                selectTagsByNames(names).stream()
                        .map(TagPO::getId)
                        .forEach(ids::add);
            }
        }
        if (tagIds == null && tagNames == null) {
            return null;
        }
        return ids.stream().limit(20).toList();
    }

    private void requireResolvedTagCount(Integer postType, List<Long> tagIds) {
        int min = Post.isInterviewType(postType) ? 2 : 1;
        if (tagIds == null || tagIds.size() < min) {
            throw PostPublishQualityValidator.fieldError("tags", Post.isInterviewType(postType)
                    ? "历史经验至少需要 2 个有效技术标签"
                    : "至少需要 1 个有效标签");
        }
    }

    private List<Long> currentTagIds(Long postId) {
        return selectTagsByPostIds(List.of(postId)).stream()
                .map(PostTagView::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private List<com.offerlab.community.post.api.dto.TagDTO> tagsByIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        Map<Long, TagPO> tags = selectTagsByIds(tagIds).stream()
                .collect(Collectors.toMap(TagPO::getId, tag -> tag));
        return tagIds.stream()
                .map(tags::get)
                .filter(java.util.Objects::nonNull)
                .map(tag -> com.offerlab.community.post.api.dto.TagDTO.builder()
                        .id(tag.getId())
                        .name(tag.getTagName())
                        .tagType(tag.getTagType())
                        .useCount(tag.getUseCount())
                        .official(tag.getIsOfficial() != null && tag.getIsOfficial() == 1)
                        .build())
                .toList();
    }

    private void syncTags(Long postId, List<Long> tagIds) {
        Set<Long> oldIds = new LinkedHashSet<>(currentTagIds(postId));
        Set<Long> newIds = tagIds == null ? Set.of() : tagIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        postTagRefMapper.deleteByPostId(postId);
        oldIds.stream()
                .filter(id -> !newIds.contains(id))
                .forEach(postTagRefMapper::decrUseCount);
        for (Long tagId : newIds) {
            int inserted = postTagRefMapper.insertIgnore(idGen.nextId(), postId, tagId);
            if (inserted > 0 && !oldIds.contains(tagId)) {
                postTagRefMapper.incrUseCount(tagId);
            }
        }
    }

    private List<TagPO> selectTagsByIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectByIds(tagIds)
                : tagMapper.selectByIdsCompat(tagIds);
    }

    private List<PostTagView> selectTagsByPostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectTagsByPostIds(postIds)
                : tagMapper.selectTagsByPostIdsCompat(postIds);
    }

    private List<TagPO> selectTagsByNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return migrationCheckService.tagGovernanceReady()
                ? tagMapper.selectByNames(names)
                : tagMapper.selectByNamesCompat(names);
    }

    private void insertIgnoreName(Long id, String name, int tagType) {
        if (migrationCheckService.tagGovernanceReady()) {
            tagMapper.insertIgnoreName(id, name, tagType);
        } else {
            tagMapper.insertIgnoreNameCompat(id, name, tagType);
        }
    }

    private List<PostPublishedEvent.TopicNotificationTarget> topicNotificationTargets(Post post, List<Long> tagIds) {
        if (post == null || !Objects.equals(post.getVisibility(), Post.VIS_PUBLIC)
                || !Objects.equals(post.getPostStatus(), Post.STATUS_PUBLISHED)) {
            return List.of();
        }
        return communityTopicService.notificationTargetsForPost(tagIds, post.getAuthorId());
    }
}

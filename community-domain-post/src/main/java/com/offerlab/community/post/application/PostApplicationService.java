package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
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
import java.util.Set;
import java.util.stream.Collectors;

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

    @Transactional
    public Long publish(PostCreateCmd cmd) {
        PostPublishQualityValidator.ValidatedPostInput input = qualityValidator.validate(
                cmd.getPostType(), cmd.getTitle(), cmd.getContent(), cmd.getExtJson(), cmd.getTagIds(), cmd.getTagNames());
        long id = idGen.nextId();
        List<Long> resolvedTagIds = resolveTagIds(input.tagIds(), input.tagNames());
        requireResolvedTagCount(input.postType(), resolvedTagIds);
        boolean reviewRequired = Boolean.TRUE.equals(cmd.getReviewRequired());
        Post post = Post.builder()
                .id(id)
                .authorId(cmd.getAuthorId())
                .postType(input.postType())
                .title(input.title())
                .content(input.content())
                .coverUrl(cmd.getCoverUrl())
                .visibility(cmd.getVisibility() == null ? Post.VIS_PUBLIC : cmd.getVisibility())
                .postStatus(reviewRequired ? Post.STATUS_REVIEWING : Post.STATUS_PUBLISHED)
                .extJson(input.extJson())
                .tagIds(resolvedTagIds)
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
                    .timestamp(Instant.now().toEpochMilli())
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
        String nextCoverUrl = cmd.getCoverUrl() == null ? post.getCoverUrl() : cmd.getCoverUrl();
        Integer nextVisibility = cmd.getVisibility() == null ? post.getVisibility() : cmd.getVisibility();
        versionHistoryService.snapshotBeforeUpdate(post, cmd.getOperatorUid(), tagsByIds(existingTagIds), post.getVersion(),
                input.title(), input.content(), nextCoverUrl, nextVisibility, input.extJson(), resolvedTagIds, tagsProvided);

        post.setVisibility(nextVisibility);
        post.setExtJson(input.extJson());
        post.setTitle(input.title());
        post.setContent(input.content());
        post.setCoverUrl(nextCoverUrl);
        if (Boolean.TRUE.equals(cmd.getReviewRequired())) {
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

    private List<Long> resolveTagIds(List<Long> tagIds, List<String> tagNames) {
        Set<Long> ids = new LinkedHashSet<>();
        if (tagIds != null) {
            List<Long> requestedIds = tagIds.stream()
                    .filter(id -> id != null && id > 0)
                    .limit(20)
                    .toList();
            if (!requestedIds.isEmpty()) {
                Set<Long> existingIds = tagMapper.selectBatchIds(requestedIds).stream()
                        .map(TagPO::getId)
                        .collect(Collectors.toCollection(HashSet::new));
                for (Long id : requestedIds) {
                    if (!existingIds.contains(id)) {
                        throw PostPublishQualityValidator.fieldError("tags", "标签不存在或已被删除");
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
                Set<String> existingNames = tagMapper.selectByNames(names).stream()
                        .map(TagPO::getTagName)
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());
                for (String name : names) {
                    if (!existingNames.contains(name.toLowerCase())) {
                        tagMapper.insertIgnoreName(idGen.nextId(), name, 4);
                    }
                }
                tagMapper.selectByNames(names).stream()
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
        int min = Post.TYPE_INTERVIEW == (postType == null ? 0 : postType) ? 2 : 1;
        if (tagIds == null || tagIds.size() < min) {
            throw PostPublishQualityValidator.fieldError("tags", Post.TYPE_INTERVIEW == (postType == null ? 0 : postType)
                    ? "面经至少需要 2 个有效技术标签"
                    : "至少需要 1 个有效标签");
        }
    }

    private List<Long> currentTagIds(Long postId) {
        return tagMapper.selectTagsByPostIds(List.of(postId)).stream()
                .map(PostTagView::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private List<com.offerlab.community.post.api.dto.TagDTO> tagsByIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        Map<Long, TagPO> tags = tagMapper.selectBatchIds(tagIds).stream()
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
        postTagRefMapper.deleteByPostId(postId);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds) {
            int inserted = postTagRefMapper.insertIgnore(idGen.nextId(), postId, tagId);
            if (inserted > 0) {
                postTagRefMapper.incrUseCount(tagId);
            }
        }
    }
}

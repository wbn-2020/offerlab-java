package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostUpdateCmd;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostApplicationService {

    private final PostRepository postRepo;
    private final PostCounterMapper counterMapper;
    private final PostCounterRedis postCounterRedis;
    private final SnowflakeIdGenerator idGen;
    private final EventPublisher events;

    @Transactional
    public Long publish(PostCreateCmd cmd) {
        long id = idGen.nextId();
        Post post = Post.builder()
                .id(id)
                .authorId(cmd.getAuthorId())
                .postType(cmd.getPostType())
                .title(cmd.getTitle())
                .content(cmd.getContent())
                .coverUrl(cmd.getCoverUrl())
                .visibility(cmd.getVisibility() == null ? Post.VIS_PUBLIC : cmd.getVisibility())
                .postStatus(Post.STATUS_PUBLISHED)
                .extJson(cmd.getExtJson())
                .build();
        postRepo.save(post);
        counterMapper.initIfAbsent(id);

        events.publish(PostPublishedEvent.builder()
                .postId(id)
                .authorId(cmd.getAuthorId())
                .timestamp(Instant.now().toEpochMilli())
                .build());
        return id;
    }

    @Transactional
    public void update(PostUpdateCmd cmd) {
        Post post = postRepo.findById(cmd.getPostId())
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (!post.getAuthorId().equals(cmd.getOperatorUid())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (cmd.getTitle() != null) post.setTitle(cmd.getTitle());
        if (cmd.getContent() != null) post.setContent(cmd.getContent());
        if (cmd.getCoverUrl() != null) post.setCoverUrl(cmd.getCoverUrl());
        if (cmd.getVisibility() != null) post.setVisibility(cmd.getVisibility());
        if (cmd.getExtJson() != null) post.setExtJson(cmd.getExtJson());
        postRepo.update(post);
    }

    @Transactional
    public void delete(Long postId, Long operatorUid) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
        if (!post.getAuthorId().equals(operatorUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        postRepo.softDelete(postId);
    }

    public Post getOrThrow(Long postId) {
        return postRepo.findById(postId).orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
    }

    public void incrView(Long postId) {
        // Redis 写主
        postCounterRedis.incrView(postId, 1);
        // 双写 MySQL（过渡方案，下一波切异步）
        counterMapper.incrView(postId, 1);
    }
}

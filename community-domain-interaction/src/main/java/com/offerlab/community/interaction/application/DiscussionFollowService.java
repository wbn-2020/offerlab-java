package com.offerlab.community.interaction.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.utils.SqlLimits;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.interaction.api.DiscussionFollowFacade;
import com.offerlab.community.interaction.api.dto.DiscussionFollowStatusDTO;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.DiscussionFollowMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.DiscussionFollowPO;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DiscussionFollowService implements DiscussionFollowFacade {

    private static final int STATUS_FOLLOWING = 1;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_NOTIFICATION_TARGETS = 500;

    private final DiscussionFollowMapper discussionFollowMapper;
    private final PostFacade postFacade;
    private final SnowflakeIdGenerator idGen;

    @Override
    public DiscussionFollowStatusDTO status(Long uid, Long postId) {
        requirePostId(postId);
        if (uid == null) {
            return DiscussionFollowStatusDTO.builder()
                    .postId(postId)
                    .followed(false)
                    .source("unauthenticated")
                    .build();
        }
        requirePostVisible(postId, uid);
        DiscussionFollowPO po = activeFollow(uid, postId);
        return toStatus(postId, po, po == null ? "not_followed" : "followed");
    }

    @Override
    @Transactional
    public DiscussionFollowStatusDTO follow(Long uid, Long postId) {
        requireUid(uid);
        requirePostId(postId);
        requirePostVisible(postId, uid);

        DiscussionFollowPO existing = discussionFollowMapper.selectAnyByUserPost(uid, postId);
        if (existing != null) {
            if (isFollowing(existing)) {
                return toStatus(postId, existing, "followed");
            }
            discussionFollowMapper.restoreById(existing.getId());
            DiscussionFollowPO restored = discussionFollowMapper.selectAnyByUserPost(uid, postId);
            return toStatus(postId, restored, "followed");
        }

        DiscussionFollowPO po = new DiscussionFollowPO();
        po.setId(idGen.nextId());
        po.setUid(uid);
        po.setPostId(postId);
        po.setFollowStatus(STATUS_FOLLOWING);
        po.setIsDeleted(0);
        try {
            discussionFollowMapper.insert(po);
        } catch (DuplicateKeyException e) {
            DiscussionFollowPO duplicated = discussionFollowMapper.selectAnyByUserPost(uid, postId);
            if (duplicated == null) {
                throw e;
            }
            if (!isFollowing(duplicated)) {
                discussionFollowMapper.restoreById(duplicated.getId());
                duplicated = discussionFollowMapper.selectAnyByUserPost(uid, postId);
            }
            return toStatus(postId, duplicated, "followed");
        }
        return toStatus(postId, discussionFollowMapper.selectAnyByUserPost(uid, postId), "followed");
    }

    @Override
    @Transactional
    public DiscussionFollowStatusDTO unfollow(Long uid, Long postId) {
        requireUid(uid);
        requirePostId(postId);
        DiscussionFollowPO existing = activeFollow(uid, postId);
        if (existing == null) {
            return toStatus(postId, null, "not_followed");
        }
        discussionFollowMapper.softDeleteById(existing.getId());
        return toStatus(postId, null, "not_followed");
    }

    @Override
    public PageResult<PostBriefDTO> listFollowedPosts(Long uid, long cursor, int size) {
        requireUid(uid);
        int limit = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        LambdaQueryWrapper<DiscussionFollowPO> q = new LambdaQueryWrapper<DiscussionFollowPO>()
                .eq(DiscussionFollowPO::getUid, uid)
                .eq(DiscussionFollowPO::getFollowStatus, STATUS_FOLLOWING)
                .eq(DiscussionFollowPO::getIsDeleted, 0)
                .orderByDesc(DiscussionFollowPO::getUpdateTime)
                .last(SqlLimits.limit(limit + 1, 1, MAX_PAGE_SIZE + 1));
        if (cursor > 0) {
            q.lt(DiscussionFollowPO::getUpdateTime, java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZoneOffset.UTC));
        }
        List<DiscussionFollowPO> rows = discussionFollowMapper.selectList(q);
        if (rows.isEmpty()) {
            return PageResult.empty();
        }
        boolean hasMore = rows.size() > limit;
        List<DiscussionFollowPO> pageRows = hasMore ? rows.subList(0, limit) : rows;
        List<Long> postIds = pageRows.stream().map(DiscussionFollowPO::getPostId).filter(Objects::nonNull).toList();
        Map<Long, PostBriefDTO> posts = postFacade.batchGetPosts(postIds, uid);
        List<PostBriefDTO> items = postIds.stream()
                .map(posts::get)
                .filter(Objects::nonNull)
                .toList();
        String next = hasMore ? cursorOf(pageRows.get(pageRows.size() - 1)) : null;
        return PageResult.of(items, next, hasMore);
    }

    @Override
    public List<Long> followerUidsForNotification(Long postId, Set<Long> excludedUids, int limit) {
        requirePostId(postId);
        int safeLimit = Math.max(1, Math.min(limit, MAX_NOTIFICATION_TARGETS));
        int fetchLimit = Math.min(MAX_NOTIFICATION_TARGETS * 2, Math.max(safeLimit * 2, safeLimit));
        Set<Long> excluded = excludedUids == null ? Set.of() : new HashSet<>(excludedUids);
        return discussionFollowMapper.selectFollowerUidsForNotification(postId, fetchLimit)
                .stream()
                .filter(Objects::nonNull)
                .filter(uid -> uid > 0)
                .filter(uid -> !excluded.contains(uid))
                .distinct()
                .limit(safeLimit)
                .toList();
    }

    @Override
    public void markNotified(Long postId, Long uid, Long commentId) {
        if (postId == null || postId <= 0 || uid == null || uid <= 0 || commentId == null || commentId <= 0) {
            return;
        }
        discussionFollowMapper.markNotified(postId, uid, commentId);
    }

    private DiscussionFollowPO activeFollow(Long uid, Long postId) {
        return discussionFollowMapper.selectOne(new LambdaQueryWrapper<DiscussionFollowPO>()
                .eq(DiscussionFollowPO::getUid, uid)
                .eq(DiscussionFollowPO::getPostId, postId)
                .eq(DiscussionFollowPO::getFollowStatus, STATUS_FOLLOWING)
                .eq(DiscussionFollowPO::getIsDeleted, 0)
                .last(SqlLimits.limitOne()));
    }

    private PostDTO requirePostVisible(Long postId, Long uid) {
        PostDTO post = postFacade.getPost(postId, uid);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private static boolean isFollowing(DiscussionFollowPO po) {
        return po != null
                && Objects.equals(po.getFollowStatus(), STATUS_FOLLOWING)
                && Objects.equals(po.getIsDeleted(), 0);
    }

    private static DiscussionFollowStatusDTO toStatus(Long postId, DiscussionFollowPO po, String source) {
        return DiscussionFollowStatusDTO.builder()
                .postId(postId)
                .followed(isFollowing(po))
                .lastReadCommentId(po == null ? null : po.getLastReadCommentId())
                .lastNotifiedCommentId(po == null ? null : po.getLastNotifiedCommentId())
                .source(source)
                .build();
    }

    private static String cursorOf(DiscussionFollowPO po) {
        if (po == null || po.getUpdateTime() == null) {
            return null;
        }
        return String.valueOf(po.getUpdateTime().toInstant(ZoneOffset.UTC).toEpochMilli());
    }

    private static void requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static void requirePostId(Long postId) {
        if (postId == null || postId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }
}

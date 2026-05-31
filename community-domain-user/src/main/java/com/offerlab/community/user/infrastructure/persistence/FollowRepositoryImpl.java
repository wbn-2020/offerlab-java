package com.offerlab.community.user.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.user.api.dto.FollowCursorDTO;
import com.offerlab.community.user.domain.repository.FollowRepository;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserCounterMapper;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserFollowMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserFollowPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class FollowRepositoryImpl implements FollowRepository {

    private static final int MAX_PAGE_LIMIT = 1000;

    private final UserFollowMapper followMapper;
    private final UserCounterMapper counterMapper;
    private final SnowflakeIdGenerator idGen;

    @Override
    @Transactional
    public boolean follow(Long fromUid, Long toUid) {
        try {
            UserFollowPO existing = followMapper.selectAnyByPair(fromUid, toUid);
            if (existing != null) {
                if (existing.getIsDeleted() == 0) {
                    return false;
                }
                if (followMapper.restoreById(existing.getId()) <= 0) {
                    return false;
                }
            } else {
                UserFollowPO po = new UserFollowPO();
                po.setId(idGen.nextId());
                po.setFromUid(fromUid);
                po.setToUid(toUid);
                po.setIsDeleted(0);
                followMapper.insert(po);
            }
        } catch (DuplicateKeyException e) {
            return false;
        }
        counterMapper.initIfAbsent(fromUid);
        counterMapper.initIfAbsent(toUid);
        counterMapper.incrFollowing(fromUid, 1);
        counterMapper.incrFollower(toUid, 1);
        return true;
    }

    @Override
    @Transactional
    public boolean unfollow(Long fromUid, Long toUid) {
        UserFollowPO existing = followMapper.selectOne(new LambdaQueryWrapper<UserFollowPO>()
                .eq(UserFollowPO::getFromUid, fromUid)
                .eq(UserFollowPO::getToUid, toUid)
                .eq(UserFollowPO::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing == null) return false;
        if (followMapper.softDeleteById(existing.getId()) <= 0) {
            return false;
        }
        counterMapper.incrFollowing(fromUid, -1);
        counterMapper.incrFollower(toUid, -1);
        return true;
    }

    @Override
    public boolean isFollowing(Long fromUid, Long toUid) {
        Long cnt = followMapper.selectCount(new LambdaQueryWrapper<UserFollowPO>()
                .eq(UserFollowPO::getFromUid, fromUid)
                .eq(UserFollowPO::getToUid, toUid)
                .eq(UserFollowPO::getIsDeleted, 0));
        return cnt != null && cnt > 0;
    }

    @Override
    public List<Long> followingIds(Long uid, long cursor, int size) {
        return followingPage(uid, cursor, size).stream().map(FollowCursorDTO::getUid).toList();
    }

    @Override
    public List<FollowCursorDTO> followingPage(Long uid, long cursor, int size) {
        LambdaQueryWrapper<UserFollowPO> q = new LambdaQueryWrapper<UserFollowPO>()
                .eq(UserFollowPO::getFromUid, uid)
                .eq(UserFollowPO::getIsDeleted, 0)
                .orderByDesc(UserFollowPO::getId)
                .last("LIMIT " + pageLimit(size));
        if (cursor > 0) {
            q.lt(UserFollowPO::getId, cursor);
        }
        return followMapper.selectList(q).stream()
                .map(po -> FollowCursorDTO.builder()
                        .relationId(po.getId())
                        .uid(po.getToUid())
                        .build())
                .toList();
    }

    @Override
    public List<Long> followerIds(Long uid, long cursor, int size) {
        return followerPage(uid, cursor, size).stream().map(FollowCursorDTO::getUid).toList();
    }

    @Override
    public List<FollowCursorDTO> followerPage(Long uid, long cursor, int size) {
        LambdaQueryWrapper<UserFollowPO> q = new LambdaQueryWrapper<UserFollowPO>()
                .eq(UserFollowPO::getToUid, uid)
                .eq(UserFollowPO::getIsDeleted, 0)
                .orderByDesc(UserFollowPO::getId)
                .last("LIMIT " + pageLimit(size));
        if (cursor > 0) {
            q.lt(UserFollowPO::getId, cursor);
        }
        return followMapper.selectList(q).stream()
                .map(po -> FollowCursorDTO.builder()
                        .relationId(po.getId())
                        .uid(po.getFromUid())
                        .build())
                .toList();
    }

    @Override
    public long followerCount(Long uid) {
        return followMapper.selectCount(new LambdaQueryWrapper<UserFollowPO>()
                .eq(UserFollowPO::getToUid, uid)
                .eq(UserFollowPO::getIsDeleted, 0));
    }

    @Override
    public long followingCount(Long uid) {
        return followMapper.selectCount(new LambdaQueryWrapper<UserFollowPO>()
                .eq(UserFollowPO::getFromUid, uid)
                .eq(UserFollowPO::getIsDeleted, 0));
    }

    private static int pageLimit(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_LIMIT));
    }
}

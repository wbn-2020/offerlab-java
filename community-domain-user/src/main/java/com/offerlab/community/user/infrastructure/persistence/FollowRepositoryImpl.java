package com.offerlab.community.user.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.user.domain.repository.FollowRepository;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserCounterMapper;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserFollowMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserFollowPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class FollowRepositoryImpl implements FollowRepository {

    private final UserFollowMapper followMapper;
    private final UserCounterMapper counterMapper;
    private final SnowflakeIdGenerator idGen;

    @Override
    @Transactional
    public boolean follow(Long fromUid, Long toUid) {
        // 已存在记录（含软删）走更新逻辑；否则插入
        UserFollowPO existing = followMapper.selectOne(new LambdaQueryWrapper<UserFollowPO>()
                .eq(UserFollowPO::getFromUid, fromUid)
                .eq(UserFollowPO::getToUid, toUid)
                .last("LIMIT 1"));
        if (existing != null) {
            if (existing.getIsDeleted() == 0) {
                return false; // 已关注
            }
            existing.setIsDeleted(0);
            followMapper.updateById(existing);
        } else {
            UserFollowPO po = new UserFollowPO();
            po.setId(idGen.nextId());
            po.setFromUid(fromUid);
            po.setToUid(toUid);
            po.setIsDeleted(0);
            followMapper.insert(po);
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
        existing.setIsDeleted(1);
        followMapper.updateById(existing);
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
        LambdaQueryWrapper<UserFollowPO> q = new LambdaQueryWrapper<UserFollowPO>()
                .eq(UserFollowPO::getFromUid, uid)
                .eq(UserFollowPO::getIsDeleted, 0)
                .orderByDesc(UserFollowPO::getId)
                .last("LIMIT " + size);
        if (cursor > 0) {
            q.lt(UserFollowPO::getId, cursor);
        }
        return followMapper.selectList(q).stream().map(UserFollowPO::getToUid).toList();
    }

    @Override
    public List<Long> followerIds(Long uid, long cursor, int size) {
        LambdaQueryWrapper<UserFollowPO> q = new LambdaQueryWrapper<UserFollowPO>()
                .eq(UserFollowPO::getToUid, uid)
                .eq(UserFollowPO::getIsDeleted, 0)
                .orderByDesc(UserFollowPO::getId)
                .last("LIMIT " + size);
        if (cursor > 0) {
            q.lt(UserFollowPO::getId, cursor);
        }
        return followMapper.selectList(q).stream().map(UserFollowPO::getFromUid).toList();
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
}

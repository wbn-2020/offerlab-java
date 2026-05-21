package com.offerlab.community.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserCounterPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserCounterMapper extends BaseMapper<UserCounterPO> {

    @Update("INSERT INTO t_user_counter(user_id) VALUES (#{userId}) " +
            "ON DUPLICATE KEY UPDATE user_id = user_id")
    int initIfAbsent(@Param("userId") Long userId);

    @Update("UPDATE t_user_counter SET follower_count = follower_count + #{delta} " +
            "WHERE user_id = #{userId}")
    int incrFollower(@Param("userId") Long userId, @Param("delta") long delta);

    @Update("UPDATE t_user_counter SET following_count = following_count + #{delta} " +
            "WHERE user_id = #{userId}")
    int incrFollowing(@Param("userId") Long userId, @Param("delta") long delta);

    @Update("UPDATE t_user_counter SET post_count = post_count + #{delta} " +
            "WHERE user_id = #{userId}")
    int incrPost(@Param("userId") Long userId, @Param("delta") long delta);
}

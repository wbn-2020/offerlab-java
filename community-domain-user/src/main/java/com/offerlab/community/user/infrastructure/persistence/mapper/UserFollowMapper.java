package com.offerlab.community.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserFollowPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollowPO> {

    @Select("""
            SELECT id, from_uid, to_uid, create_time, is_deleted
            FROM t_user_follow
            WHERE from_uid = #{fromUid}
              AND to_uid = #{toUid}
            ORDER BY is_deleted ASC, create_time DESC
            LIMIT 1
            """)
    UserFollowPO selectAnyByPair(@Param("fromUid") Long fromUid, @Param("toUid") Long toUid);

    @Select("""
            <script>
            SELECT to_uid
            FROM t_user_follow
            WHERE from_uid = #{fromUid}
              AND is_deleted = 0
              AND to_uid IN
              <foreach collection="toUids" item="toUid" open="(" separator="," close=")">
                #{toUid}
              </foreach>
            </script>
            """)
    List<Long> selectFollowingTargets(@Param("fromUid") Long fromUid, @Param("toUids") Collection<Long> toUids);

    @Update("UPDATE t_user_follow SET is_deleted = 0 WHERE id = #{id} AND is_deleted = 1")
    int restoreById(@Param("id") Long id);

    @Update("UPDATE t_user_follow SET is_deleted = 1 WHERE id = #{id} AND is_deleted = 0")
    int softDeleteById(@Param("id") Long id);
}

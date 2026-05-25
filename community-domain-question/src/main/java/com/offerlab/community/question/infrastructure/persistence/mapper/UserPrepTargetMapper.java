package com.offerlab.community.question.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.question.infrastructure.persistence.po.UserPrepTargetPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserPrepTargetMapper extends BaseMapper<UserPrepTargetPO> {
    @Select("""
            SELECT *
            FROM t_user_prep_target
            WHERE uid = #{uid}
            ORDER BY create_time DESC, id DESC
            """)
    List<UserPrepTargetPO> selectByUser(@Param("uid") Long uid);

    @Insert("""
            INSERT INTO t_user_prep_target (id, uid, target_type, target_value)
            VALUES (#{id}, #{uid}, #{targetType}, #{targetValue})
            ON DUPLICATE KEY UPDATE create_time = create_time
            """)
    int insertIgnore(@Param("id") Long id,
                     @Param("uid") Long uid,
                     @Param("targetType") String targetType,
                     @Param("targetValue") String targetValue);

    @Delete("""
            DELETE FROM t_user_prep_target
            WHERE id = #{id}
              AND uid = #{uid}
            """)
    int deleteByUser(@Param("id") Long id, @Param("uid") Long uid);
}

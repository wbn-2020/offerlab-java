package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommentMapper extends BaseMapper<CommentPO> {

    @Update("UPDATE t_int_comment SET like_count = GREATEST(0, like_count + #{delta}) WHERE id = #{id} AND comment_status = 1 AND is_deleted = 0")
    int incrLikeCount(@Param("id") Long id, @Param("delta") int delta);
}

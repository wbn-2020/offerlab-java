package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostTagRefPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PostTagRefMapper extends BaseMapper<PostTagRefPO> {

    @Insert("INSERT IGNORE INTO t_post_tag_ref(id, post_id, tag_id) VALUES (#{id}, #{postId}, #{tagId})")
    int insertIgnore(@Param("id") Long id, @Param("postId") Long postId, @Param("tagId") Long tagId);

    @Update("UPDATE t_tag SET use_count = use_count + 1 WHERE id = #{tagId} AND is_deleted = 0")
    int incrUseCount(@Param("tagId") Long tagId);

    @Delete("DELETE FROM t_post_tag_ref WHERE post_id = #{postId}")
    int deleteByPostId(@Param("postId") Long postId);

    @Select("SELECT post_id FROM t_post_tag_ref WHERE tag_id = #{tagId}")
    List<Long> selectPostIdsByTagId(@Param("tagId") Long tagId);
}

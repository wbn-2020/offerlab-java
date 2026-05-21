package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import com.offerlab.community.post.infrastructure.persistence.projection.PostTagView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface TagMapper extends BaseMapper<TagPO> {

    @Select("""
            <script>
            SELECT id, tag_name, tag_type, use_count, is_official
            FROM t_tag
            WHERE is_deleted = 0
            ORDER BY is_official DESC, use_count DESC, id ASC
            </script>
            """)
    List<TagPO> selectActiveTags();

    @Select("""
            <script>
            SELECT post_id, id, tag_name, tag_type, use_count, is_official
            FROM (
                SELECT r.post_id, t.id, t.tag_name, t.tag_type, t.use_count, t.is_official
                FROM t_post_tag_ref r
                JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
                WHERE r.post_id IN
                <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                    #{postId}
                </foreach>
            ) x
            ORDER BY post_id ASC, id ASC
            </script>
            """)
    List<PostTagView> selectTagsByPostIds(@Param("postIds") Collection<Long> postIds);

    @Select("""
            <script>
            SELECT id, tag_name, tag_type, use_count, is_official
            FROM t_tag
            WHERE is_deleted = 0
              AND tag_name IN
              <foreach collection="names" item="name" open="(" separator="," close=")">
                  #{name}
              </foreach>
            </script>
            """)
    List<TagPO> selectByNames(@Param("names") Collection<String> names);
}

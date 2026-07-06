package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.CommunityTopicTagPO;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CommunityTopicTagMapper extends BaseMapper<CommunityTopicTagPO> {

    @Select("""
            <script>
            SELECT t.id, t.tag_name, t.tag_type, t.use_count, t.is_official,
                   t.tag_status, t.recommended, t.synonyms, t.merge_target_id
            FROM t_community_topic_tag r
            JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
              AND t.tag_status = 1
              AND t.merge_target_id IS NULL
            WHERE r.topic_id = #{topicId}
            ORDER BY t.is_official DESC, t.use_count DESC, t.id ASC
            </script>
            """)
    List<TagPO> selectTagsByTopicId(@Param("topicId") Long topicId);

    @Select("""
            <script>
            SELECT r.topic_id, t.id, t.tag_name, t.tag_type, t.use_count, t.is_official,
                   t.tag_status, t.recommended, t.synonyms, t.merge_target_id
            FROM t_community_topic_tag r
            JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
              AND t.tag_status = 1
              AND t.merge_target_id IS NULL
            WHERE r.topic_id IN
            <foreach collection="topicIds" item="topicId" open="(" separator="," close=")">
                #{topicId}
            </foreach>
            ORDER BY r.topic_id ASC, t.is_official DESC, t.use_count DESC, t.id ASC
            </script>
            """)
    List<java.util.Map<String, Object>> selectTagsByTopicIds(@Param("topicIds") Collection<Long> topicIds);

    @Delete("DELETE FROM t_community_topic_tag WHERE topic_id = #{topicId}")
    int deleteByTopicId(@Param("topicId") Long topicId);
}

package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.ContentSuggestionPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ContentSuggestionMapper extends BaseMapper<ContentSuggestionPO> {

    @Insert("""
            INSERT INTO t_int_content_suggestion (
                id, post_id, post_author_id, submitter_uid, suggestion_type,
                detail, normalized_content_hash, source_url, allow_public_attribution,
                decision, author_reply, public_note, result_version, pending_dedup_key,
                decided_at, create_time, update_time
            )
            VALUES (
                #{id}, #{postId}, #{postAuthorId}, #{submitterUid}, #{suggestionType},
                #{detail}, #{normalizedContentHash}, #{sourceUrl}, #{allowPublicAttribution},
                NULL, NULL, NULL, NULL, #{pendingDedupKey},
                NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
            )
            """)
    int insertSuggestion(ContentSuggestionPO suggestion);

    @Select("""
            SELECT id, post_id, post_author_id, submitter_uid, suggestion_type,
                   detail, normalized_content_hash, source_url, allow_public_attribution,
                   decision, author_reply, public_note, result_version, pending_dedup_key,
                   decided_at, create_time, update_time
            FROM t_int_content_suggestion
            WHERE post_id = #{postId}
              AND submitter_uid = #{submitterUid}
              AND suggestion_type = #{suggestionType}
              AND normalized_content_hash = #{normalizedContentHash}
              AND decision IS NULL
            LIMIT 1
            """)
    ContentSuggestionPO selectPendingByContent(@Param("postId") Long postId,
                                               @Param("submitterUid") Long submitterUid,
                                               @Param("suggestionType") String suggestionType,
                                               @Param("normalizedContentHash") String normalizedContentHash);

    @Select("""
            SELECT id, post_id, post_author_id, submitter_uid, suggestion_type,
                   detail, normalized_content_hash, source_url, allow_public_attribution,
                   decision, author_reply, public_note, result_version, pending_dedup_key,
                   decided_at, create_time, update_time
            FROM t_int_content_suggestion
            WHERE id = #{id}
            FOR UPDATE
            """)
    ContentSuggestionPO selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            <script>
            SELECT id, post_id, post_author_id, submitter_uid, suggestion_type,
                   detail, normalized_content_hash, source_url, allow_public_attribution,
                   decision, author_reply, public_note, result_version, pending_dedup_key,
                   decided_at, create_time, update_time
            FROM t_int_content_suggestion
            WHERE post_id = #{postId}
              AND submitter_uid = #{submitterUid}
              <if test='status == "PENDING"'>
              AND decision IS NULL
              </if>
              <if test='status == "DECIDED"'>
              AND decision IS NOT NULL
              AND decided_at IS NOT NULL
              </if>
              <choose>
                <when test='status == "DECIDED"'>
                ORDER BY decided_at DESC, id DESC
                </when>
                <otherwise>
                ORDER BY update_time DESC, id DESC
                </otherwise>
              </choose>
            LIMIT #{limit}
            </script>
            """)
    List<ContentSuggestionPO> listMine(@Param("postId") Long postId,
                                       @Param("submitterUid") Long submitterUid,
                                       @Param("status") String status,
                                       @Param("limit") int limit);

    @Select("""
            <script>
            SELECT id, post_id, post_author_id, submitter_uid, suggestion_type,
                   detail, normalized_content_hash, source_url, allow_public_attribution,
                   decision, author_reply, public_note, result_version, pending_dedup_key,
                   decided_at, create_time, update_time
            FROM t_int_content_suggestion
            WHERE post_id = #{postId}
              <if test='status == "PENDING"'>
              AND decision IS NULL
              </if>
              <if test='status == "DECIDED"'>
              AND decision IS NOT NULL
              AND decided_at IS NOT NULL
              </if>
              <choose>
                <when test='status == "DECIDED"'>
                ORDER BY decided_at DESC, id DESC
                </when>
                <otherwise>
                ORDER BY update_time DESC, id DESC
                </otherwise>
              </choose>
            LIMIT #{limit}
            </script>
            """)
    List<ContentSuggestionPO> listForAuthor(@Param("postId") Long postId,
                                            @Param("status") String status,
                                            @Param("limit") int limit);

    @Select("""
            SELECT id, post_id, post_author_id, submitter_uid, suggestion_type,
                   detail, normalized_content_hash, source_url, allow_public_attribution,
                   decision, author_reply, public_note, result_version, pending_dedup_key,
                   decided_at, create_time, update_time
            FROM t_int_content_suggestion
            WHERE post_id = #{postId}
              AND public_note IS NOT NULL
              AND CHAR_LENGTH(TRIM(public_note)) > 0
              AND decision IN ('ACCEPTED', 'PARTIAL_ACCEPTED', 'MERGED')
              AND decided_at IS NOT NULL
            ORDER BY decided_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<ContentSuggestionPO> listPublicDecisions(@Param("postId") Long postId,
                                                  @Param("limit") int limit);

    @Select("""
            <script>
            SELECT id, post_id, post_author_id, submitter_uid, suggestion_type,
                   detail, normalized_content_hash, source_url, allow_public_attribution,
                   decision, author_reply, public_note, result_version, pending_dedup_key,
                   decided_at, create_time, update_time
            FROM t_int_content_suggestion
            WHERE id IN
              <foreach collection="suggestionIds" item="suggestionId" open="(" separator="," close=")">
                #{suggestionId}
              </foreach>
            ORDER BY id ASC
            FOR UPDATE
            </script>
            """)
    List<ContentSuggestionPO> selectByIdsForUpdate(@Param("suggestionIds") List<Long> suggestionIds);

    @Update("""
            UPDATE t_int_content_suggestion
            SET decision = #{decision},
                author_reply = #{authorReply},
                public_note = #{publicNote},
                result_version = #{resultVersion},
                pending_dedup_key = NULL,
                decided_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND decision IS NULL
            """)
    int decide(@Param("id") Long id,
               @Param("decision") String decision,
               @Param("authorReply") String authorReply,
               @Param("publicNote") String publicNote,
               @Param("resultVersion") Integer resultVersion);

    @Select("""
            SELECT COUNT(*)
            FROM t_int_content_suggestion
            WHERE post_id = #{postId}
              AND suggestion_type = #{suggestionType}
              AND decision IS NULL
            """)
    long countPendingByPostAndType(@Param("postId") Long postId,
                                   @Param("suggestionType") String suggestionType);
}

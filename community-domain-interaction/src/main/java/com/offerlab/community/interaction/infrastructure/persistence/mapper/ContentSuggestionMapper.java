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
                detail, normalized_content_hash, source_url, base_version,
                target_scope, target_locator, expected_change, allow_public_attribution,
                decision, resolution, delivery_status, author_reply, public_note,
                result_version, pending_dedup_key,
                decided_at, create_time, update_time
            )
            VALUES (
                #{id}, #{postId}, #{postAuthorId}, #{submitterUid}, #{suggestionType},
                #{detail}, #{normalizedContentHash}, #{sourceUrl}, #{baseVersion},
                #{targetScope}, #{targetLocator}, #{expectedChange}, #{allowPublicAttribution},
                NULL, 'PENDING', 'UNLINKED', NULL, NULL, NULL, #{pendingDedupKey},
                NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
            )
            """)
    int insertSuggestion(ContentSuggestionPO suggestion);

    @Select("""
            SELECT id, post_id, post_author_id, submitter_uid, suggestion_type,
                   detail, normalized_content_hash, source_url, base_version,
                   target_scope, target_locator, expected_change, allow_public_attribution,
                   decision, resolution, delivery_status, author_reply, public_note,
                   result_version, pending_dedup_key,
                   decided_at, create_time, update_time
            FROM t_int_content_suggestion
            WHERE post_id = #{postId}
              AND submitter_uid = #{submitterUid}
              AND suggestion_type = #{suggestionType}
              AND normalized_content_hash = #{normalizedContentHash}
              AND resolution = 'PENDING'
              AND COALESCE(decision, '') = ''
            LIMIT 1
            """)
    ContentSuggestionPO selectPendingByContent(@Param("postId") Long postId,
                                               @Param("submitterUid") Long submitterUid,
                                               @Param("suggestionType") String suggestionType,
                                               @Param("normalizedContentHash") String normalizedContentHash);

    @Select("""
            SELECT id, post_id, post_author_id, submitter_uid, suggestion_type,
                   detail, normalized_content_hash, source_url, base_version,
                   target_scope, target_locator, expected_change, allow_public_attribution,
                   decision, resolution, delivery_status, author_reply, public_note,
                   result_version, pending_dedup_key,
                   decided_at, create_time, update_time
            FROM t_int_content_suggestion
            WHERE id = #{id}
            FOR UPDATE
            """)
    ContentSuggestionPO selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            <script>
            SELECT id, post_id, post_author_id, submitter_uid, suggestion_type,
                   detail, normalized_content_hash, source_url, base_version,
                   target_scope, target_locator, expected_change, allow_public_attribution,
                   decision, resolution, delivery_status, author_reply, public_note,
                   result_version, pending_dedup_key,
                   decided_at, create_time, update_time
            FROM t_int_content_suggestion
            WHERE post_id = #{postId}
              AND submitter_uid = #{submitterUid}
              <if test='status == "PENDING"'>
              AND resolution = 'PENDING'
              AND COALESCE(decision, '') = ''
              </if>
              <if test='status == "DECIDED"'>
              AND (resolution &lt;&gt; 'PENDING' OR COALESCE(decision, '') &lt;&gt; '')
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
                   detail, normalized_content_hash, source_url, base_version,
                   target_scope, target_locator, expected_change, allow_public_attribution,
                   decision, resolution, delivery_status, author_reply, public_note,
                   result_version, pending_dedup_key,
                   decided_at, create_time, update_time
            FROM t_int_content_suggestion
            WHERE post_id = #{postId}
              <if test='status == "PENDING"'>
              AND resolution = 'PENDING'
              AND COALESCE(decision, '') = ''
              </if>
              <if test='status == "DECIDED"'>
              AND (resolution &lt;&gt; 'PENDING' OR COALESCE(decision, '') &lt;&gt; '')
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
                   detail, normalized_content_hash, source_url, base_version,
                   target_scope, target_locator, expected_change, allow_public_attribution,
                   decision, resolution, delivery_status, author_reply, public_note,
                   result_version, pending_dedup_key,
                   decided_at, create_time, update_time
            FROM t_int_content_suggestion
            WHERE post_id = #{postId}
              AND public_note IS NOT NULL
              AND CHAR_LENGTH(TRIM(public_note)) > 0
              AND (
                    resolution IN ('ACCEPTED', 'PARTIAL', 'PLANNED')
                    OR (
                        resolution = 'PENDING'
                        AND (
                            decision IN ('ACCEPTED', 'PARTIAL_ACCEPTED', 'MERGED')
                            OR decision = 'PLANNED'
                        )
                    )
              )
              AND decided_at IS NOT NULL
            ORDER BY decided_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<ContentSuggestionPO> listPublicDecisions(@Param("postId") Long postId,
                                                  @Param("limit") int limit);

    @Select("""
            <script>
            SELECT id, post_id, post_author_id, submitter_uid, suggestion_type,
                   detail, normalized_content_hash, source_url, base_version,
                   target_scope, target_locator, expected_change, allow_public_attribution,
                   decision, resolution, delivery_status, author_reply, public_note,
                   result_version, pending_dedup_key,
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
                resolution = #{resolution},
                delivery_status = 'UNLINKED',
                author_reply = #{authorReply},
                public_note = #{publicNote},
                result_version = NULL,
                pending_dedup_key = NULL,
                decided_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND resolution = 'PENDING'
              AND COALESCE(decision, '') = ''
            """)
    int resolve(@Param("id") Long id,
                @Param("decision") String decision,
                @Param("resolution") String resolution,
                @Param("authorReply") String authorReply,
                @Param("publicNote") String publicNote);

    @Update("""
            UPDATE t_int_content_suggestion
            SET decision = CASE
                    WHEN COALESCE(decision, '') = '' THEN #{decision}
                    ELSE decision
                END,
                resolution = #{resolution},
                delivery_status = 'LINKED',
                result_version = #{resultVersion},
                pending_dedup_key = NULL,
                decided_at = COALESCE(decided_at, CURRENT_TIMESTAMP(3)),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND delivery_status = 'UNLINKED'
              AND result_version IS NULL
              AND resolution <> 'REJECTED'
              AND COALESCE(decision, '') <> 'REJECTED'
              AND #{resolution} IN ('ACCEPTED', 'PARTIAL', 'PLANNED')
            """)
    int linkToVersion(@Param("id") Long id,
                      @Param("decision") String decision,
                      @Param("resolution") String resolution,
                      @Param("resultVersion") Integer resultVersion);

    @Select("""
            SELECT COUNT(*)
            FROM t_int_content_suggestion
            WHERE post_id = #{postId}
              AND suggestion_type = #{suggestionType}
              AND resolution = 'PENDING'
              AND COALESCE(decision, '') = ''
            """)
    long countPendingByPostAndType(@Param("postId") Long postId,
                                   @Param("suggestionType") String suggestionType);
}

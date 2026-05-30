package com.offerlab.community.infra.moderation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ContentModerationMapper {
    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = #{tableName}
            """)
    int tableExists(@Param("tableName") String tableName);

    @Select("""
            SELECT id, keyword, match_type AS matchType, action, scope
            FROM t_moderation_keyword
            WHERE enabled = 1
              AND (scope = 'ALL' OR scope = #{scope})
            ORDER BY update_time DESC
            LIMIT 200
            """)
    List<ModerationKeyword> listEnabledKeywords(@Param("scope") String scope);

    @Select("""
            SELECT uid,
                   muted_until AS mutedUntil,
                   banned_until AS bannedUntil,
                   reason,
                   operator_uid AS operatorUid
            FROM t_user_moderation_state
            WHERE uid = #{uid}
            """)
    UserModerationState findUserState(@Param("uid") Long uid);

    @Select("""
            <script>
            SELECT id, keyword, match_type AS matchType, action, scope,
                   enabled, remark, operator_uid AS operatorUid
            FROM t_moderation_keyword
            WHERE 1 = 1
              <if test="keyword != null and keyword != ''">
                AND keyword LIKE CONCAT('%', #{keyword}, '%')
              </if>
              <if test="scope != null and scope != ''">
                AND scope = #{scope}
              </if>
            ORDER BY enabled DESC, update_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ModerationKeyword> listKeywords(@Param("keyword") String keyword,
                                         @Param("scope") String scope,
                                         @Param("limit") int limit);

    @Select("""
            SELECT id, keyword, match_type AS matchType, action, scope,
                   enabled, remark, operator_uid AS operatorUid
            FROM t_moderation_keyword
            WHERE id = #{id}
            """)
    ModerationKeyword findKeywordById(@Param("id") Long id);

    @Insert("""
            INSERT INTO t_moderation_keyword_hit (
                id, scope, uid, keyword_id, keyword, action, content_summary
            )
            VALUES (
                #{id}, #{scope}, #{uid}, #{keywordId}, #{keyword}, #{action}, #{contentSummary}
            )
            """)
    int insertKeywordHit(ModerationKeywordHit hit);

    @Select("""
            <script>
            SELECT id, scope, uid, keyword_id AS keywordId, keyword, action,
                   content_summary AS contentSummary, create_time AS createTime
            FROM t_moderation_keyword_hit
            WHERE 1 = 1
              <if test="scope != null and scope != ''">
                AND scope = #{scope}
              </if>
              <if test="action != null and action != ''">
                AND action = #{action}
              </if>
              <if test="uid != null">
                AND uid = #{uid}
              </if>
              <if test="keyword != null and keyword != ''">
                AND keyword LIKE CONCAT('%', #{keyword}, '%')
              </if>
            ORDER BY create_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ModerationKeywordHit> listKeywordHits(@Param("scope") String scope,
                                               @Param("action") String action,
                                               @Param("uid") Long uid,
                                               @Param("keyword") String keyword,
                                               @Param("limit") int limit);

    @Insert("""
            INSERT INTO t_moderation_keyword (
                id, keyword, match_type, action, scope, enabled, remark, operator_uid
            )
            VALUES (
                #{id}, #{keyword}, #{matchType}, #{action}, #{scope}, #{enabled}, #{remark}, #{operatorUid}
            )
            """)
    int insertKeyword(ModerationKeyword keyword);

    @Update("""
            UPDATE t_moderation_keyword
            SET keyword = #{keyword},
                match_type = #{matchType},
                action = #{action},
                scope = #{scope},
                enabled = #{enabled},
                remark = #{remark},
                operator_uid = #{operatorUid},
                update_time = NOW(3)
            WHERE id = #{id}
            """)
    int updateKeyword(ModerationKeyword keyword);

    @Update("""
            UPDATE t_moderation_keyword
            SET enabled = #{enabled},
                operator_uid = #{operatorUid},
                update_time = NOW(3)
            WHERE id = #{id}
            """)
    int updateKeywordStatus(@Param("id") Long id,
                            @Param("enabled") int enabled,
                            @Param("operatorUid") Long operatorUid);

    @Select("""
            SELECT uid,
                   muted_until AS mutedUntil,
                   banned_until AS bannedUntil,
                   reason,
                   operator_uid AS operatorUid
            FROM t_user_moderation_state
            ORDER BY update_time DESC
            LIMIT #{limit}
            """)
    List<UserModerationState> listUserStates(@Param("limit") int limit);

    @Select("""
            <script>
            SELECT x.uid,
                   x.keyword AS recentViolationKeyword,
                   x.action AS recentViolationAction,
                   x.content_summary AS recentViolationSummary,
                   x.create_time AS recentViolationTime
            FROM (
                SELECT h.*,
                       ROW_NUMBER() OVER (PARTITION BY h.uid ORDER BY h.create_time DESC, h.id DESC) AS rn
                FROM t_moderation_keyword_hit h
                WHERE h.uid IN
                <foreach collection="uids" item="uid" open="(" separator="," close=")">
                  #{uid}
                </foreach>
            ) x
            WHERE x.rn = 1
            </script>
            """)
    List<UserModerationState> listLatestUserViolations(@Param("uids") List<Long> uids);

    @Insert("""
            INSERT INTO t_user_moderation_state (
                uid, muted_until, banned_until, reason, operator_uid
            )
            VALUES (
                #{uid}, #{mutedUntil}, #{bannedUntil}, #{reason}, #{operatorUid}
            )
            ON DUPLICATE KEY UPDATE
                muted_until = VALUES(muted_until),
                banned_until = VALUES(banned_until),
                reason = VALUES(reason),
                operator_uid = VALUES(operator_uid),
                update_time = NOW(3)
            """)
    int upsertUserState(@Param("uid") Long uid,
                        @Param("mutedUntil") LocalDateTime mutedUntil,
                        @Param("bannedUntil") LocalDateTime bannedUntil,
                        @Param("reason") String reason,
                        @Param("operatorUid") Long operatorUid);

    @Update("""
            UPDATE t_user_moderation_state
            SET muted_until = NULL,
                reason = #{reason},
                operator_uid = #{operatorUid},
                update_time = NOW(3)
            WHERE uid = #{uid}
            """)
    int clearMute(@Param("uid") Long uid,
                  @Param("reason") String reason,
                  @Param("operatorUid") Long operatorUid);

    @Update("""
            UPDATE t_user_moderation_state
            SET banned_until = NULL,
                reason = #{reason},
                operator_uid = #{operatorUid},
                update_time = NOW(3)
            WHERE uid = #{uid}
            """)
    int clearBan(@Param("uid") Long uid,
                 @Param("reason") String reason,
                 @Param("operatorUid") Long operatorUid);
}

package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.PostOutcomePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface PostOutcomeMapper extends BaseMapper<PostOutcomePO> {

    @Select("""
            SELECT id, post_id, uid, outcome_type, context_note, result_note, visibility,
                   publication_status, consented_at, reviewer_uid, review_note, reviewed_at,
                   follow_up_at, outcome_status, revision, create_time, update_time, is_deleted
            FROM t_int_post_outcome
            WHERE post_id = #{postId}
              AND uid = #{uid}
              AND outcome_status = 'ACTIVE'
              AND is_deleted = 0
            LIMIT 1
            """)
    PostOutcomePO selectActiveMine(@Param("postId") Long postId, @Param("uid") Long uid);

    @Select("""
            SELECT id, post_id, uid, outcome_type, context_note, result_note, visibility,
                   publication_status, consented_at, reviewer_uid, review_note, reviewed_at,
                   follow_up_at, outcome_status, revision, create_time, update_time, is_deleted
            FROM t_int_post_outcome
            WHERE id = #{id}
              AND is_deleted = 0
            LIMIT 1
            """)
    PostOutcomePO selectActiveById(@Param("id") Long id);

    @Insert("""
            INSERT INTO t_int_post_outcome (
                id, post_id, uid, outcome_type, context_note, result_note, visibility,
                publication_status, consented_at, follow_up_at, outcome_status, revision,
                create_time, update_time, is_deleted
            ) VALUES (
                #{row.id}, #{row.postId}, #{row.uid}, #{row.outcomeType}, #{row.contextNote},
                #{row.resultNote}, #{row.visibility}, #{row.publicationStatus}, #{row.consentedAt},
                #{row.followUpAt}, 'ACTIVE', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
            )
            """)
    int insertOutcome(@Param("row") PostOutcomePO row);

    @Update("""
            UPDATE t_int_post_outcome
            SET outcome_type = #{row.outcomeType},
                context_note = #{row.contextNote},
                result_note = #{row.resultNote},
                visibility = #{row.visibility},
                publication_status = #{row.publicationStatus},
                consented_at = #{row.consentedAt},
                reviewer_uid = NULL,
                review_note = NULL,
                reviewed_at = NULL,
                follow_up_at = #{row.followUpAt},
                revision = revision + 1,
                update_time = #{now}
            WHERE id = #{row.id}
              AND post_id = #{row.postId}
              AND uid = #{row.uid}
              AND revision = #{expectedRevision}
              AND outcome_status = 'ACTIVE'
              AND is_deleted = 0
            """)
    int updateIfRevision(@Param("row") PostOutcomePO row,
                         @Param("expectedRevision") Integer expectedRevision,
                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_int_post_outcome
            SET outcome_status = 'WITHDRAWN',
                publication_status = 'WITHDRAWN',
                revision = revision + 1,
                update_time = #{now}
            WHERE id = #{id}
              AND post_id = #{postId}
              AND uid = #{uid}
              AND revision = #{expectedRevision}
              AND outcome_status = 'ACTIVE'
              AND is_deleted = 0
            """)
    int withdrawIfRevision(@Param("id") Long id,
                           @Param("postId") Long postId,
                           @Param("uid") Long uid,
                           @Param("expectedRevision") Integer expectedRevision,
                           @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_int_post_outcome
            SET publication_status = CASE WHEN #{approved} = 1 THEN 'PUBLISHED' ELSE 'REJECTED' END,
                reviewer_uid = #{reviewerUid},
                review_note = #{note},
                reviewed_at = CURRENT_TIMESTAMP(3),
                revision = revision + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND publication_status = 'PENDING_REVIEW'
              AND outcome_status = 'ACTIVE'
              AND is_deleted = 0
            """)
    int reviewPending(@Param("id") Long id,
                      @Param("reviewerUid") Long reviewerUid,
                      @Param("approved") boolean approved,
                      @Param("note") String note);

    @Select("""
            SELECT outcome_type AS outcomeType, COUNT(*) AS outcomeCount
            FROM t_int_post_outcome
            WHERE post_id = #{postId}
              AND uid <> #{postAuthorUid}
              AND outcome_status = 'ACTIVE'
              AND publication_status = 'PUBLISHED'
              AND visibility IN ('PUBLIC_ANONYMOUS', 'PUBLIC_ATTRIBUTED')
              AND is_deleted = 0
            GROUP BY outcome_type
            """)
    List<Map<String, Object>> countPublicByType(@Param("postId") Long postId,
                                                @Param("postAuthorUid") Long postAuthorUid);

    @Select("""
            SELECT id, outcome_type AS outcomeType, result_note AS resultNote, uid,
                   visibility, create_time AS createdAt
            FROM t_int_post_outcome
            WHERE post_id = #{postId}
              AND uid <> #{postAuthorUid}
              AND outcome_status = 'ACTIVE'
              AND publication_status = 'PUBLISHED'
              AND visibility IN ('PUBLIC_ANONYMOUS', 'PUBLIC_ATTRIBUTED')
              AND is_deleted = 0
            ORDER BY create_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<PublicOutcomeRow> listPublicSamples(@Param("postId") Long postId,
                                             @Param("postAuthorUid") Long postAuthorUid,
                                             @Param("limit") int limit);

    @Select("""
            SELECT id, post_id AS postId, uid, outcome_type AS outcomeType,
                   publication_status AS publicationStatus, outcome_status AS outcomeStatus,
                   follow_up_at AS followUpAt, revision, update_time AS updateTime
            FROM t_int_post_outcome
            WHERE uid = #{uid}
              AND outcome_status = 'ACTIVE'
              AND publication_status IN ('PENDING_REVIEW', 'REJECTED')
              AND is_deleted = 0
              AND (follow_up_at IS NULL OR follow_up_at <= CURRENT_TIMESTAMP(3))
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<KnowledgeOutcomeRow> listKnowledgeActions(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            SELECT publication_status AS publicationStatus, COUNT(*) AS actionCount
            FROM t_int_post_outcome
            WHERE uid = #{uid}
              AND outcome_status = 'ACTIVE'
              AND is_deleted = 0
              AND publication_status IN ('PENDING_REVIEW', 'REJECTED')
            GROUP BY publication_status
            """)
    List<Map<String, Object>> countKnowledgeActions(@Param("uid") Long uid);

    @Select("""
            SELECT id, post_id AS postId, uid, outcome_type AS outcomeType,
                   publication_status AS publicationStatus, outcome_status AS outcomeStatus,
                   follow_up_at AS followUpAt, revision, update_time AS updateTime
            FROM t_int_post_outcome
            WHERE id = #{id}
              AND is_deleted = 0
            LIMIT 1
            """)
    KnowledgeOutcomeRow selectKnowledgeOutcome(@Param("id") Long id);

    @Select("""
            SELECT id, post_id AS postId, uid, outcome_type AS outcomeType,
                   publication_status AS publicationStatus, outcome_status AS outcomeStatus,
                   follow_up_at AS followUpAt, revision, update_time AS updateTime
            FROM t_int_post_outcome
            WHERE id = #{id}
              AND uid = #{uid}
              AND is_deleted = 0
            LIMIT 1
            """)
    KnowledgeOutcomeRow selectOwnedKnowledgeOutcome(@Param("id") Long id, @Param("uid") Long uid);

    class PublicOutcomeRow {
        private Long id;
        private String outcomeType;
        private String resultNote;
        private Long uid;
        private String visibility;
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getOutcomeType() { return outcomeType; }
        public void setOutcomeType(String outcomeType) { this.outcomeType = outcomeType; }
        public String getResultNote() { return resultNote; }
        public void setResultNote(String resultNote) { this.resultNote = resultNote; }
        public Long getUid() { return uid; }
        public void setUid(Long uid) { this.uid = uid; }
        public String getVisibility() { return visibility; }
        public void setVisibility(String visibility) { this.visibility = visibility; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    class KnowledgeOutcomeRow {
        private Long id;
        private Long postId;
        private Long uid;
        private String outcomeType;
        private String publicationStatus;
        private String outcomeStatus;
        private LocalDateTime followUpAt;
        private Integer revision;
        private LocalDateTime updateTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getPostId() { return postId; }
        public void setPostId(Long postId) { this.postId = postId; }
        public Long getUid() { return uid; }
        public void setUid(Long uid) { this.uid = uid; }
        public String getOutcomeType() { return outcomeType; }
        public void setOutcomeType(String outcomeType) { this.outcomeType = outcomeType; }
        public String getPublicationStatus() { return publicationStatus; }
        public void setPublicationStatus(String publicationStatus) { this.publicationStatus = publicationStatus; }
        public String getOutcomeStatus() { return outcomeStatus; }
        public void setOutcomeStatus(String outcomeStatus) { this.outcomeStatus = outcomeStatus; }
        public LocalDateTime getFollowUpAt() { return followUpAt; }
        public void setFollowUpAt(LocalDateTime followUpAt) { this.followUpAt = followUpAt; }
        public Integer getRevision() { return revision; }
        public void setRevision(Integer revision) { this.revision = revision; }
        public LocalDateTime getUpdateTime() { return updateTime; }
        public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    }
}

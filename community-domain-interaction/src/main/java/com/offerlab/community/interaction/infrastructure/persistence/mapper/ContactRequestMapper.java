package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.ContactRequestPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ContactRequestMapper extends BaseMapper<ContactRequestPO> {

    @Select("""
            SELECT id, requester_uid, receiver_uid, source_type, source_id, scene, message_preview,
                   request_status, receiver_action_time, expire_time, report_id, dedup_key,
                   create_time, update_time, is_deleted
            FROM t_int_contact_request
            WHERE requester_uid = #{requesterUid}
              AND receiver_uid = #{receiverUid}
              AND request_status = 'PENDING'
              AND is_deleted = 0
              AND (expire_time IS NULL OR expire_time > #{now})
            ORDER BY create_time DESC
            LIMIT 1
            """)
    ContactRequestPO selectActivePending(@Param("requesterUid") Long requesterUid,
                                         @Param("receiverUid") Long receiverUid,
                                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_int_contact_request
            SET request_status = 'EXPIRED',
                dedup_key = CONCAT('contact:', requester_uid, ':', receiver_uid, ':', id, ':EXPIRED'),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE requester_uid = #{requesterUid}
              AND receiver_uid = #{receiverUid}
              AND request_status = 'PENDING'
              AND is_deleted = 0
              AND expire_time IS NOT NULL
              AND expire_time <= #{now}
            """)
    int expireStalePending(@Param("requesterUid") Long requesterUid,
                           @Param("receiverUid") Long receiverUid,
                           @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_int_contact_request
            SET request_status = 'EXPIRED',
                dedup_key = CONCAT('contact:', requester_uid, ':', receiver_uid, ':', id, ':EXPIRED'),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE receiver_uid = #{receiverUid}
              AND request_status = 'PENDING'
              AND is_deleted = 0
              AND expire_time IS NOT NULL
              AND expire_time <= #{now}
            LIMIT #{limit}
            """)
    int expireStalePendingForReceiver(@Param("receiverUid") Long receiverUid,
                                      @Param("now") LocalDateTime now,
                                      @Param("limit") int limit);

    @Update("""
            UPDATE t_int_contact_request
            SET request_status = 'EXPIRED',
                dedup_key = CONCAT('contact:', requester_uid, ':', receiver_uid, ':', id, ':EXPIRED'),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE requester_uid = #{requesterUid}
              AND request_status = 'PENDING'
              AND is_deleted = 0
              AND expire_time IS NOT NULL
              AND expire_time <= #{now}
            LIMIT #{limit}
            """)
    int expireStalePendingForRequester(@Param("requesterUid") Long requesterUid,
                                       @Param("now") LocalDateTime now,
                                       @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM t_int_contact_request
            WHERE requester_uid = #{requesterUid}
              AND is_deleted = 0
              AND create_time >= #{since}
            """)
    long countCreatedSince(@Param("requesterUid") Long requesterUid,
                           @Param("since") LocalDateTime since);

    @Select("""
            SELECT GET_LOCK(CONCAT('contact_request_daily:', #{requesterUid}, ':', DATE_FORMAT(CURRENT_DATE(), '%Y%m%d')), 3)
            """)
    Integer acquireDailyLimitLock(@Param("requesterUid") Long requesterUid);

    @Select("""
            SELECT RELEASE_LOCK(CONCAT('contact_request_daily:', #{requesterUid}, ':', DATE_FORMAT(CURRENT_DATE(), '%Y%m%d')))
            """)
    Integer releaseDailyLimitLock(@Param("requesterUid") Long requesterUid);

    @Select("""
            SELECT id, requester_uid, receiver_uid, source_type, source_id, scene, message_preview,
                   request_status, receiver_action_time, expire_time, report_id, dedup_key,
                   create_time, update_time, is_deleted
            FROM t_int_contact_request
            WHERE id = #{id}
              AND is_deleted = 0
            LIMIT 1
            """)
    ContactRequestPO selectActiveById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT id, requester_uid, receiver_uid, source_type, source_id, scene, message_preview,
                   request_status, receiver_action_time, expire_time, report_id, dedup_key,
                   create_time, update_time, is_deleted
            FROM t_int_contact_request
            WHERE receiver_uid = #{receiverUid}
              AND is_deleted = 0
              <if test="status != null">
              AND request_status = #{status}
              </if>
              <if test="cursorTime != null">
              AND (
                update_time &lt; #{cursorTime}
                OR (#{cursorId} IS NOT NULL AND update_time = #{cursorTime} AND id &lt; #{cursorId})
              )
              </if>
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ContactRequestPO> listInbox(@Param("receiverUid") Long receiverUid,
                                     @Param("status") String status,
                                     @Param("cursorTime") LocalDateTime cursorTime,
                                     @Param("cursorId") Long cursorId,
                                     @Param("limit") int limit);

    @Select("""
            <script>
            SELECT id, requester_uid, receiver_uid, source_type, source_id, scene, message_preview,
                   request_status, receiver_action_time, expire_time, report_id, dedup_key,
                   create_time, update_time, is_deleted
            FROM t_int_contact_request
            WHERE requester_uid = #{requesterUid}
              AND is_deleted = 0
              <if test="status != null">
              AND request_status = #{status}
              </if>
              <if test="cursorTime != null">
              AND (
                update_time &lt; #{cursorTime}
                OR (#{cursorId} IS NOT NULL AND update_time = #{cursorTime} AND id &lt; #{cursorId})
              )
              </if>
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ContactRequestPO> listOutbox(@Param("requesterUid") Long requesterUid,
                                      @Param("status") String status,
                                      @Param("cursorTime") LocalDateTime cursorTime,
                                      @Param("cursorId") Long cursorId,
                                      @Param("limit") int limit);

    @Select("""
            SELECT request_status AS status, COUNT(*) AS count
            FROM t_int_contact_request
            WHERE receiver_uid = #{receiverUid}
              AND is_deleted = 0
            GROUP BY request_status
            """)
    List<Map<String, Object>> countInboxByStatus(@Param("receiverUid") Long receiverUid);

    @Select("""
            SELECT request_status AS status, COUNT(*) AS count
            FROM t_int_contact_request
            WHERE requester_uid = #{requesterUid}
              AND is_deleted = 0
            GROUP BY request_status
            """)
    List<Map<String, Object>> countOutboxByStatus(@Param("requesterUid") Long requesterUid);

    @Update("""
            UPDATE t_int_contact_request
            SET request_status = 'EXPIRED',
                dedup_key = CONCAT('contact:', requester_uid, ':', receiver_uid, ':', id, ':EXPIRED'),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND request_status = 'PENDING'
              AND is_deleted = 0
              AND expire_time IS NOT NULL
              AND expire_time <= CURRENT_TIMESTAMP(3)
            """)
    int expirePendingById(@Param("id") Long id);

    @Update("""
            UPDATE t_int_contact_request
            SET request_status = #{newStatus},
                receiver_action_time = CURRENT_TIMESTAMP(3),
                report_id = #{reportId},
                dedup_key = CONCAT('contact:', requester_uid, ':', receiver_uid, ':', id, ':', #{newStatus}),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND receiver_uid = #{receiverUid}
              AND request_status = 'PENDING'
              AND is_deleted = 0
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
            """)
    int updatePendingStatusAsReceiver(@Param("id") Long id,
                                      @Param("receiverUid") Long receiverUid,
                                      @Param("newStatus") String newStatus,
                                      @Param("reportId") Long reportId);

    @Select("""
            <script>
            SELECT id, requester_uid, receiver_uid, source_type, source_id, scene, message_preview,
                   request_status, receiver_action_time, expire_time, report_id, dedup_key,
                   create_time, update_time, is_deleted
            FROM t_int_contact_request
            WHERE receiver_uid = #{receiverUid}
              AND request_status = 'REPORTED'
              AND report_id = id
              AND is_deleted = 0
              <if test="cursorId != null">
              AND id &lt; #{cursorId}
              </if>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ContactRequestPO> selectIndependentReportsAsReceiver(@Param("receiverUid") Long receiverUid,
                                                              @Param("cursorId") Long cursorId,
                                                              @Param("limit") int limit);

    @Select("""
            SELECT id, requester_uid, receiver_uid, source_type, source_id, scene, message_preview,
                   request_status, receiver_action_time, expire_time, report_id, dedup_key,
                   create_time, update_time, is_deleted
            FROM t_int_contact_request
            WHERE id = #{reportId}
              AND receiver_uid = #{receiverUid}
              AND request_status = 'REPORTED'
              AND report_id = id
              AND is_deleted = 0
            LIMIT 1
            """)
    ContactRequestPO selectIndependentReportById(@Param("receiverUid") Long receiverUid,
                                                 @Param("reportId") Long reportId);

    @Update("""
            UPDATE t_int_contact_request
            SET update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND request_status = 'REPORTED'
              AND report_id = id
              AND is_deleted = 0
            """)
    int touchReportResolved(@Param("id") Long id);
}

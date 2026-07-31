package com.offerlab.community.analytics.infrastructure.persistence.mapper;

import com.offerlab.community.analytics.infrastructure.persistence.po.GrowthEventPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface GrowthEventMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_growth_event'
            """)
    int tableExists();

    @Insert("""
            INSERT INTO t_growth_event(
                id, event_key, event_type, uid, domain, content_id, target_type, target_value, source_page, ext_json
            ) VALUES (
                #{id}, #{eventKey}, #{eventType}, #{uid}, #{domain}, #{contentId}, #{targetType}, #{targetValue}, #{sourcePage}, #{extJson}
            )
            """)
    int insertEvent(GrowthEventPO event);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM t_growth_event
            WHERE create_time &gt;= #{since}
              <if test="domain != null">
              AND domain = #{domain}
              </if>
            </script>
            """)
    long countTotal(@Param("since") LocalDateTime since, @Param("domain") Integer domain);

    @Select("""
            <script>
            SELECT event_type AS name, COUNT(*) AS count
            FROM t_growth_event
            WHERE create_time &gt;= #{since}
              <if test="domain != null">
              AND domain = #{domain}
              </if>
            GROUP BY event_type
            ORDER BY COUNT(*) DESC, event_type ASC
            </script>
            """)
    List<Map<String, Object>> countByEventType(@Param("since") LocalDateTime since, @Param("domain") Integer domain);

    @Select("""
            SELECT COALESCE(domain, 0) AS name, COUNT(*) AS count
            FROM t_growth_event
            WHERE create_time >= #{since}
            GROUP BY COALESCE(domain, 0)
            ORDER BY COUNT(*) DESC, name ASC
            """)
    List<Map<String, Object>> countByDomain(@Param("since") LocalDateTime since);

    @Select("""
            <script>
            SELECT DATE(create_time) AS label, COUNT(*) AS count
            FROM t_growth_event
            WHERE create_time &gt;= #{since}
              <if test="domain != null">
              AND domain = #{domain}
              </if>
            GROUP BY DATE(create_time)
            ORDER BY DATE(create_time) ASC
            </script>
            """)
    List<Map<String, Object>> countByDate(@Param("since") LocalDateTime since, @Param("domain") Integer domain);

    @Select("""
            SELECT COALESCE(domain, 0) AS domain,
                   event_type AS eventType,
                   COUNT(*) AS count
            FROM t_growth_event
            WHERE uid = #{uid}
              AND create_time >= #{since}
            GROUP BY COALESCE(domain, 0), event_type
            ORDER BY domain ASC, event_type ASC
            """)
    List<Map<String, Object>> countUserEventsByDomain(@Param("uid") Long uid,
                                                      @Param("since") LocalDateTime since);

    @Delete("""
            DELETE FROM t_growth_event
            WHERE event_type = #{eventType}
              AND create_time < #{before}
            ORDER BY create_time ASC
            LIMIT #{limit}
            """)
    int deleteBefore(@Param("eventType") String eventType,
                     @Param("before") LocalDateTime before,
                     @Param("limit") int limit);
}

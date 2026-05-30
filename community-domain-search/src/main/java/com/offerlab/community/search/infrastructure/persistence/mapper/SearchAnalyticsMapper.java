package com.offerlab.community.search.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.search.infrastructure.persistence.po.SearchAnalyticsEventPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SearchAnalyticsMapper extends BaseMapper<SearchAnalyticsEventPO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_search_analytics_event'
            """)
    int tableExists();

    @Insert("""
            INSERT INTO t_search_analytics_event (
                id, event_type, uid, keyword, company, position, post_type, sort_type, result_count
            ) VALUES (
                #{id}, #{eventType}, #{uid}, #{keyword}, #{company}, #{position}, #{postType}, #{sortType}, #{resultCount}
            )
            """)
    int insertEvent(SearchAnalyticsEventPO event);

    @Select("""
            SELECT keyword,
                   COUNT(*) AS count,
                   SUM(CASE WHEN result_count = 0 THEN 1 ELSE 0 END) AS noResultCount,
                   MAX(result_count) AS lastResultCount,
                   MAX(create_time) AS lastSearchedAt
            FROM t_search_analytics_event
            WHERE event_type = 'SEARCH'
              AND keyword IS NOT NULL
              AND keyword <> ''
              AND create_time >= DATE_SUB(NOW(3), INTERVAL #{days} DAY)
            GROUP BY keyword
            ORDER BY count DESC, lastSearchedAt DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> topSearchKeywords(@Param("days") int days, @Param("limit") int limit);

    @Select("""
            SELECT keyword,
                   COUNT(*) AS count,
                   COUNT(*) AS noResultCount,
                   MAX(result_count) AS lastResultCount,
                   MAX(create_time) AS lastSearchedAt
            FROM t_search_analytics_event
            WHERE event_type = 'SEARCH'
              AND result_count = 0
              AND keyword IS NOT NULL
              AND keyword <> ''
              AND create_time >= DATE_SUB(NOW(3), INTERVAL #{days} DAY)
            GROUP BY keyword
            ORDER BY noResultCount DESC, lastSearchedAt DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> topNoResultKeywords(@Param("days") int days, @Param("limit") int limit);

    @Select("""
            SELECT company,
                   COUNT(*) AS count,
                   MAX(create_time) AS lastSearchedAt
            FROM t_search_analytics_event
            WHERE event_type = 'PREP_CLICK'
              AND company IS NOT NULL
              AND company <> ''
              AND create_time >= DATE_SUB(NOW(3), INTERVAL #{days} DAY)
            GROUP BY company
            ORDER BY count DESC, lastSearchedAt DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> topPrepClicks(@Param("days") int days, @Param("limit") int limit);
}
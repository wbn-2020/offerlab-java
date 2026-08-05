package com.offerlab.community.feed.infrastructure.persistence.mapper;

import com.offerlab.community.feed.infrastructure.persistence.po.FeedRevisionAwareQualitySignalWindowQuery;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedFeedbackPreferenceMapperTest {

    @Test
    void revisionAwareAggregateIsFeedOnlyAndAppliesEveryV32Condition() throws Exception {
        Method method = FeedFeedbackPreferenceMapper.class.getMethod(
                "aggregateRevisionAwareActiveQualitySignals",
                List.class,
                LocalDateTime.class,
                LocalDateTime.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join("\n", select.value()).toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("from t_feed_feedback_preference"));
        assertTrue(sql.contains("reason = 'v30:quality_not_expected'"));
        assertTrue(sql.contains("action in ('hide', 'less_like_this')"));
        assertTrue(sql.contains("expires_at &gt; #{now}"));
        assertTrue(sql.contains("update_time &gt;= #{basewindowstart}"));
        assertTrue(sql.contains("uid &lt;&gt; #{window.authorid}"));
        assertTrue(sql.contains("update_time &lt;= #{window.windowstart}"));
        assertTrue(sql.contains("update_time &gt; #{window.windowstart}"));
        assertTrue(sql.contains("count(distinct case"));
        assertFalse(sql.contains(" join "));
        assertFalse(sql.contains("t_post_"));
    }

    @Test
    void revisionAwareAggregateRendersBothBoundaryModes() throws Exception {
        Method method = FeedFeedbackPreferenceMapper.class.getMethod(
                "aggregateRevisionAwareActiveQualitySignals",
                List.class,
                LocalDateTime.class,
                LocalDateTime.class);
        Select select = method.getAnnotation(Select.class);
        LocalDateTime boundary = LocalDateTime.of(2026, 8, 1, 1, 0);
        String renderedSql = new XMLLanguageDriver()
                .createSqlSource(new Configuration(), select.value()[0], Map.class)
                .getBoundSql(Map.of(
                        "windows", List.of(
                                new FeedRevisionAwareQualitySignalWindowQuery(701L, 71L, boundary, true),
                                new FeedRevisionAwareQualitySignalWindowQuery(702L, 72L, null, false)),
                        "baseWindowStart", LocalDateTime.of(2026, 8, 1, 0, 0),
                        "now", LocalDateTime.of(2026, 8, 4, 0, 0)))
                .getSql()
                .toLowerCase(Locale.ROOT);

        assertTrue(renderedSql.contains("update_time <= ?"));
        assertTrue(renderedSql.contains("update_time > ?"));
        assertTrue(renderedSql.contains("and 1 = 0"));
        assertTrue(renderedSql.contains("and 1 = 1"));
        assertTrue(renderedSql.contains("group by post_id"));
    }
}

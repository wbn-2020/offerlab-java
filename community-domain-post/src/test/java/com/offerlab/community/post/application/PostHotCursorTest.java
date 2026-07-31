package com.offerlab.community.post.application;

import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.common.exception.BizException;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostHotCursorTest {

    @Test
    void cursorRoundTripUsesTheExactSqlRankingAnchor() {
        PostMapper.HotPostRow row = new PostMapper.HotPostRow();
        row.setId(9_001_234_567_890_123_456L);
        row.setHotScore(12_345L);
        row.setCreateTime(LocalDateTime.of(2026, 7, 25, 12, 30, 15, 123_000_000));
        row.setRankingTime(LocalDateTime.of(2026, 7, 26, 8, 45, 30, 456_000_000));

        String encoded = PostFacadeImpl.HotCursor.of(row);
        PostFacadeImpl.HotCursor parsed = PostFacadeImpl.HotCursor.parse(encoded);

        assertEquals(row.getHotScore(), parsed.score());
        assertEquals(row.getCreateTime(), parsed.time());
        assertEquals(row.getId(), parsed.id());
        assertEquals(row.getRankingTime(), parsed.rankingTime());
    }

    @Test
    void legacyOrMalformedCursorFailsClosedInsteadOfRestartingAtTheFirstPage() {
        assertThrows(BizException.class,
                () -> PostFacadeImpl.HotCursor.parse("1234:1721900000000:99"));
        assertThrows(BizException.class,
                () -> PostFacadeImpl.HotCursor.parse("v2:not-a-time:score:time:id"));

        PostFacadeImpl.HotCursor firstPage = PostFacadeImpl.HotCursor.parse("0");
        assertNull(firstPage.score());
        assertNull(firstPage.rankingTime());
    }

    @Test
    void mapperUsesOneDatabaseRankingTimeAndIntegerScoreForPredicateAndOrder() throws Exception {
        Select select = PostMapper.class.getMethod(
                        "selectHotPosts",
                        Long.class,
                        LocalDateTime.class,
                        Long.class,
                        Integer.class,
                        LocalDateTime.class,
                        int.class)
                .getAnnotation(Select.class);
        String sql = String.join("\n", select.value()).toLowerCase();

        assertTrue(sql.contains("coalesce(#{rankingtime}, current_timestamp(3))"));
        assertTrue(sql.contains("context.rankingtime as rankingtime"));
        assertTrue(sql.contains("coalesce(c.like_count, 0) * 30"));
        assertTrue(sql.contains("coalesce(c.view_count, 0) * 2"));
        assertTrue(sql.contains("hotscore < #{cursorscore}"));
        assertTrue(sql.contains("order by hotscore desc, create_time desc, id desc"));
        assertFalse(sql.contains("now()"));
        assertFalse(sql.contains("* 0.2"));
    }

    @Test
    void hotPageCursorIsNotRecomputedFromRedisCounters() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/offerlab/community/post/application/PostFacadeImpl.java"),
                StandardCharsets.UTF_8);
        int start = source.indexOf("private PageResult<PostBriefDTO> pagedHotPo");
        int end = source.indexOf("private int pageSize", start);
        String method = source.substring(start, end);

        assertTrue(method.contains("HotCursor.of(cursorPost)"));
        assertFalse(method.contains("batchGetCounters"));
        assertFalse(method.contains("LocalDateTime.now"));
    }
}

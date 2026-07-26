package com.offerlab.community.post.application;

import com.offerlab.community.post.infrastructure.persistence.PostRepositoryImpl;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostKeysetCursorTest {

    private static final long SNOWFLAKE_EPOCH_MILLIS = 1609459200000L;
    private static final long SNOWFLAKE_ID =
            ((Instant.parse("2026-07-25T12:00:00Z").toEpochMilli()
                    - SNOWFLAKE_EPOCH_MILLIS) << 22)
                    | (1L << 17)
                    | (1L << 12);

    @Test
    void listCursorPreservesTheCompleteSnowflakeId() throws Exception {
        Method method = PostFacadeImpl.class.getDeclaredMethod("listCursor", Long.class);
        method.setAccessible(true);

        assertEquals(String.valueOf(SNOWFLAKE_ID), method.invoke(null, SNOWFLAKE_ID));
    }

    @Test
    void repositoryPassesTheCompleteCursorIdWithoutModuloTruncation() {
        AtomicReference<Long> capturedCursor = new AtomicReference<>();
        PostMapper mapper = (PostMapper) Proxy.newProxyInstance(
                PostMapper.class.getClassLoader(),
                new Class<?>[]{PostMapper.class},
                (proxy, method, args) -> {
                    if ("selectPublicPosts".equals(method.getName())) {
                        capturedCursor.set((Long) args[6]);
                        return List.of();
                    }
                    return null;
                });
        PostRepositoryImpl repository = new PostRepositoryImpl(mapper, null, null, null);

        repository.findPosts(null, null, null, null, null, SNOWFLAKE_ID, 20);

        assertEquals(SNOWFLAKE_ID, capturedCursor.get());
    }

    @Test
    void repositoryDecodesTheLegacyCompositeCursorWithoutTreatingItAsAnId() {
        long legacyMillis = Instant.now().minusSeconds(3600).toEpochMilli();
        long legacyCursor = legacyMillis * 1_000_000L + 123_456L;
        AtomicReference<LocalDateTime> capturedTime = new AtomicReference<>();
        AtomicReference<Long> capturedCursor = new AtomicReference<>();
        PostMapper mapper = (PostMapper) Proxy.newProxyInstance(
                PostMapper.class.getClassLoader(),
                new Class<?>[]{PostMapper.class},
                (proxy, method, args) -> {
                    if ("selectPublicPosts".equals(method.getName())) {
                        capturedTime.set((LocalDateTime) args[5]);
                        capturedCursor.set((Long) args[6]);
                        return List.of();
                    }
                    return null;
                });
        PostRepositoryImpl repository = new PostRepositoryImpl(mapper, null, null, null);

        repository.findPosts(null, null, null, null, null, legacyCursor, 20);

        assertEquals(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(legacyMillis), ZoneOffset.UTC),
                capturedTime.get());
        assertNull(capturedCursor.get());
    }

    @Test
    void mapperPredicateAndOrderingUseTheSameIdKeyset() throws Exception {
        Select select = PostMapper.class.getMethod(
                        "selectPublicPosts",
                        Long.class,
                        Long.class,
                        Integer.class,
                        Boolean.class,
                        Integer.class,
                        LocalDateTime.class,
                        Long.class,
                        int.class)
                .getAnnotation(Select.class);
        String sql = String.join("\n", select.value());

        assertTrue(sql.contains("AND p.id &lt; #{cursorId}"));
        assertTrue(sql.contains("AND p.create_time &lt; #{cursorTime}"));
        assertTrue(sql.contains("<choose>"));
        assertTrue(sql.contains("ORDER BY p.id DESC"));
        assertFalse(sql.contains("p.id &lt; #{cursorId}))"));
        assertFalse(sql.contains("1_000_000"));
    }

    @Test
    void explicitKeysetEntryPassesTheCompleteTimeAndIdTuple() {
        LocalDateTime cursorTime = LocalDateTime.of(2026, 7, 25, 12, 0);
        AtomicReference<LocalDateTime> capturedTime = new AtomicReference<>();
        AtomicReference<Long> capturedCursor = new AtomicReference<>();
        PostMapper mapper = (PostMapper) Proxy.newProxyInstance(
                PostMapper.class.getClassLoader(),
                new Class<?>[]{PostMapper.class},
                (proxy, method, args) -> {
                    if ("selectPublicPostsByTimeKeyset".equals(method.getName())) {
                        capturedTime.set((LocalDateTime) args[5]);
                        capturedCursor.set((Long) args[6]);
                        return List.of();
                    }
                    return null;
                });
        PostRepositoryImpl repository = new PostRepositoryImpl(mapper, null, null, null);

        repository.findPostsByKeyset(
                null,
                null,
                null,
                null,
                null,
                cursorTime,
                SNOWFLAKE_ID,
                20);

        assertEquals(cursorTime, capturedTime.get());
        assertEquals(SNOWFLAKE_ID, capturedCursor.get());
    }

    @Test
    void explicitKeysetPredicateAndOrderingUseTheSameTimeIdTuple() throws Exception {
        Select select = PostMapper.class.getMethod(
                        "selectPublicPostsByTimeKeyset",
                        Long.class,
                        Long.class,
                        Integer.class,
                        Boolean.class,
                        Integer.class,
                        LocalDateTime.class,
                        Long.class,
                        int.class)
                .getAnnotation(Select.class);
        String sql = String.join("\n", select.value());

        assertTrue(sql.contains("p.create_time &lt; #{cursorTime}"));
        assertTrue(sql.contains("p.create_time = #{cursorTime} AND p.id &lt; #{cursorId}"));
        assertTrue(sql.contains("ORDER BY p.create_time DESC, p.id DESC"));
    }
}

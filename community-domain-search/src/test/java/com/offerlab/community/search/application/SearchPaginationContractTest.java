package com.offerlab.community.search.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchPaginationContractTest {

    private static final long POST_ID = 9_001_234_567_890_123_456L;
    private static final LocalDateTime CREATE_TIME = LocalDateTime.of(2026, 7, 25, 12, 0);

    @Test
    void relevanceCursorCarriesTheExactElasticsearchSortTuple() throws Exception {
        AtomicReference<Map<String, Object>> capturedBody = new AtomicReference<>();
        ObjectMapper objectMapper = new ObjectMapper();
        ElasticsearchHttpClient elasticsearch = new ElasticsearchHttpClient(null, objectMapper, null) {
            @Override
            public String postIndex() {
                return "post_idx";
            }

            @Override
            public Optional<JsonNode> search(String index, Map<String, Object> body) {
                capturedBody.set(body);
                try {
                    return Optional.of(objectMapper.readTree("{\"hits\":{\"hits\":[]}}"));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        SearchFacadeImpl facade = new SearchFacadeImpl(
                null, null, null, objectMapper, elasticsearch, null, null, null, null);
        String cursor = relevanceCursor(4.25D);

        Method search = SearchFacadeImpl.class.getDeclaredMethod(
                "searchByElasticsearch",
                String.class,
                String.class,
                String.class,
                Integer.class,
                Integer.class,
                String.class,
                String.class,
                int.class,
                boolean.class
        );
        search.setAccessible(true);
        search.invoke(facade, "java", null, null, null, null,
                "relevance", cursor, 10, false);

        long millis = CREATE_TIME.toInstant(ZoneOffset.UTC).toEpochMilli();
        assertEquals(List.of(4.25D, millis, POST_ID), capturedBody.get().get("search_after"));
        assertEquals(List.of(
                Map.of("_score", Map.of("order", "desc")),
                Map.of("createTime", Map.of("order", "desc")),
                Map.of("postId", Map.of("order", "desc"))
        ), capturedBody.get().get("sort"));
        String query = capturedBody.get().get("query").toString();
        assertFalse(query.contains("range={createTime"));
    }

    @Test
    void hotFallbackSqlUsesOneStableDatabaseKeyset() throws Exception {
        Select select = PostMapper.class.getMethod(
                        "searchPublicPostsHotFallback",
                        String.class,
                        Long.class,
                        String.class,
                        String.class,
                        Integer.class,
                        Integer.class,
                        boolean.class,
                        LocalDateTime.class,
                        Long.class,
                        LocalDateTime.class,
                        Long.class,
                        int.class)
                .getAnnotation(Select.class);
        String sql = String.join("\n", select.value());

        assertTrue(sql.contains("ranked.hotScore &lt; #{cursorHotScore}"));
        assertTrue(sql.contains("ranked.createTime &lt; #{cursorTime}"));
        assertTrue(sql.contains("ranked.postId &lt; #{cursorId}"));
        assertTrue(sql.contains("ORDER BY ranked.hotScore DESC, ranked.createTime DESC, ranked.postId DESC"));
        assertTrue(sql.contains("TIMESTAMPDIFF(HOUR, p.create_time, #{rankingTime})"));
        assertTrue(sql.contains("LIMIT #{limit}"));
    }

    private static String relevanceCursor(double score) throws Exception {
        Class<?> valuesType = List.of(SearchFacadeImpl.class.getDeclaredClasses()).stream()
                .filter(type -> "SearchSortValues".equals(type.getSimpleName()))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = valuesType.getDeclaredConstructor(
                Double.class, Long.class, long.class, long.class, long.class);
        constructor.setAccessible(true);
        Object values = constructor.newInstance(
                score,
                null,
                CREATE_TIME.toInstant(ZoneOffset.UTC).toEpochMilli(),
                POST_ID,
                0L
        );
        Method cursorMethod = SearchFacadeImpl.class.getDeclaredMethod(
                "searchCursor", String.class, valuesType);
        cursorMethod.setAccessible(true);
        return (String) cursorMethod.invoke(null, "relevance", values);
    }
}

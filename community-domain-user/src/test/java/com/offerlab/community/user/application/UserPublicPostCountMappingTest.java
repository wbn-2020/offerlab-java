package com.offerlab.community.user.application;

import com.offerlab.community.user.infrastructure.persistence.mapper.UserPublicContentMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPublicPostCountMappingTest {

    @Test
    void countMappingAcceptsCommonMyBatisAliasesAndSkipsMalformedRows() throws Exception {
        Map<String, Object> nullCount = new HashMap<>();
        nullCount.put("author_id", 3L);
        nullCount.put("post_count", null);
        UserPublicContentMapper mapper = stub(List.of(
                Map.of("authorId", 1L, "postCount", 2L),
                Map.of("AUTHOR_ID", "2", "POST_COUNT", "3"),
                Map.of("AUTHORID", 1L, "POSTCOUNT", 4L),
                Map.of("author_id", "invalid", "post_count", 9L),
                nullCount));

        Map<Long, Long> counts = invokePublicPostCounts(mapper, List.of(1L, 2L, 3L));

        assertEquals(Map.of(1L, 4L, 2L, 3L), counts);
    }

    @Test
    void nullAndEmptyMapperResultsBecomeEmptyCounts() throws Exception {
        assertTrue(invokePublicPostCounts(stub(null), List.of(1L)).isEmpty());
        assertTrue(invokePublicPostCounts(stub(List.of()), List.of(1L)).isEmpty());
        assertTrue(invokePublicPostCounts(stubThatMustNotBeCalled(), List.of()).isEmpty());
    }

    private static UserPublicContentMapper stub(List<Map<String, Object>> countRows) {
        return new UserPublicContentMapper() {
            @Override
            public List<Map<String, Object>> countPublicPostsByAuthors(Collection<Long> authorIds) {
                return countRows;
            }

            @Override
            public List<Map<String, Object>> topPublicAuthors(int limit) {
                throw new AssertionError("publicPostCounts must not call topPublicAuthors");
            }
        };
    }

    private static UserPublicContentMapper stubThatMustNotBeCalled() {
        return new UserPublicContentMapper() {
            @Override
            public List<Map<String, Object>> countPublicPostsByAuthors(Collection<Long> authorIds) {
                throw new AssertionError("empty author list must not query the mapper");
            }

            @Override
            public List<Map<String, Object>> topPublicAuthors(int limit) {
                throw new AssertionError("empty author list must not query the mapper");
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Long> invokePublicPostCounts(UserPublicContentMapper mapper,
                                                          List<Long> authorIds) throws Exception {
        Constructor<?> constructor = UserApplicationService.class.getDeclaredConstructors()[0];
        Object[] arguments = new Object[constructor.getParameterCount()];
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        for (int index = 0; index < parameterTypes.length; index++) {
            if (parameterTypes[index] == UserPublicContentMapper.class) {
                arguments[index] = mapper;
            }
        }
        UserApplicationService service = (UserApplicationService) constructor.newInstance(arguments);
        Method method = UserApplicationService.class.getDeclaredMethod("publicPostCounts", List.class);
        method.setAccessible(true);
        return (Map<Long, Long>) method.invoke(service, authorIds);
    }
}

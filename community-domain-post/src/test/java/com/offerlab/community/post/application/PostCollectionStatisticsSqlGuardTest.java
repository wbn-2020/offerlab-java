package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostCollectionStatisticsSqlGuardTest {

    private static final Path ROOT = Path.of(System.getProperty("user.dir")).getParent();

    @Test
    void tagStatisticsUseTheSamePublicCommunityBoundaryAsTheTagList() throws Exception {
        String source = read("community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/TagMapper.java");
        String countSql = section(source, "long countPublicPostsByTag", "List<java.util.Map<String, Object>> countPublicPostTypesByTag");
        String distributionSql = section(source, "List<java.util.Map<String, Object>> countPublicPostTypesByTag", "@Select(\"\"\"");

        assertPublicBoundary(countSql);
        assertPublicBoundary(distributionSql);
        assertTrue(countSql.contains("r.tag_id = #{tagId}"));
        assertTrue(distributionSql.contains("r.tag_id = #{tagId}"));
        assertTrue(countSql.contains("t.tag_status = 1"));
        assertTrue(distributionSql.contains("t.merge_target_id IS NULL"));
        assertFalse(countSql.contains("LIMIT"));
        assertFalse(distributionSql.contains("LIMIT"));
    }

    @Test
    void topicStatisticsAggregateTheWholeTopicWithTheListMatchingRules() throws Exception {
        String source = read("community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java");
        String countSql = section(source, "long countPublicPostsByTopic", "List<Map<String, Object>> countPublicPostTypesByTopic");
        String distributionSql = section(source, "List<Map<String, Object>> countPublicPostTypesByTopic", "@Select(\"\"\"");

        assertPublicBoundary(countSql);
        assertPublicBoundary(distributionSql);
        assertTrue(countSql.contains("ptr.tag_id IN"));
        assertTrue(distributionSql.contains("ptr.tag_id IN"));
        assertTrue(countSql.contains("$.contextTopicId"));
        assertTrue(distributionSql.contains("$.topicNames"));
        assertFalse(countSql.contains("cursorTime"));
        assertFalse(distributionSql.contains("LIMIT"));
    }

    private static void assertPublicBoundary(String source) {
        assertTrue(source.contains("p.is_deleted = 0"));
        assertTrue(source.contains("p.post_status = 1"));
        assertTrue(source.contains("p.visibility = 1"));
        assertTrue(source.contains("p.content_environment = 'COMMUNITY'"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(ROOT.resolve(path));
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        if (start < 0 || end < 0) {
            throw new AssertionError("Unable to locate SQL section: " + startMarker);
        }
        int annotationStart = source.lastIndexOf("@Select", start);
        return source.substring(annotationStart, end);
    }
}

package com.offerlab.community.interaction.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRevisitVisibilityGuardTest {

    @Test
    void persistedRevisitsMustRecheckCurrentPostVisibility() throws Exception {
        String mapper = Files.readString(Path.of(
                "src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/UserRevisitItemMapper.java"
        ), StandardCharsets.UTF_8);

        assertOccurrences(mapper, "ON p.id = CAST(SUBSTRING(r.target_path, 7) AS UNSIGNED)", 2);
        assertOccurrences(mapper, "AND r.target_path = CONCAT('/post/', p.id)", 2);
        assertOccurrences(mapper, "AND p.is_deleted = 0", 5);
        assertOccurrences(mapper, "AND p.post_status = 1", 5);
        assertOccurrences(mapper, "AND p.visibility = 1", 5);
    }

    private static void assertOccurrences(String source, String expected, int minimum) {
        int count = source.split(java.util.regex.Pattern.quote(expected), -1).length - 1;
        assertTrue(count >= minimum,
                () -> "Expected at least " + minimum + " occurrences of: " + expected + ", found " + count);
    }
}

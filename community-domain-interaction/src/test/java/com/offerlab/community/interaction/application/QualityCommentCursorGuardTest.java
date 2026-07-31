package com.offerlab.community.interaction.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityCommentCursorGuardTest {

    @Test
    void qualityPaginationStaysAnchorFreeSoHiddenCommentsCannotDeadlockPaging() throws Exception {
        String mapper = Files.readString(
                Path.of("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/CommentMapper.java"),
                StandardCharsets.UTF_8);
        String facade = Files.readString(
                Path.of("src/main/java/com/offerlab/community/interaction/application/InteractionFacadeImpl.java"),
                StandardCharsets.UTF_8);

        // The quality cursor must carry the full sort key so pagination never
        // depends on the anchor comment row still being visible (V16 fix for the
        // pre-composite dead loop; audit item 5.1).
        assertTrue(mapper.contains("#{cursorId} IS NULL"),
                "quality roots query must be a value-carrying keyset query");
        assertFalse(mapper.contains("cursor_rank"),
                "quality roots query must not look up the anchor row's rank");
        assertTrue(facade.contains("QUALITY_CURSOR_VERSION"),
                "quality cursor must stay versioned");
        assertTrue(facade.contains("parts.length != 8"),
                "quality cursor must stay self-contained with the full 7-part sort key");
        int parseStart = facade.indexOf("private static QualityCursor parseQualityCursor(");
        assertTrue(parseStart >= 0, "quality cursor parser must exist");
        String parseBody = facade.substring(parseStart, facade.indexOf("private static int parseQualityBit", parseStart));
        assertFalse(parseBody.contains("selectById"),
                "quality cursor parsing must never load the anchor comment");
    }
}

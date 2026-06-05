package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostSyntheticPaginationGuardTest {

    @Test
    void publicPostListsMustOverScanBeforeFilteringSyntheticContent() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/offerlab/community/post/application/PostFacadeImpl.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("scanSize(limit)"),
                "public post list queries should scan beyond the requested page before filtering synthetic content");
        assertTrue(source.contains("visibleHasMore"),
                "pagination should calculate visible page state after synthetic filtering");
        assertTrue(source.contains("rawHasMore"),
                "pagination should still advance cursors when a scanned batch contains only filtered records");
        assertTrue(source.contains("scanPublicPosts"),
                "public post lists should keep scanning when synthetic rows fill the first database window");
        assertTrue(source.contains("MAX_SYNTHETIC_SCAN_ROWS"),
                "synthetic filtering fallback must be bounded so a bad dataset cannot trigger an unbounded scan");
        assertTrue(source.contains("hasVisiblePageAfterSyntheticFiltering"),
                "public post lists should stop scanning only after enough visible records are found");
        assertTrue(source.contains(".filter(tag -> !PublicContentFilter.isSyntheticText(tag.getName()))"),
                "public tag lists must hide explicit synthetic tag names");
    }
}

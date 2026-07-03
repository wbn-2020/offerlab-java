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
        String repository = Files.readString(
                Path.of("src/main/java/com/offerlab/community/post/domain/repository/PostRepository.java"),
                StandardCharsets.UTF_8);
        String repositoryImpl = Files.readString(
                Path.of("src/main/java/com/offerlab/community/post/infrastructure/persistence/PostRepositoryImpl.java"),
                StandardCharsets.UTF_8);
        String mapper = Files.readString(
                Path.of("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java"),
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
        assertTrue(source.contains("scanPublicPosts(authorId, tagId, postType, featured, activeDomain, cursor, limit)"),
                "domain filtering must be part of the database scan window before pagination metadata is calculated");
        assertTrue(!source.contains("In-memory domain filter for Phase 1"),
                "domain filtering after scan/page metadata calculation reintroduces false hasMore/nextCursor");
        assertTrue(repository.contains("Integer domain, long cursor, int size"),
                "repository contract must accept domain so PostFacade can push filtering into the query layer");
        assertTrue(repositoryImpl.contains("selectPublicPosts(authorId, tagId != null && tagId > 0 ? tagId : null, postType,\n                featured, domain,"),
                "repository implementation must pass domain to the public post mapper");
        assertTrue(mapper.contains("@Param(\"domain\") Integer domain"),
                "public post mapper must receive domain as a SQL parameter");
        assertTrue(mapper.contains("COALESCE(e_domain.domain, 1) = #{domain}"),
                "public post SQL must filter legacy null domains as TECH through t_post_extension.domain before ORDER BY/LIMIT");
    }
}

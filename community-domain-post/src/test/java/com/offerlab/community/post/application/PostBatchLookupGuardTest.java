package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostBatchLookupGuardTest {

    @Test
    void postBatchLookupsMustNormalizeAndCapInputIds() throws Exception {
        String facade = Files.readString(Path.of("src/main/java/com/offerlab/community/post/application/PostFacadeImpl.java"),
                StandardCharsets.UTF_8);
        String repository = Files.readString(Path.of("src/main/java/com/offerlab/community/post/infrastructure/persistence/PostRepositoryImpl.java"),
                StandardCharsets.UTF_8);

        assertTrue(facade.contains("MAX_BATCH_LOOKUP_IDS = 500"),
                "post facade batch lookups must keep a hard upper bound to avoid oversized Redis/DB batches");
        assertTrue(facade.contains("normalizeBatchIds(postIds, MAX_BATCH_LOOKUP_IDS)"),
                "post facade must normalize caller-provided ids before batch detail/counter lookups");
        assertTrue(facade.contains("postRepo.batchFindByIds(normalizedIds)"),
                "batch post detail lookup must pass normalized ids into repository");
        assertTrue(facade.contains("postCounterRedis.batchGet(normalizedIds)"),
                "batch counter lookup must pass normalized ids into Redis");
        assertTrue(facade.contains(".filter(Objects::nonNull)"),
                "batch id normalization must reject null ids");
        assertTrue(facade.contains(".filter(id -> id > 0)"),
                "batch id normalization must reject non-positive ids");
        assertTrue(facade.contains(".distinct()"),
                "batch id normalization must deduplicate ids before IO");
        assertTrue(facade.contains(".limit(maxSize)"),
                "batch id normalization must cap caller-controlled collections");

        assertTrue(repository.contains("MAX_BATCH_FIND_IDS = 500"),
                "repository batch lookup must also cap ids when called without the facade");
        assertTrue(repository.contains("postMapper.selectBatchIds(normalizedIds)"),
                "repository must not pass raw caller ids into selectBatchIds");
        assertTrue(repository.contains("extMapper.selectBatchIds(normalizedIds)"),
                "repository extension lookup must use the same bounded ids");
    }
}

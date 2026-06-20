package com.offerlab.community.user.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserBatchLookupGuardTest {

    @Test
    void userBatchLookupsMustNormalizeAndCapInputUids() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/offerlab/community/user/application/UserFacadeImpl.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("MAX_BATCH_BRIEF_UIDS = 500"),
                "batch user brief lookups must keep a hard upper bound");
        assertTrue(source.contains("MAX_BATCH_FOLLOW_CHECK_UIDS = 500"),
                "batch follow-state checks must keep a hard upper bound");
        assertTrue(source.contains("normalizeBatchUids(uids, MAX_BATCH_BRIEF_UIDS)"),
                "batch user brief lookups must normalize caller-provided uids");
        assertTrue(source.contains("normalizeBatchUids(toUids, MAX_BATCH_FOLLOW_CHECK_UIDS)"),
                "batch follow-state checks must normalize caller-provided uids");
        assertTrue(source.contains(".filter(Objects::nonNull)"),
                "batch uid normalization must reject null uids");
        assertTrue(source.contains(".filter(uid -> uid > 0)"),
                "batch uid normalization must reject non-positive uids");
        assertTrue(source.contains(".distinct()"),
                "batch uid normalization must deduplicate before IO");
        assertTrue(source.contains(".limit(maxSize)"),
                "batch uid normalization must cap caller-controlled collections");
    }
}

package com.offerlab.community.feed.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedInboxStableScoreTest {

    @Test
    void stableScoreUsesPostSequenceToBreakSameMillisecondTies() {
        long timestamp = 1_789_000_000_123L;

        double first = FeedInboxRedis.stableScore(timestamp, 1001L);
        double second = FeedInboxRedis.stableScore(timestamp, 1002L);

        assertNotEquals(first, second);
        assertTrue(second > first);
        assertEquals(timestamp, FeedInboxRedis.scoreTimestamp(first));
        assertEquals(timestamp, FeedInboxRedis.scoreTimestamp(second));
    }

    @Test
    void legacyTimestampScoresRemainReadable() {
        long timestamp = 1_789_000_000_123L;

        assertEquals(timestamp, FeedInboxRedis.scoreTimestamp(timestamp));
    }
}

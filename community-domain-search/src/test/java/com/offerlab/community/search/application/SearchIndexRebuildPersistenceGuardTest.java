package com.offerlab.community.search.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchIndexRebuildPersistenceGuardTest {

    @Test
    void migrationMirrorsMustDefinePersistentSingleActiveRebuild() throws Exception {
        String canonical = read("../db/migration/20260720_search_index_rebuild_task.sql");
        String flyway = read("../community-bootstrap/src/main/resources/db/flyway/core/"
                + "V20260720.01__search_index_rebuild_task.sql");
        String init = read("../db/init/25_search_index_rebuild_task.sql");

        assertEquals(canonical, flyway);
        assertEquals(canonical, init);
        assertTrue(canonical.contains("CREATE TABLE IF NOT EXISTS t_search_index_rebuild_task"));
        assertTrue(canonical.contains("task_status IN ('PENDING', 'RUNNING')"));
        assertTrue(canonical.contains("UNIQUE KEY uk_search_index_rebuild_active (active_key)"));
        for (String column : new String[]{
                "checkpoint_id", "indexed_count", "failed_count", "total_count",
                "last_error", "lock_owner", "lock_until", "heartbeat_time", "started_at", "finished_at"
        }) {
            assertTrue(canonical.contains(column), "rebuild migration must persist " + column);
        }
    }

    @Test
    void expiredRunningTaskMustFailAndNeverBeReclaimedAsCheckpointResume() throws Exception {
        String mapper = read("src/main/java/com/offerlab/community/search/infrastructure/persistence/mapper/"
                + "SearchIndexRebuildTaskMapper.java");

        assertTrue(mapper.contains("task_status='FAILED'"));
        assertTrue(mapper.contains("last_error='worker lease expired'"));
        assertTrue(mapper.contains("WHERE task_status='RUNNING' AND lock_until <= NOW(3)"));
        assertTrue(mapper.contains("WHERE task_id=#{taskId} AND task_status='PENDING'"));
        assertFalse(mapper.contains("task_status='RUNNING' AND lock_until <= NOW(3)\n            ORDER BY"),
                "expired RUNNING tasks must not be reclaimed for checkpoint resume");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

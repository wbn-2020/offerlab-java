package com.offerlab.community.search.application;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchIndexTaskServiceLifecycleTest {

    @Test
    void ownedExecutorIsIsolatedAndClosedWithService() throws Exception {
        SearchIndexTaskService service = new SearchIndexTaskService(null);
        Field activeField = SearchIndexTaskService.class.getDeclaredField("rebuildExecutor");
        activeField.setAccessible(true);
        Field ownedField = SearchIndexTaskService.class.getDeclaredField("ownedRebuildExecutor");
        ownedField.setAccessible(true);
        ExecutorService owned = (ExecutorService) ownedField.get(service);

        assertNotSame(ForkJoinPool.commonPool(), activeField.get(service));

        service.destroy();

        assertTrue(owned.isShutdown());
    }
}

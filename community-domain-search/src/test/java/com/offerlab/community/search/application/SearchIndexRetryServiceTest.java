package com.offerlab.community.search.application;

import com.offerlab.community.search.infrastructure.persistence.mapper.SearchIndexRebuildTaskMapper;
import com.offerlab.community.search.infrastructure.persistence.mapper.SearchIndexRetryTaskMapper;
import com.offerlab.community.search.infrastructure.persistence.po.SearchIndexRetryTaskPO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SearchIndexRetryServiceTest {

    @Test
    void statusSummarizesBucketsWhenTableIsAvailable() {
        SearchIndexRetryService service = new SearchIndexRetryService(availableMapper(List.of(
                Map.of("status", SearchIndexRetryTaskMapper.STATUS_PENDING, "count", 2L),
                Map.of("status", SearchIndexRetryTaskMapper.STATUS_FAILED, "count", 3L)
        ), 4L), null, null);

        Map<String, Object> status = service.status();

        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) status.get("byStatus");
        assertEquals("DEGRADED", status.get("status"));
        assertEquals(true, status.get("available"));
        assertEquals(true, status.get("attentionRequired"));
        assertEquals("Search index retry queue has failed or due tasks", status.get("message"));
        @SuppressWarnings("unchecked")
        Map<String, Object> diagnostics = (Map<String, Object>) status.get("diagnostics");
        @SuppressWarnings("unchecked")
        Map<String, Object> failedSample = (Map<String, Object>) diagnostics.get("failedSample");
        assertEquals(99L, failedSample.get("id"));
        assertEquals("INDEX", failedSample.get("operation"));
        assertEquals("es down", diagnostics.get("latestError"));
        assertEquals("Open /api/v1/ops/search-index-retry-tasks?status=2, confirm Elasticsearch readiness, then replay failed test records first.",
                diagnostics.get("recommendedAction"));
        assertEquals(2L, byStatus.get("pending"));
        assertEquals(3L, byStatus.get("failed"));
        assertEquals(4L, status.get("duePending"));
    }

    @Test
    void statusKeepsUpAndZeroBucketsWhenTableIsAvailableButEmpty() {
        SearchIndexRetryService service = new SearchIndexRetryService(availableMapper(List.of(), 0L), null, null);

        Map<String, Object> status = service.status();

        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) status.get("byStatus");
        assertEquals("UP", status.get("status"));
        assertEquals(true, status.get("available"));
        assertEquals(false, status.get("attentionRequired"));
        assertEquals(0L, byStatus.get("pending"));
        assertEquals(0L, byStatus.get("done"));
        assertEquals(0L, byStatus.get("failed"));
        assertEquals(0L, byStatus.get("running"));
        assertEquals(0L, status.get("duePending"));
    }

    @Test
    void statusReportsDownWhenTableIsUnavailable() {
        SearchIndexRetryService service = new SearchIndexRetryService(unavailableMapper(), null, null);

        Map<String, Object> status = service.status();

        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) status.get("byStatus");
        assertEquals("DOWN", status.get("status"));
        assertEquals(false, status.get("available"));
        assertEquals(true, status.get("attentionRequired"));
        assertEquals("search index retry table unavailable", status.get("message"));
        assertEquals(0L, byStatus.get("pending"));
        assertEquals(0L, byStatus.get("failed"));
        assertEquals(0L, status.get("duePending"));
    }

    @Test
    void retryClaimPausesWithoutConsumingAttemptsWhileRebuildIsActive() {
        AtomicBoolean claimCalled = new AtomicBoolean(false);
        SearchIndexRetryTaskMapper retryMapper = proxy(methodName -> switch (methodName) {
            case "tableExists" -> 1;
            case "claimDue" -> {
                claimCalled.set(true);
                yield 0;
            }
            default -> throw new UnsupportedOperationException(methodName);
        });
        SearchIndexRebuildTaskMapper rebuildMapper = rebuildProxy(methodName -> switch (methodName) {
            case "tableExists", "countActive" -> 1;
            case "failExpiredLease" -> 0;
            default -> throw new UnsupportedOperationException(methodName);
        });
        SearchIndexRetryService service = new SearchIndexRetryService(retryMapper, null, null, rebuildMapper);

        service.retryDueTasks();

        assertFalse(claimCalled.get());
    }

    private static SearchIndexRetryTaskMapper availableMapper(List<Map<String, Object>> rows, long duePending) {
        return proxy((methodName) -> switch (methodName) {
            case "tableExists" -> 1;
            case "countByStatus" -> rows;
            case "countDuePending" -> duePending;
            case "listRecent" -> List.of(failedTask());
            default -> throw new UnsupportedOperationException(methodName);
        });
    }

    private static SearchIndexRetryTaskPO failedTask() {
        SearchIndexRetryTaskPO task = new SearchIndexRetryTaskPO();
        task.setId(99L);
        task.setOperation("INDEX");
        task.setPostId(88L);
        task.setRetryCount(5);
        task.setNextRetryTime(LocalDateTime.parse("2026-06-08T23:00:00"));
        task.setLastError("es down");
        return task;
    }

    private static SearchIndexRetryTaskMapper unavailableMapper() {
        return proxy((methodName) -> {
            if ("tableExists".equals(methodName)) {
                throw new IllegalStateException("down");
            }
            throw new UnsupportedOperationException(methodName);
        });
    }

    private static SearchIndexRetryTaskMapper proxy(MethodResult result) {
        return (SearchIndexRetryTaskMapper) Proxy.newProxyInstance(
                SearchIndexRetryTaskMapper.class.getClassLoader(),
                new Class<?>[]{SearchIndexRetryTaskMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(thisProxyName(), args);
                    }
                    return result.invoke(method.getName());
                }
        );
    }

    private static SearchIndexRebuildTaskMapper rebuildProxy(MethodResult result) {
        return (SearchIndexRebuildTaskMapper) Proxy.newProxyInstance(
                SearchIndexRebuildTaskMapper.class.getClassLoader(),
                new Class<?>[]{SearchIndexRebuildTaskMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(thisProxyName(), args);
                    }
                    return result.invoke(method.getName());
                }
        );
    }

    private static Object thisProxyName() {
        return "SearchIndexRetryTaskMapperTestProxy";
    }

    private interface MethodResult {
        Object invoke(String methodName);
    }
}

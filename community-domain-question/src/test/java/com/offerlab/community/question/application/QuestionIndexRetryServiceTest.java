package com.offerlab.community.question.application;

import com.offerlab.community.question.infrastructure.persistence.mapper.QuestionIndexRetryTaskMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionIndexRetryServiceTest {

    @Test
    void statusSummarizesBucketsWhenTableIsAvailable() {
        QuestionIndexRetryService service = new QuestionIndexRetryService(availableMapper(List.of(
                Map.of("status", QuestionIndexRetryTaskMapper.STATUS_PENDING, "count", 2L),
                Map.of("status", QuestionIndexRetryTaskMapper.STATUS_FAILED, "count", 3L)
        ), 4L), null, null);

        Map<String, Object> status = service.status();

        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) status.get("byStatus");
        assertEquals("DEGRADED", status.get("status"));
        assertEquals(true, status.get("available"));
        assertEquals(true, status.get("attentionRequired"));
        assertEquals("Question index retry queue has failed or due tasks", status.get("message"));
        assertEquals(2L, byStatus.get("pending"));
        assertEquals(3L, byStatus.get("failed"));
        assertEquals(4L, status.get("duePending"));
    }

    @Test
    void statusKeepsUpAndZeroBucketsWhenTableIsAvailableButEmpty() {
        QuestionIndexRetryService service = new QuestionIndexRetryService(availableMapper(List.of(), 0L), null, null);

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
        QuestionIndexRetryService service = new QuestionIndexRetryService(unavailableMapper(), null, null);

        Map<String, Object> status = service.status();

        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) status.get("byStatus");
        assertEquals("DOWN", status.get("status"));
        assertEquals(false, status.get("available"));
        assertEquals(true, status.get("attentionRequired"));
        assertEquals("question index retry table unavailable", status.get("message"));
        assertEquals(0L, byStatus.get("pending"));
        assertEquals(0L, byStatus.get("failed"));
        assertEquals(0L, status.get("duePending"));
    }

    private static QuestionIndexRetryTaskMapper availableMapper(List<Map<String, Object>> rows, long duePending) {
        return proxy((methodName) -> switch (methodName) {
            case "tableExists" -> 1;
            case "countByStatus" -> rows;
            case "countDuePending" -> duePending;
            default -> throw new UnsupportedOperationException(methodName);
        });
    }

    private static QuestionIndexRetryTaskMapper unavailableMapper() {
        return proxy((methodName) -> {
            if ("tableExists".equals(methodName)) {
                throw new IllegalStateException("down");
            }
            throw new UnsupportedOperationException(methodName);
        });
    }

    private static QuestionIndexRetryTaskMapper proxy(MethodResult result) {
        return (QuestionIndexRetryTaskMapper) Proxy.newProxyInstance(
                QuestionIndexRetryTaskMapper.class.getClassLoader(),
                new Class<?>[]{QuestionIndexRetryTaskMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(thisProxyName(), args);
                    }
                    return result.invoke(method.getName());
                }
        );
    }

    private static Object thisProxyName() {
        return "QuestionIndexRetryTaskMapperTestProxy";
    }

    private interface MethodResult {
        Object invoke(String methodName);
    }
}

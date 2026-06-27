package com.offerlab.community.question.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.question.infrastructure.persistence.mapper.QuestionIndexTaskMapper;
import com.offerlab.community.question.infrastructure.persistence.po.QuestionIndexTaskPO;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Comparator;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionIndexTaskServiceGateTest {
    private static final String TYPE_REBUILD = "QUESTION_INDEX_REBUILD";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_FAILED = "FAILED";

    @Test
    void defaultExecutorMustNotUseForkJoinCommonPool() throws Exception {
        QuestionIndexTaskService service = new QuestionIndexTaskService(indexer(), mapper(new MapperState()));
        Field field = QuestionIndexTaskService.class.getDeclaredField("rebuildExecutor");
        field.setAccessible(true);

        Object executor = field.get(service);

        assertNotSame(ForkJoinPool.commonPool(), executor);
    }

    @Test
    void submitRebuildTaskReturnsRemoteActiveTaskWhenRedisGateIsClaimedElsewhere() {
        MapperState state = new MapperState();
        GateRedisTemplate redis = new GateRedisTemplate(false, "remote-task-1");
        QuestionIndexTaskService service = new QuestionIndexTaskService(indexer(), mapper(state), redis);
        service.setRebuildExecutorForTest(command -> {
        });

        QuestionIndexTaskService.QuestionIndexTask task = service.submitRebuildTask(7L);

        assertEquals("remote-task-1", task.getTaskId());
        assertEquals(TYPE_REBUILD, task.getType());
        assertEquals(STATUS_RUNNING, task.getStatus());
        assertTrue(state.tasks.isEmpty());
        assertEquals(0, redis.releaseCalls);
    }

    @Test
    void submitRebuildTaskFailsClosedWhenRedisGateIsUnavailable() {
        MapperState state = new MapperState();
        GateRedisTemplate redis = new GateRedisTemplate(true, null);
        QuestionIndexTaskService service = new QuestionIndexTaskService(indexer(), mapper(state), redis);
        service.setRebuildExecutorForTest(command -> {
        });

        BizException error = assertThrows(BizException.class, () -> service.submitRebuildTask(7L));

        assertEquals(ErrorCode.CACHE_ERROR.getCode(), error.getCode());
        assertTrue(state.tasks.isEmpty());
        assertEquals(0, redis.releaseCalls);
    }

    @Test
    void submitRebuildTaskReleasesGateAndMarksTaskFailedWhenExecutorRejects() {
        MapperState state = new MapperState();
        GateRedisTemplate redis = new GateRedisTemplate(false, null);
        QuestionIndexTaskService service = new QuestionIndexTaskService(indexer(), mapper(state), redis);
        service.setRebuildExecutorForTest(command -> {
            throw new RejectedExecutionException("executor closed");
        });

        BizException error = assertThrows(BizException.class, () -> service.submitRebuildTask(7L));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), error.getCode());
        assertEquals(1, state.tasks.size());
        QuestionIndexTaskPO task = state.tasks.values().iterator().next();
        assertEquals(STATUS_FAILED, task.getTaskStatus());
        assertNotNull(task.getMessage());
        assertTrue(task.getMessage().contains("scheduled"));
        assertEquals(1, redis.releaseCalls);
        assertFalse(redis.hasActiveClaim());
    }

    private static QuestionSearchIndexer indexer() {
        ApplicationEventPublisher publisher = event -> {
        };
        return new QuestionSearchIndexer(null, null, null, publisher);
    }

    @SuppressWarnings("unchecked")
    private static QuestionIndexTaskMapper mapper(MapperState state) {
        return (QuestionIndexTaskMapper) Proxy.newProxyInstance(
                QuestionIndexTaskMapper.class.getClassLoader(),
                new Class[]{QuestionIndexTaskMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> state.tableExists;
                    case "listRecent" -> state.tasks.values().stream()
                            .sorted(Comparator.comparing(QuestionIndexTaskPO::getCreateTime,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                            .limit(args == null || args.length == 0 ? state.tasks.size() : ((Number) args[0]).intValue())
                            .toList();
                    case "findActiveRebuildTask" -> state.tasks.values().stream()
                            .filter(task -> TYPE_REBUILD.equals(task.getTaskType()))
                            .filter(task -> STATUS_PENDING.equals(task.getTaskStatus()) || STATUS_RUNNING.equals(task.getTaskStatus()))
                            .sorted(Comparator.comparing(QuestionIndexTaskPO::getCreateTime,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                            .findFirst()
                            .orElse(null);
                    case "insertTask" -> {
                        QuestionIndexTaskPO task = copy((QuestionIndexTaskPO) args[0]);
                        state.tasks.put(task.getTaskId(), task);
                        yield 1;
                    }
                    case "findByTaskId" -> state.tasks.get(String.valueOf(args[0]));
                    case "finish" -> {
                        QuestionIndexTaskPO task = state.tasks.get(String.valueOf(args[0]));
                        if (task == null) {
                            yield 0;
                        }
                        task.setTaskStatus((String) args[1]);
                        task.setAccepted((Integer) args[2]);
                        task.setIndexed((Integer) args[3]);
                        task.setFailed((Integer) args[4]);
                        task.setTotal((Integer) args[5]);
                        task.setIndexName((String) args[6]);
                        task.setMessage((String) args[7]);
                        yield 1;
                    }
                    case "markRetry" -> {
                        QuestionIndexTaskPO task = state.tasks.get(String.valueOf(args[0]));
                        if (task == null || !STATUS_FAILED.equals(task.getTaskStatus())) {
                            yield 0;
                        }
                        task.setTaskStatus(STATUS_PENDING);
                        task.setAccepted(0);
                        task.setIndexed(0);
                        task.setFailed(0);
                        task.setTotal(0);
                        task.setMessage(null);
                        yield 1;
                    }
                    case "markRunning" -> {
                        QuestionIndexTaskPO task = state.tasks.get(String.valueOf(args[0]));
                        if (task == null || !(STATUS_PENDING.equals(task.getTaskStatus()) || STATUS_FAILED.equals(task.getTaskStatus()))) {
                            yield 0;
                        }
                        task.setTaskStatus(STATUS_RUNNING);
                        task.setMessage(null);
                        yield 1;
                    }
                    default -> throw new UnsupportedOperationException("Unexpected mapper method: " + method.getName());
                });
    }

    private static QuestionIndexTaskPO copy(QuestionIndexTaskPO source) {
        QuestionIndexTaskPO task = new QuestionIndexTaskPO();
        task.setTaskId(source.getTaskId());
        task.setTaskType(source.getTaskType());
        task.setTaskStatus(source.getTaskStatus());
        task.setOperatorUid(source.getOperatorUid());
        task.setAccepted(source.getAccepted());
        task.setIndexed(source.getIndexed());
        task.setFailed(source.getFailed());
        task.setTotal(source.getTotal());
        task.setIndexName(source.getIndexName());
        task.setMessage(source.getMessage());
        task.setCreateTime(source.getCreateTime());
        task.setUpdateTime(source.getUpdateTime());
        return task;
    }

    private static final class MapperState {
        private final Map<String, QuestionIndexTaskPO> tasks = new LinkedHashMap<>();
        private int tableExists = 1;
    }

    private static final class GateRedisTemplate extends StringRedisTemplate {
        private final boolean failClaim;
        private final ValueOperations<String, String> valueOps;
        private String activeClaimTaskId;
        private String currentTaskId;
        private int releaseCalls;

        private GateRedisTemplate(boolean failClaim, String currentTaskId) {
            this.failClaim = failClaim;
            this.currentTaskId = currentTaskId;
            this.valueOps = valueOps();
        }

        @Override
        public ValueOperations<String, String> opsForValue() {
            return valueOps;
        }

        @Override
        public <T> T execute(RedisCallback<T> action) {
            releaseCalls++;
            if (activeClaimTaskId != null && activeClaimTaskId.equals(currentTaskId)) {
                currentTaskId = null;
                activeClaimTaskId = null;
            }
            return null;
        }

        private boolean hasActiveClaim() {
            return currentTaskId != null;
        }

        @SuppressWarnings("unchecked")
        private ValueOperations<String, String> valueOps() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class[]{ValueOperations.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setIfAbsent" -> {
                            if (failClaim) {
                                throw new IllegalStateException("redis down");
                            }
                            if (currentTaskId == null) {
                                currentTaskId = String.valueOf(args[1]);
                                activeClaimTaskId = currentTaskId;
                                yield true;
                            }
                            yield false;
                        }
                        case "get" -> currentTaskId;
                        default -> throw new UnsupportedOperationException("Unexpected redis op: " + method.getName());
                    });
        }
    }
}

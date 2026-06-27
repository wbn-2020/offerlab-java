package com.offerlab.community.search.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.review.ReviewQueueItemCommand;
import com.offerlab.community.search.api.dto.ReviewQueueCreateCmd;
import com.offerlab.community.search.infrastructure.persistence.mapper.ReviewQueueMapper;
import com.offerlab.community.search.infrastructure.persistence.po.ReviewQueueItemPO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReviewQueueServiceTest {

    @Test
    void createFailsClosedWhenReadinessIsBlockedEvenIfTableExists() {
        ReviewQueueMapperState mapperState = new ReviewQueueMapperState(1);
        ReviewQueueService service = newService(mapperState, new MigrationCheckStub(false));

        BizException ex = assertThrows(BizException.class, () -> service.create(createCmd(), 7L));

        assertEquals(ErrorCode.DATABASE_ERROR.getCode(), ex.getCode());
        assertEquals(0, mapperState.upsertCalls);
        assertEquals(0, mapperState.itemsById.size());
    }

    @Test
    void sourceUpsertSkipsWriteWhenReadinessIsBlocked() {
        ReviewQueueMapperState mapperState = new ReviewQueueMapperState(1);
        ReviewQueueService service = newService(mapperState, new MigrationCheckStub(false));

        service.upsert(new ReviewQueueItemCommand("POST_REPORT", 9001L, "title", "summary", "high", 7L, 10, "{}", "note"));

        assertEquals(0, mapperState.upsertCalls);
        assertEquals(0, mapperState.itemsById.size());
    }

    private static ReviewQueueService newService(ReviewQueueMapperState mapperState,
                                                 MigrationCheckService migrationCheckService) {
        return new ReviewQueueService(
                mapper(mapperState),
                new SnowflakeIdGenerator(),
                new AdminAuditStub(),
                migrationCheckService
        );
    }

    private static ReviewQueueMapper mapper(ReviewQueueMapperState state) {
        return (ReviewQueueMapper) Proxy.newProxyInstance(
                ReviewQueueMapper.class.getClassLoader(),
                new Class<?>[]{ReviewQueueMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> state.tableExists;
                    case "upsertItem" -> {
                        state.upsertCalls++;
                        ReviewQueueItemPO item = (ReviewQueueItemPO) args[0];
                        state.itemsById.put(item.getId(), clone(item));
                        yield 1;
                    }
                    case "findById" -> state.itemsById.get((Long) args[0]);
                    case "findBySource" -> state.findBySource((String) args[0], (Long) args[1]);
                    case "list" -> List.of();
                    case "countByStatus" -> List.of();
                    case "claim", "release", "resolve", "resolveBySource" -> 0;
                    case "toString" -> "ReviewQueueMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static ReviewQueueCreateCmd createCmd() {
        ReviewQueueCreateCmd cmd = new ReviewQueueCreateCmd();
        cmd.setSourceType("POST_REPORT");
        cmd.setSourceId(9001L);
        cmd.setTitle("queue title");
        cmd.setSummary("summary");
        cmd.setRiskLevel("high");
        cmd.setPriority(10);
        cmd.setExtJson("{}");
        cmd.setNote("manual create");
        return cmd;
    }

    private static ReviewQueueItemPO clone(ReviewQueueItemPO source) {
        ReviewQueueItemPO copy = new ReviewQueueItemPO();
        copy.setId(source.getId());
        copy.setSourceType(source.getSourceType());
        copy.setSourceId(source.getSourceId());
        copy.setTitle(source.getTitle());
        copy.setSummary(source.getSummary());
        copy.setRiskLevel(source.getRiskLevel());
        copy.setQueueStatus(source.getQueueStatus());
        copy.setCreatorUid(source.getCreatorUid());
        copy.setPriority(source.getPriority());
        copy.setExtJson(source.getExtJson());
        return copy;
    }

    private static final class ReviewQueueMapperState {
        private final int tableExists;
        private final Map<Long, ReviewQueueItemPO> itemsById = new LinkedHashMap<>();
        private int upsertCalls;

        private ReviewQueueMapperState(int tableExists) {
            this.tableExists = tableExists;
        }

        private ReviewQueueItemPO findBySource(String sourceType, Long sourceId) {
            return itemsById.values().stream()
                    .filter(item -> Objects.equals(item.getSourceType(), sourceType))
                    .filter(item -> Objects.equals(item.getSourceId(), sourceId))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static final class MigrationCheckStub extends MigrationCheckService {
        private final boolean reviewQueueReady;

        private MigrationCheckStub(boolean reviewQueueReady) {
            super(null);
            this.reviewQueueReady = reviewQueueReady;
        }

        @Override
        public boolean reviewQueueReady() {
            return reviewQueueReady;
        }
    }

    private static final class AdminAuditStub extends AdminAuditService {
        private AdminAuditStub() {
            super(null, null, null);
        }

        @Override
        public void recordRequired(Long operatorUid, String action, String resourceType, Object resourceId,
                                   Object before, Object after, String remark) {
        }
    }
}

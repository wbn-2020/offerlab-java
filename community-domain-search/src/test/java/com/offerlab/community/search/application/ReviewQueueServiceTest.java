package com.offerlab.community.search.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.review.ReviewQueueItemCommand;
import com.offerlab.community.infra.review.ReviewQueueSourceActionHandler;
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

    @Test
    void approveResolvesReportQueueItems() {
        ReviewQueueMapperState mapperState = new ReviewQueueMapperState(1);
        ReviewQueueItemPO item = queueItem(101L, "POST_REPORT", 9001L);
        mapperState.itemsById.put(item.getId(), item);
        ReviewQueueService service = newService(mapperState, new MigrationCheckStub(true));

        ReviewQueueItemPO approved = service.approve(101L, 77L, "confirmed violation", "CONFIRM");

        assertEquals(ReviewQueueMapper.STATUS_APPROVED, approved.getQueueStatus());
        assertEquals("approved", approved.getHandleResult());
        assertEquals("confirmed violation", approved.getHandleNote());
        assertEquals(77L, approved.getAssigneeUid());
    }

    @Test
    void approveDispatchesSourceActionForReportQueueItems() {
        ReviewQueueMapperState mapperState = new ReviewQueueMapperState(1);
        ReviewQueueItemPO item = queueItem(102L, "POST_REPORT", 9002L);
        mapperState.itemsById.put(item.getId(), item);
        RecordingSourceActionHandler handler = new RecordingSourceActionHandler("POST_REPORT");
        ReviewQueueService service = newService(mapperState, new MigrationCheckStub(true), List.of(handler));

        service.approve(102L, 77L, "confirmed violation", "CONFIRM");

        assertEquals(1, handler.calls);
        assertEquals("POST_REPORT", handler.sourceType);
        assertEquals(9002L, handler.sourceId);
        assertEquals(ReviewQueueMapper.STATUS_APPROVED, handler.status);
        assertEquals("approved", handler.result);
        assertEquals("confirmed violation", handler.note);
        assertEquals(77L, handler.operatorUid);
    }

    private static ReviewQueueService newService(ReviewQueueMapperState mapperState,
                                                 MigrationCheckService migrationCheckService) {
        return newService(mapperState, migrationCheckService, List.of());
    }

    private static ReviewQueueService newService(ReviewQueueMapperState mapperState,
                                                 MigrationCheckService migrationCheckService,
                                                 List<ReviewQueueSourceActionHandler> sourceActionHandlers) {
        return new ReviewQueueService(
                mapper(mapperState),
                new SnowflakeIdGenerator(),
                new AdminAuditStub(),
                migrationCheckService,
                sourceActionHandlers
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
                    case "claim", "release" -> 0;
                    case "resolve" -> state.resolve((Long) args[0], (String) args[1], (String) args[2],
                            (String) args[3], (Long) args[4]);
                    case "resolveBySource" -> 0;
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

    private static ReviewQueueItemPO queueItem(Long id, String sourceType, Long sourceId) {
        ReviewQueueItemPO item = new ReviewQueueItemPO();
        item.setId(id);
        item.setSourceType(sourceType);
        item.setSourceId(sourceId);
        item.setTitle("queue title");
        item.setSummary("summary");
        item.setRiskLevel("high");
        item.setQueueStatus(ReviewQueueMapper.STATUS_PENDING);
        item.setCreatorUid(7L);
        item.setPriority(80);
        item.setExtJson("{}");
        return item;
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
        copy.setHandleResult(source.getHandleResult());
        copy.setHandleNote(source.getHandleNote());
        copy.setAssigneeUid(source.getAssigneeUid());
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

        private int resolve(Long id, String status, String result, String note, Long operatorUid) {
            ReviewQueueItemPO item = itemsById.get(id);
            if (item == null || !List.of(ReviewQueueMapper.STATUS_PENDING, ReviewQueueMapper.STATUS_CLAIMED)
                    .contains(item.getQueueStatus())) {
                return 0;
            }
            item.setQueueStatus(status);
            item.setHandleResult(result);
            item.setHandleNote(note);
            item.setAssigneeUid(operatorUid);
            return 1;
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

    private static final class RecordingSourceActionHandler implements ReviewQueueSourceActionHandler {
        private final String supportedSourceType;
        private int calls;
        private String sourceType;
        private Long sourceId;
        private String status;
        private String result;
        private String note;
        private Long operatorUid;

        private RecordingSourceActionHandler(String supportedSourceType) {
            this.supportedSourceType = supportedSourceType;
        }

        @Override
        public boolean supports(String sourceType) {
            return Objects.equals(supportedSourceType, sourceType);
        }

        @Override
        public void handle(String sourceType, Long sourceId, String status, String result, String note, Long operatorUid) {
            calls++;
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.status = status;
            this.result = result;
            this.note = note;
            this.operatorUid = operatorUid;
        }
    }

}

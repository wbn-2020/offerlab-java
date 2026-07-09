package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.infrastructure.persistence.mapper.DomainModeratorMapper;
import com.offerlab.community.post.infrastructure.persistence.po.DomainModeratorPO;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainModeratorServiceTest {

    @Test
    void upsertFailsClosedWhenReadinessIsBlockedEvenIfTableExists() {
        DomainModeratorMapperState mapperState = new DomainModeratorMapperState(1);
        DomainModeratorService service = newService(mapperState, new MigrationCheckStub(false));

        BizException ex = assertThrows(BizException.class,
                () -> service.upsertModerator(9L, 1, 7L, "assign owner"));

        assertEquals(ErrorCode.DEPENDENCY_ERROR.getCode(), ex.getCode());
        assertEquals(0, mapperState.upsertCalls);
        assertTrue(mapperState.rows.isEmpty());
    }

    @Test
    void updateFailsClosedWhenReadinessIsBlockedEvenIfTableExists() {
        DomainModeratorMapperState mapperState = new DomainModeratorMapperState(1);
        DomainModeratorService service = newService(mapperState, new MigrationCheckStub(false));

        BizException ex = assertThrows(BizException.class,
                () -> service.updateModeratorStatus(9L, 1, Boolean.FALSE, 7L, "disable owner"));

        assertEquals(ErrorCode.DEPENDENCY_ERROR.getCode(), ex.getCode());
        assertEquals(0, mapperState.updateCalls);
    }

    private static DomainModeratorService newService(DomainModeratorMapperState mapperState,
                                                     MigrationCheckService migrationCheckService) {
        return new DomainModeratorService(
                mapper(mapperState),
                new AdminPermissionStub(),
                new AdminAuditStub(),
                new SnowflakeIdGenerator(),
                migrationCheckService
        );
    }

    private static DomainModeratorMapper mapper(DomainModeratorMapperState state) {
        return (DomainModeratorMapper) Proxy.newProxyInstance(
                DomainModeratorMapper.class.getClassLoader(),
                new Class<?>[]{DomainModeratorMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> state.tableExists;
                    case "selectByDomain" -> state.selectByDomain((Integer) args[0], (Integer) args[1], (Integer) args[2]);
                    case "selectEnabledByUid" -> state.selectEnabledByUid((Long) args[0]);
                    case "countActive" -> state.countActive((Long) args[0], (Integer) args[1]);
                    case "upsertModerator" -> {
                        state.upsertCalls++;
                        yield 1;
                    }
                    case "updateModeratorStatus" -> {
                        state.updateCalls++;
                        yield 1;
                    }
                    case "toString" -> "DomainModeratorMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static final class DomainModeratorMapperState {
        private final int tableExists;
        private final Map<String, DomainModeratorPO> rows = new LinkedHashMap<>();
        private int upsertCalls;
        private int updateCalls;

        private DomainModeratorMapperState(int tableExists) {
            this.tableExists = tableExists;
        }

        private List<DomainModeratorPO> selectByDomain(Integer domain, Integer enabled, Integer limit) {
            return rows.values().stream()
                    .filter(item -> domain == null || Objects.equals(item.getDomain(), domain))
                    .filter(item -> enabled == null || Objects.equals(item.getEnabled(), enabled))
                    .limit(limit == null ? 50 : limit)
                    .toList();
        }

        private List<DomainModeratorPO> selectEnabledByUid(Long uid) {
            return rows.values().stream()
                    .filter(item -> Objects.equals(item.getUid(), uid))
                    .filter(item -> Objects.equals(item.getEnabled(), 1))
                    .toList();
        }

        private int countActive(Long uid, Integer domain) {
            return (int) rows.values().stream()
                    .filter(item -> Objects.equals(item.getUid(), uid))
                    .filter(item -> Objects.equals(item.getDomain(), domain))
                    .filter(item -> Objects.equals(item.getEnabled(), 1))
                    .count();
        }
    }

    private static final class MigrationCheckStub extends MigrationCheckService {
        private final boolean domainModeratorReady;

        private MigrationCheckStub(boolean domainModeratorReady) {
            super(null);
            this.domainModeratorReady = domainModeratorReady;
        }

        @Override
        public boolean domainModeratorReady() {
            return domainModeratorReady;
        }
    }

    private static final class AdminPermissionStub extends AdminPermissionService {
        private final List<Long> requiredAdmins = new ArrayList<>();

        private AdminPermissionStub() {
            super("", false, "test-local-open-token", null, new StandardEnvironment());
        }

        @Override
        public void requireAdmin(Long uid) {
            requiredAdmins.add(uid);
        }

        @Override
        public boolean isAdmin(Long uid) {
            return true;
        }

        @Override
        public boolean hasRole(Long uid, String roleCode) {
            return false;
        }

        @Override
        public boolean isLocalOpenMode() {
            return false;
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

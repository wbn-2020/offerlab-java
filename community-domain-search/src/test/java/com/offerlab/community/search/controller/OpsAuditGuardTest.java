package com.offerlab.community.search.controller;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAuditGuardTest {

    @Test
    void auditLogsMustSupportFilteringPaginationAndCreateTime() throws Exception {
        String controllerSource = Files.readString(Path.of("src/main/java/com/offerlab/community/search/controller/OpsController.java"), StandardCharsets.UTF_8);
        String serviceSource = Files.readString(Path.of("../community-infrastructure/src/main/java/com/offerlab/community/infra/audit/AdminAuditService.java"), StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of("../community-infrastructure/src/main/java/com/offerlab/community/infra/audit/AdminAuditLogMapper.java"), StandardCharsets.UTF_8);
        String logSource = Files.readString(Path.of("../community-infrastructure/src/main/java/com/offerlab/community/infra/audit/AdminAuditLog.java"), StandardCharsets.UTF_8);

        assertTrue(controllerSource.contains("/audit-logs/page"), "ops controller must expose paged audit logs");
        assertTrue(controllerSource.contains("Long operatorUid"), "audit log API must accept operatorUid filter");
        assertTrue(controllerSource.contains("String startDate"), "audit log API must accept startDate filter");
        assertTrue(controllerSource.contains("String endDate"), "audit log API must accept endDate filter");
        assertTrue(controllerSource.contains("parseStartDate(startDate)"), "audit log API must parse start date safely");
        assertTrue(controllerSource.contains("parseEndDate(endDate)"), "audit log API must parse end date safely");
        assertTrue(serviceSource.contains("PageResult<AdminAuditLog> page"), "audit service must return paginated audit logs");
        assertTrue(serviceSource.contains("mapper.count"), "audit service must return total count");
        assertTrue(mapperSource.contains("operator_uid = #{operatorUid}"), "audit mapper must filter operator uid");
        assertTrue(mapperSource.contains("create_time &gt;= #{startTime}"), "audit mapper must filter start time");
        assertTrue(mapperSource.contains("create_time &lt;= #{endTime}"), "audit mapper must filter end time");
        assertTrue(mapperSource.contains("LIMIT #{offset}, #{limit}"), "audit mapper must page with offset and limit");
        assertTrue(mapperSource.contains("create_time AS createTime"), "audit mapper must expose create time");
        assertTrue(logSource.contains("LocalDateTime createTime"), "audit DTO must expose create time");
    }
}

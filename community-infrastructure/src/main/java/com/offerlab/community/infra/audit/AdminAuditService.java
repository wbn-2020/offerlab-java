package com.offerlab.community.infra.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditService {
    private final AdminAuditLogMapper mapper;
    private final SnowflakeIdGenerator idGen;
    private final ObjectMapper objectMapper;

    public void record(Long operatorUid, String action, String resourceType, Object resourceId,
                       Object before, Object after, String remark) {
        try {
            if (mapper.tableExists() <= 0) {
                return;
            }
            AdminAuditLog log = new AdminAuditLog();
            log.setId(idGen.nextId());
            log.setOperatorUid(operatorUid);
            log.setAction(limit(action, 64));
            log.setResourceType(limit(resourceType, 64));
            log.setResourceId(resourceId == null ? null : limit(String.valueOf(resourceId), 64));
            log.setBeforeJson(toJson(before));
            log.setAfterJson(toJson(after));
            log.setRemark(limit(remark, 1000));
            mapper.insert(log);
        } catch (Exception e) {
            log.warn("record admin audit failed: action={} resourceType={} resourceId={}",
                    action, resourceType, resourceId, e);
        }
    }

    public List<AdminAuditLog> listRecent(String action, String resourceType, int limit) {
        if (!tableReady()) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100));
        try {
            return mapper.listRecent(clean(action), clean(resourceType), safeLimit);
        } catch (RuntimeException e) {
            log.warn("admin audit list unavailable, returning empty audit view: {}", e.getMessage());
            return List.of();
        }
    }

    public PageResult<AdminAuditLog> page(String action, String resourceType, Long operatorUid,
                                          LocalDateTime startTime, LocalDateTime endTime,
                                          int page, int pageSize) {
        if (!tableReady()) {
            return PageResult.<AdminAuditLog>builder()
                    .items(List.of())
                    .total(0L)
                    .hasMore(false)
                    .nextCursor(null)
                    .build();
        }
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize <= 0 ? 20 : pageSize, 100));
        int offset = (safePage - 1) * safePageSize;
        String cleanAction = clean(action);
        String cleanResourceType = clean(resourceType);
        List<AdminAuditLog> items;
        long total;
        try {
            items = mapper.page(cleanAction, cleanResourceType, operatorUid, startTime, endTime, offset, safePageSize);
            total = mapper.count(cleanAction, cleanResourceType, operatorUid, startTime, endTime);
        } catch (RuntimeException e) {
            log.warn("admin audit page unavailable, returning empty audit view: {}", e.getMessage());
            return PageResult.<AdminAuditLog>builder()
                    .items(List.of())
                    .total(0L)
                    .hasMore(false)
                    .nextCursor(null)
                    .build();
        }
        boolean hasMore = offset + items.size() < total;
        return PageResult.<AdminAuditLog>builder()
                .items(items)
                .total(total)
                .hasMore(hasMore)
                .nextCursor(hasMore ? String.valueOf(safePage + 1) : null)
                .build();
    }

    private boolean tableReady() {
        try {
            return mapper.tableExists() > 0;
        } catch (RuntimeException e) {
            log.warn("admin audit table check failed, returning empty audit view: {}", e.getMessage());
            return false;
        }
    }

    private String toJson(Object value) throws Exception {
        if (value == null) {
            return null;
        }
        JsonNode node = objectMapper.valueToTree(value);
        redact(node);
        return objectMapper.writeValueAsString(node);
    }

    private void redact(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (sensitiveField(field.getKey())) {
                    objectNode.put(field.getKey(), "***");
                } else {
                    redact(field.getValue());
                }
            }
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::redact);
        }
    }

    private boolean sensitiveField(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELDS.stream().anyMatch(normalized::contains);
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String limit(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "passwd", "pwd", "token", "secret", "authorization", "credential",
            "email", "phone", "mobile", "idcard", "identity", "cookie"
    );
}

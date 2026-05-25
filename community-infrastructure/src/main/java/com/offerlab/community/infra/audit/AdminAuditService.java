package com.offerlab.community.infra.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

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
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100));
        return mapper.listRecent(clean(action), clean(resourceType), safeLimit);
    }

    private String toJson(Object value) throws Exception {
        return value == null ? null : objectMapper.writeValueAsString(value);
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
}

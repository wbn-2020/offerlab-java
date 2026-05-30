package com.offerlab.community.infra.audit;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminAuditLog {
    private Long id;
    private Long operatorUid;
    private String action;
    private String resourceType;
    private String resourceId;
    private String beforeJson;
    private String afterJson;
    private String remark;
    private LocalDateTime createTime;
}

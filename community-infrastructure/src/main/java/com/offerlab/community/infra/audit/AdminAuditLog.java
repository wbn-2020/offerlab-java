package com.offerlab.community.infra.audit;

import lombok.Data;

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
}

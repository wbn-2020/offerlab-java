package com.offerlab.community.infra.moderation;

import lombok.Data;

@Data
public class ModerationKeyword {
    private Long id;
    private String keyword;
    private String matchType;
    private String action;
    private String scope;
    private Integer enabled;
    private String remark;
    private Long operatorUid;
}

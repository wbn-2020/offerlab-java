package com.offerlab.community.infra.moderation;

import lombok.Data;

@Data
public class ModerationKeywordAdminCmd {
    private String keyword;
    private String matchType;
    private String action;
    private String scope;
    private Integer enabled;
    private String remark;
}

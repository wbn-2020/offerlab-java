package com.offerlab.community.infra.moderation;

import lombok.Data;

@Data
public class UserModerationStateCmd {
    private Long uid;
    private Integer muteHours;
    private Integer banHours;
    private String reason;
}

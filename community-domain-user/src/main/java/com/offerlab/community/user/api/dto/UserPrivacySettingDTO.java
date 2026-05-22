package com.offerlab.community.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrivacySettingDTO {
    private String profileVisibility;
    private String intentVisibility;
    private Boolean searchable;
    private Boolean interactionNotification;
    private Boolean systemNotification;
}

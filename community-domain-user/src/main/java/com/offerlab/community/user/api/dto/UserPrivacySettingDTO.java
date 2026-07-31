package com.offerlab.community.user.api.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrivacySettingDTO {
    @Pattern(regexp = "PUBLIC|FOLLOWERS|PRIVATE", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String profileVisibility;
    @Pattern(regexp = "PUBLIC|FOLLOWERS|PRIVATE", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String intentVisibility;
    private Boolean searchable;
    private Boolean interactionNotification;
    private Boolean systemNotification;
    private Boolean likeNotification;
    private Boolean commentNotification;
    private Boolean followNotification;
    private Boolean favoriteNotification;
    private Boolean mentionNotification;
}

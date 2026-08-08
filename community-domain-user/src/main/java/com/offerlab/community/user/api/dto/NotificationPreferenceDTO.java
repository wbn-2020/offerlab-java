package com.offerlab.community.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceDTO {
    private Boolean interactionNotification;
    private Boolean systemNotification;
    private Boolean likeNotification;
    private Boolean commentNotification;
    private Boolean followNotification;
    private Boolean favoriteNotification;
    private Boolean mentionNotification;
    private Boolean governanceReminderNotification;
    private Integer governanceReminderQuietStartMinute;
    private Integer governanceReminderQuietEndMinute;
    private String governanceReminderTimeZone;
}

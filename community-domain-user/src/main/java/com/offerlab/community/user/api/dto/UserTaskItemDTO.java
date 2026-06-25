package com.offerlab.community.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTaskItemDTO {
    private String taskCode;
    private String title;
    private String description;
    private String actionText;
    private String actionRoute;
    private Boolean manualCompletable;
    private Boolean completed;
    private LocalDateTime completedAt;
}

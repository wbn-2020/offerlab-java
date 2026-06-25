package com.offerlab.community.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTaskOverviewDTO {
    private String taskType;
    private String title;
    private String subtitle;
    private Boolean active;
    private Integer completedCount;
    private Integer totalCount;
    private List<UserTaskItemDTO> items;
}

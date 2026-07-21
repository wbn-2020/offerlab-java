package com.offerlab.community.interaction.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevisitReadStateDTO {
    private Long itemId;
    private String resourceKey;
    private String status;
    private String targetPath;
    private LocalDateTime dueAt;
    private LocalDateTime updateTime;
}

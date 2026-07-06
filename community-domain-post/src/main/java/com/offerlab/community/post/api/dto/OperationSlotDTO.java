package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationSlotDTO {
    private Long id;
    private String slotCode;
    private String name;
    private String description;
    private String status;
    private Integer sortOrder;
    private Integer defaultLimit;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String previewToken;
    private Integer currentVersion;
    private String schemaVersion;
    private Integer sourceVersion;
    private String source;
    private Boolean degraded;
    private String fallbackReason;
    private List<OperationSlotItemDTO> items;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

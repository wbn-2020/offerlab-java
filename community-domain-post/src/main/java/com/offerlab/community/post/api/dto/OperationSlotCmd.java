package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperationSlotCmd {
    @Size(max = 64)
    private String slotCode;
    @Size(max = 64)
    private String name;
    @Size(max = 500)
    private String description;
    private String status;
    private Integer sortOrder;
    private Integer defaultLimit;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
}

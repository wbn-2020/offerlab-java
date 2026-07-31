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
public class ContactRequestDTO {
    private Long requestId;
    private Long requesterUid;
    private String requesterName;
    private Long receiverUid;
    private String receiverName;
    private String sourceType;
    private Long sourceId;
    private String scene;
    private String messagePreview;
    private String requestStatus;
    private LocalDateTime receiverActionTime;
    private LocalDateTime expireTime;
    private Long reportId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

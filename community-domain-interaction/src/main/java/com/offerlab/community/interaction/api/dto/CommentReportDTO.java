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
public class CommentReportDTO {
    private Long id;
    private Long commentId;
    private Long postId;
    private Long reporterUid;
    private String reason;
    private String detail;
    private Integer reportStatus;
    private Long reviewerUid;
    private String reviewNote;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

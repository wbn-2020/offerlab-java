package com.offerlab.community.interaction.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentReportDTO {
    private Long id;
    private Long commentId;
    private Long postId;
    private String postTitle;
    private String commentSummary;
    private Long reporterUid;
    private String reason;
    private String detail;
    private Integer reportStatus;
    private String userStatus;
    private Boolean postAvailable;
    private Boolean commentAvailable;
    private String contentStatus;
    private Long reviewerUid;
    private String reviewNote;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

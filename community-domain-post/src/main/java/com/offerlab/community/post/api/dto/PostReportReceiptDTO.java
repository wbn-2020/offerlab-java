package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostReportReceiptDTO {
    private Long id;
    private Long postId;
    private Boolean targetAvailable;
    private String postTitle;
    private String postSummary;
    private String reason;
    private String detail;
    private Integer reportStatus;
    private String userStatus;
    private String statusMessage;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

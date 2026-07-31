package com.offerlab.community.report.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserReportReceiptDTO(
        Long reportId,
        String sourceType,
        Long targetId,
        Long postId,
        String targetTitle,
        String targetSummary,
        String reason,
        String detail,
        String userStatus,
        String resultText,
        String targetPath,
        LocalDateTime createTime,
        LocalDateTime reviewTime,
        Boolean targetAvailable
) {
}

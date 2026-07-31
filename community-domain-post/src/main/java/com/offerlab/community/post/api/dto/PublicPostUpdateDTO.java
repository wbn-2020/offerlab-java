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
public class PublicPostUpdateDTO {
    private Integer resultVersion;
    private String publicUpdateSummary;
    private String impactScope;
    private LocalDateTime createTime;
}

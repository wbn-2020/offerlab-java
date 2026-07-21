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
public class PostReferenceDTO {
    private Long id;
    private Long postId;
    private Long ownerUid;
    private String referenceType;
    private String title;
    private String url;
    private String normalizedUrl;
    private String sourceDomain;
    private String note;
    private String brokenReason;
    private String referenceStatus;
    private Integer sortOrder;
    private Integer revision;
    private LocalDateTime lastConfirmedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

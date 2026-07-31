package com.offerlab.community.analytics.collaboration.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicContributionFactDTO {

    private String factType;
    private Long sourceId;
    private String referenceType;
    private Long referenceId;
    private Integer domain;
    private LocalDateTime occurredAt;
}

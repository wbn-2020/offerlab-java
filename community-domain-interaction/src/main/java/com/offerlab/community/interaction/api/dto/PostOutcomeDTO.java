package com.offerlab.community.interaction.api.dto;

import com.offerlab.community.interaction.api.enums.PostOutcomePublicationStatus;
import com.offerlab.community.interaction.api.enums.PostOutcomeStatus;
import com.offerlab.community.interaction.api.enums.PostOutcomeType;
import com.offerlab.community.interaction.api.enums.PostOutcomeVisibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostOutcomeDTO {
    private Long id;
    private Long postId;
    private Long uid;
    private PostOutcomeType outcomeType;
    private String contextNote;
    private String resultNote;
    private PostOutcomeVisibility visibility;
    private PostOutcomePublicationStatus publicationStatus;
    private LocalDateTime consentedAt;
    private Long reviewerUid;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private LocalDateTime followUpAt;
    private PostOutcomeStatus outcomeStatus;
    private Integer revision;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

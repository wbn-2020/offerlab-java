package com.offerlab.community.interaction.api.dto;

import com.offerlab.community.interaction.api.enums.PostOutcomeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostOutcomeSampleDTO {
    private Long id;
    private PostOutcomeType outcomeType;
    private String resultNote;
    private Long contributorUid;
    private LocalDateTime createdAt;
}

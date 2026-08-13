package com.offerlab.community.analytics.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorChallengeAdminCmd {
    private Long id;

    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}")
    private String challengeCode;

    @NotBlank
    @Size(max = 80)
    private String title;

    @NotBlank
    @Size(max = 500)
    private String description;

    @Min(1)
    @Max(5)
    private Integer domain;

    @Min(1)
    @Max(16)
    private Integer postType;

    @Size(max = 64)
    private String assistTemplateCode;

    @NotNull
    private LocalDateTime startsAt;

    @NotNull
    private LocalDateTime endsAt;

    @NotBlank
    @Size(max = 500)
    private String reason;
}

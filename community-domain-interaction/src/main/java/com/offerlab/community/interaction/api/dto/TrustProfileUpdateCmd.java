package com.offerlab.community.interaction.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrustProfileUpdateCmd {

    @NotNull
    private TrustProfileRole authorRole;

    private LocalDateTime experienceStartAt;

    private LocalDateTime experienceEndAt;

    @Size(max = 500)
    private String applicableAudience;

    @Size(max = 1000)
    private String applicableContext;

    @Size(max = 2000)
    private String processSummary;

    @Size(max = 2000)
    private String outcomeSummary;

    @Size(max = 2000)
    private String knownLimitations;

    @Size(max = 1000)
    private String sourceSummary;

    @Size(max = 1000)
    private String interestDisclosure;
}

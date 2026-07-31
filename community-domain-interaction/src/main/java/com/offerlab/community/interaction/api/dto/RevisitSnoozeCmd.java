package com.offerlab.community.interaction.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RevisitSnoozeCmd {
    @NotNull
    private LocalDateTime until;
}

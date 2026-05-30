package com.offerlab.community.question.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PrepTargetCmd {
    @NotBlank
    @Pattern(regexp = "company|position|tag")
    private String targetType;

    @NotBlank
    @Size(max = 128)
    private String targetValue;

    private LocalDate interviewDate;

    @Pattern(regexp = "low|medium|high|urgent")
    private String priority;

    @Size(max = 300)
    private String note;
}

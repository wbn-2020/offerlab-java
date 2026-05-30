package com.offerlab.community.question.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MockInterviewStartCmd {
    @Size(max = 128)
    private String company;

    @Size(max = 128)
    private String position;

    @Size(max = 16)
    private String difficulty;

    @Size(max = 64)
    private String focusTag;

    @Min(1)
    @Max(10)
    private Integer questionCount;
}

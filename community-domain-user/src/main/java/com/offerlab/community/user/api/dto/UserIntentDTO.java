package com.offerlab.community.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIntentDTO {
    private List<String> targetCompanies;
    private List<String> targetPositions;
    private Integer yearsOfExp;
    private String expectedCity;
    private List<String> techStack;
    private SalaryRange expectedSalaryRange;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalaryRange {
        private Integer min;
        private Integer max;
        private String unit;
    }
}

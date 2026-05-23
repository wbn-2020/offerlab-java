package com.offerlab.community.user.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    /** 后端规范字段；前端历史上使用 targetCity，读写时需继续兼容。 */
    @JsonAlias("targetCity")
    private String expectedCity;
    private List<String> techStack;
    private SalaryRange expectedSalaryRange;

    @JsonProperty("targetCity")
    public String getTargetCity() {
        return expectedCity;
    }

    @JsonProperty("targetCity")
    public void setTargetCity(String targetCity) {
        this.expectedCity = targetCity;
    }

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

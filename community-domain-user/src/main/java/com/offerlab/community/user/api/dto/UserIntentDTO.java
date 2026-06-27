package com.offerlab.community.user.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
    private static final int MAX_LIST_SIZE = 20;
    private static final int MAX_ITEM_LENGTH = 64;
    private static final int MAX_CITY_LENGTH = 64;
    private static final int MAX_YEARS_OF_EXP = 50;
    private static final int MAX_SALARY_VALUE = 1000;
    private static final int MAX_SALARY_UNIT_LENGTH = 16;

    @Size(max = MAX_LIST_SIZE)
    private List<@Size(max = MAX_ITEM_LENGTH) String> targetCompanies;
    @Size(max = MAX_LIST_SIZE)
    private List<@Size(max = MAX_ITEM_LENGTH) String> targetPositions;
    @Min(0)
    @Max(MAX_YEARS_OF_EXP)
    private Integer yearsOfExp;
    /**
     * Backend canonical field name. Frontend historically used targetCity,
     * so both names must remain compatible during read/write.
     */
    @Size(max = MAX_CITY_LENGTH)
    @JsonAlias("targetCity")
    private String expectedCity;
    @Size(max = MAX_LIST_SIZE)
    private List<@Size(max = MAX_ITEM_LENGTH) String> techStack;
    @Size(max = MAX_LIST_SIZE)
    private List<@Size(max = MAX_ITEM_LENGTH) String> interestTopics;
    @Size(max = MAX_LIST_SIZE)
    private List<@Size(max = MAX_ITEM_LENGTH) String> interestTags;
    @Size(max = MAX_LIST_SIZE)
    private List<@Size(max = MAX_ITEM_LENGTH) String> contentPreferences;
    @Valid
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
        @Min(0)
        @Max(MAX_SALARY_VALUE)
        private Integer min;
        @Min(0)
        @Max(MAX_SALARY_VALUE)
        private Integer max;
        @Size(max = MAX_SALARY_UNIT_LENGTH)
        private String unit;
    }
}

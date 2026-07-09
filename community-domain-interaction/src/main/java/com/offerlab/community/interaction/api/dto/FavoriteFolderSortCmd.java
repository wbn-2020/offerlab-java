package com.offerlab.community.interaction.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FavoriteFolderSortCmd {
    @NotNull
    @Min(0)
    @Max(1000000)
    private Integer sortOrder;
}

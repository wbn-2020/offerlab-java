package com.offerlab.community.interaction.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class FavoriteFolderReorderCmd {

    @NotNull
    @Size(min = 1, max = 100)
    private List<@Positive Long> folderIds;
}

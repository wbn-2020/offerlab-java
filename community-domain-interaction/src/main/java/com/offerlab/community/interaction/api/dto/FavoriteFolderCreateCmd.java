package com.offerlab.community.interaction.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FavoriteFolderCreateCmd {
    @NotBlank
    @Size(max = 50)
    private String name;
    @Size(max = 255)
    private String description;
    private String visibility;
    private Boolean privateFolder;
}

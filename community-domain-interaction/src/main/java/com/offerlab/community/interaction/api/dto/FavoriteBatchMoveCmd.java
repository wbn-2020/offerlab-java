package com.offerlab.community.interaction.api.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class FavoriteBatchMoveCmd {
    @NotEmpty
    private List<Long> postIds;
    private Long folderId;
}

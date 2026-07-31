package com.offerlab.community.post.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PostReferenceReorderCmd {
    @NotNull
    @Size(min = 1, max = 100)
    private List<@Valid PostReferenceReorderItemCmd> items;
}

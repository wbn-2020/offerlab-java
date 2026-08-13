package com.offerlab.community.feed.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class FeedAuthorControlCmd {

    @NotNull
    @Positive
    private Long authorUid;
}

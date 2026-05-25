package com.offerlab.community.feed.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FeedFeedbackCmd {
    @NotNull
    private Long postId;
    @Pattern(regexp = "not_interested|hide|dislike", message = "unsupported feedback action")
    private String action;
    @Size(max = 200)
    private String reason;
}

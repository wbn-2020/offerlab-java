package com.offerlab.community.feed.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FeedFeedbackCmd {
    @NotNull
    private Long postId;
    @NotNull
    @Pattern(
            regexp = "(?i)HIDE|LESS_LIKE_THIS|RESTORE|not_interested|dislike|hide_author|more_like_this",
            message = "unsupported feedback action")
    private String action;
    @Size(max = 200)
    private String reason;
    @Pattern(
            regexp = "(?i)\\s*(?:NOT_RELEVANT|TOO_FREQUENT|ALREADY_KNOWN|QUALITY_NOT_EXPECTED|OTHER)?\\s*",
            message = "unsupported feedback reason code")
    private String reasonCode;
}

package com.offerlab.community.feed.api.dto;

import lombok.Data;

@Data
public class FeedFeedbackCmd {
    private Long postId;
    private String action;
    private String reason;
}

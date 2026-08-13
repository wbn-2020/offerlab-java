package com.offerlab.community.feed.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelHotBoardItemVO {
    private Integer rank;
    private FeedItemVO item;
    private String reasonText;
}

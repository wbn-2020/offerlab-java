package com.offerlab.community.feed.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelHotBoardVO {
    private Integer domain;
    private String ruleVersion;
    private LocalDateTime generatedAt;
    private List<ChannelHotBoardItemVO> items;
}

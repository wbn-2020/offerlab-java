package com.offerlab.community.interaction.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeActionPage<T> {
    private List<T> items;
    private String nextCursor;
    private Boolean hasMore;
    private Long total;
    private List<String> sourceErrors;
}

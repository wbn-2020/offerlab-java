package com.offerlab.community.post.knowledge.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeThreadNodeDTO {
    private Long postId;
    private String title;
    /** Original channel of the post; null stays null (never bucketed). */
    private Integer domain;
    /** Relation that links this node toward the anchor side of the chain. */
    private PostKnowledgeRelationType relationType;
    /** 1-based distance from the anchor in walk direction. */
    private int hop;
}

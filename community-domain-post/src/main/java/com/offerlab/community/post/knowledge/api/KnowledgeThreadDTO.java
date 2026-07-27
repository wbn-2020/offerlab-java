package com.offerlab.community.post.knowledge.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Read-only bounded reading thread around one anchor post. Upstream nodes are
 * "read these first" (posts this chain marks as prerequisites / predecessors),
 * downstream nodes are "read these next" (continuations / supersessions).
 * Only APPROVED + VISIBLE relations between publicly visible posts appear.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeThreadDTO {
    private Long anchorPostId;
    private List<KnowledgeThreadNodeDTO> upstream;
    private List<KnowledgeThreadNodeDTO> downstream;
    /** true when the walk stopped at a bound, not at the natural end of the chain. */
    private boolean truncated;
}

package com.offerlab.community.post.knowledge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostKnowledgeRelationCreateCmd {

    @NotNull
    @Positive
    private Long targetPostId;

    @NotNull
    private PostKnowledgeRelationType relationType;

    @NotBlank
    @Size(max = 2000)
    private String reasonText;
}

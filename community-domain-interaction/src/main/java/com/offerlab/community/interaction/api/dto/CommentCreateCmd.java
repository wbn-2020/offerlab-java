package com.offerlab.community.interaction.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentCreateCmd {
    @NotNull
    private Long postId;
    @NotNull
    private Long authorUid;
    private Long parentId;
    private Long replyToUid;
    @NotBlank
    @Size(max = 2000)
    private String content;
}

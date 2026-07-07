package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentReportReviewedEvent {
    private Long reporterUid;
    private Long reportId;
    private Long postId;
    private Long commentId;
    private String userStatus;
    private String targetPath;
}

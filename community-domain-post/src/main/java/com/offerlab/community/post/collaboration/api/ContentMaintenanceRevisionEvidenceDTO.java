package com.offerlab.community.post.collaboration.api;

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
public class ContentMaintenanceRevisionEvidenceDTO {
    private String state;
    private String summary;
    private List<PublicUpdateDTO> updates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicUpdateDTO {
        private Integer resultVersion;
        private String publicUpdateSummary;
        private String impactScope;
        private LocalDateTime createTime;
    }
}

package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Public, read-only projection for a topic or collection space.
 *
 * The projection intentionally contains only public facts. Optional sources
 * may be unavailable without making the primary topic/collection unreadable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunitySpaceDTO {
    private String spaceType;
    private Long spaceId;
    private String slug;
    private String title;
    private String description;
    private Integer domain;
    private String visibility;
    private String canonicalPath;
    private List<PostBriefDTO> representativeItems;
    private KnowledgeAssetOverviewDTO knowledge;
    private DiscoveryMapDTO discovery;
    private List<PublicUpdateDTO> latestUpdates;
    private List<RelatedNeedDTO> relatedNeeds;
    private List<RelatedContributionDTO> relatedContributions;
    private GovernanceSummaryDTO governanceSummary;
    private String nextCursor;
    private Boolean hasMore;
    private List<String> degradedSources;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicUpdateDTO {
        private String eventId;
        private String dedupKey;
        private String sourceType;
        private String sourceId;
        private String title;
        private String summary;
        private String impactScope;
        private String targetPath;
        private Integer resultVersion;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedNeedDTO {
        private Long id;
        private String sourceType;
        private Long sourceRefId;
        private String contentFormat;
        private String title;
        private String description;
        private String status;
        private LocalDateTime updateTime;
        private String targetPath;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedContributionDTO {
        private Long contributionId;
        private Long postId;
        private Long contributorUid;
        private String contributionType;
        private String title;
        private String targetPath;
        private LocalDateTime createTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GovernanceSummaryDTO {
        private long publicItemCount;
        private long publicContributionCount;
        private boolean hasPublicMaintenance;
        private String publicStatus;
    }
}

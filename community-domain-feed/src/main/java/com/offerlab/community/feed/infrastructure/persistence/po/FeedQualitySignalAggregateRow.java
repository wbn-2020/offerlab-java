package com.offerlab.community.feed.infrastructure.persistence.po;

import java.time.LocalDateTime;

public class FeedQualitySignalAggregateRow {
    private Long postId;
    private Integer domain;
    private Long distinctReaderCount;
    private Long qualifiedPostCount;
    private LocalDateTime latestUpdatedAt;

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Integer getDomain() {
        return domain;
    }

    public void setDomain(Integer domain) {
        this.domain = domain;
    }

    public Long getDistinctReaderCount() {
        return distinctReaderCount;
    }

    public void setDistinctReaderCount(Long distinctReaderCount) {
        this.distinctReaderCount = distinctReaderCount;
    }

    public Long getQualifiedPostCount() {
        return qualifiedPostCount;
    }

    public void setQualifiedPostCount(Long qualifiedPostCount) {
        this.qualifiedPostCount = qualifiedPostCount;
    }

    public LocalDateTime getLatestUpdatedAt() {
        return latestUpdatedAt;
    }

    public void setLatestUpdatedAt(LocalDateTime latestUpdatedAt) {
        this.latestUpdatedAt = latestUpdatedAt;
    }
}

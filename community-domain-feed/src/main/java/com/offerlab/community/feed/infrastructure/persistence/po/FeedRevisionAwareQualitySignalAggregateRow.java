package com.offerlab.community.feed.infrastructure.persistence.po;

public class FeedRevisionAwareQualitySignalAggregateRow {
    private Long postId;
    private Long priorRevisionDistinctReaderCount;
    private Long currentRevisionDistinctReaderCount;

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getPriorRevisionDistinctReaderCount() {
        return priorRevisionDistinctReaderCount;
    }

    public void setPriorRevisionDistinctReaderCount(Long priorRevisionDistinctReaderCount) {
        this.priorRevisionDistinctReaderCount = priorRevisionDistinctReaderCount;
    }

    public Long getCurrentRevisionDistinctReaderCount() {
        return currentRevisionDistinctReaderCount;
    }

    public void setCurrentRevisionDistinctReaderCount(Long currentRevisionDistinctReaderCount) {
        this.currentRevisionDistinctReaderCount = currentRevisionDistinctReaderCount;
    }
}

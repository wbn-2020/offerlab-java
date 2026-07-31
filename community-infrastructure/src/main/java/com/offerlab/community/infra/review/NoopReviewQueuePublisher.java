package com.offerlab.community.infra.review;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(ReviewQueuePublisher.class)
public class NoopReviewQueuePublisher implements ReviewQueuePublisher {
    @Override
    public void upsert(ReviewQueueItemCommand command) {
        // Optional integration point: domain modules can run without the search/governance module.
    }

    @Override
    public void resolve(String sourceType, Long sourceId, String status, String result, String note, Long operatorUid) {
        // Optional integration point: domain modules can run without the search/governance module.
    }

    @Override
    public void resolveRequired(String sourceType, Long sourceId, String status, String result,
                                String note, Long operatorUid) {
        throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(), "Review queue service is unavailable");
    }
}

package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.review.ReviewQueueSourceActionHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PostPendingReviewQueueActionHandler implements ReviewQueueSourceActionHandler {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PostApplicationService postService;

    public PostPendingReviewQueueActionHandler(@Lazy PostApplicationService postService) {
        this.postService = postService;
    }

    @Override
    public boolean supports(String sourceType) {
        return PostApplicationService.PENDING_REVIEW_SOURCE_TYPE.equals(normalize(sourceType));
    }

    @Override
    public boolean resolveSourceBeforeQueue() {
        return true;
    }

    @Override
    public void handle(String sourceType, Long sourceId, String status, String result, String note, Long operatorUid) {
        handle(sourceType, sourceId, status, result, note, operatorUid, null);
    }

    @Override
    public void handle(String sourceType, Long sourceId, String status, String result, String note,
                       Long operatorUid, String extJson) {
        if (!supports(sourceType) || sourceId == null || "CLOSED".equals(normalize(status))) {
            return;
        }
        String normalizedStatus = normalize(status);
        if (!"APPROVED".equals(normalizedStatus)
                && !"REJECTED".equals(normalizedStatus)) {
            return;
        }
        postService.resolvePendingPostReview(
                sourceId,
                operatorUid,
                "APPROVED".equals(normalizedStatus),
                note,
                expectedVersion(extJson)
        );
    }

    private Integer expectedVersion(String extJson) {
        if (extJson == null || extJson.isBlank()) {
            throw staleQueueItem();
        }
        try {
            JsonNode version = JSON.readTree(extJson).get("version");
            if (version == null || !version.canConvertToInt() || version.asInt() < 0) {
                throw staleQueueItem();
            }
            return version.asInt();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw staleQueueItem();
        }
    }

    private BizException staleQueueItem() {
        return new BizException(
                ErrorCode.INVALID_STATUS.getCode(),
                "审核任务缺少有效的帖子内容版本，请重新生成审核任务");
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}

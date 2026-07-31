package com.offerlab.community.question.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.review.ReviewQueueSourceActionHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Locale;

@Component
public class QuestionPendingReviewQueueActionHandler implements ReviewQueueSourceActionHandler {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final QuestionFacadeImpl questionFacade;

    public QuestionPendingReviewQueueActionHandler(@Lazy QuestionFacadeImpl questionFacade) {
        this.questionFacade = questionFacade;
    }

    @Override
    public boolean supports(String sourceType) {
        return QuestionFacadeImpl.PENDING_REVIEW_SOURCE_TYPE.equals(normalize(sourceType));
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
        if (!"APPROVED".equals(normalizedStatus) && !"REJECTED".equals(normalizedStatus)) {
            return;
        }
        questionFacade.resolvePendingQuestionReview(
                sourceId,
                "APPROVED".equals(normalizedStatus),
                expectedUpdateTime(extJson)
        );
    }

    private LocalDateTime expectedUpdateTime(String extJson) {
        if (extJson == null || extJson.isBlank()) {
            throw staleQueueItem();
        }
        try {
            JsonNode value = JSON.readTree(extJson).get("expectedUpdateTime");
            if (value == null || !value.isTextual() || value.asText().isBlank()) {
                throw staleQueueItem();
            }
            return LocalDateTime.parse(value.asText());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw staleQueueItem();
        }
    }

    private BizException staleQueueItem() {
        return new BizException(ErrorCode.INVALID_STATUS.getCode(), "审核任务缺少有效的题目内容版本，请重新生成审核任务");
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}

package com.offerlab.community.question.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionExtractionEventListener {
    private final QuestionFacade questionFacade;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQuestionExtractRequested(QuestionExtractRequestedEvent event) {
        try {
            questionFacade.processExtractTask(event.getTaskId());
        } catch (Exception e) {
            log.warn("question extraction task failed unexpectedly: taskId={} postId={}",
                    event.getTaskId(), event.getPostId(), e);
        }
    }
}

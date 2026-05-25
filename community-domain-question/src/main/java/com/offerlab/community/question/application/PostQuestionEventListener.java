package com.offerlab.community.question.application;

import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.api.event.PostUpdatedEvent;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.PostFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostQuestionEventListener {
    private final QuestionFacade questionFacade;
    private final PostFacade postFacade;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostPublished(PostPublishedEvent event) {
        try {
            questionFacade.extractPostQuestions(event.getPostId(), false);
        } catch (Exception e) {
            log.warn("post question extraction skipped: postId={}", event.getPostId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostUpdated(PostUpdatedEvent event) {
        try {
            questionFacade.extractPostQuestions(event.getPostId(), false);
            questionFacade.evictQuestionCachesForPost(postFacade.getPost(event.getPostId()));
        } catch (Exception e) {
            log.warn("post question extraction skipped after update: postId={}", event.getPostId(), e);
        }
    }
}

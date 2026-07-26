package com.offerlab.community.question.application;

import com.offerlab.community.post.api.event.PostDeletedEvent;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.api.event.PostUpdatedEvent;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.PostFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostQuestionEventListener {
    private final QuestionFacade questionFacade;
    private final PostFacade postFacade;

    @Value("${offerlab.kafka.enabled:true}")
    private boolean kafkaEnabled;

    @Value("${offerlab.question.kafka-consumer-enabled:true}")
    private boolean kafkaConsumerEnabled = true;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPostPublished(PostPublishedEvent event) {
        if (kafkaOwnsDelivery()) {
            return;
        }
        handlePostPublishedSynchronously(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPostUpdated(PostUpdatedEvent event) {
        if (kafkaOwnsDelivery()) {
            return;
        }
        handlePostUpdatedSynchronously(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPostDeleted(PostDeletedEvent event) {
        if (kafkaOwnsDelivery()) {
            return;
        }
        handlePostDeletedSynchronously(event);
    }

    public void handlePostPublishedSynchronously(PostPublishedEvent event) {
        questionFacade.extractPostQuestions(
                requirePostId(event == null ? null : event.getPostId()), false);
    }

    public void handlePostUpdatedSynchronously(PostUpdatedEvent event) {
        Long postId = requirePostId(event == null ? null : event.getPostId());
        questionFacade.extractPostQuestions(postId, false);
        try {
            PostDTO post = postFacade.getPost(postId);
            if (post != null) {
                questionFacade.evictQuestionCachesForPost(post);
            }
        } catch (RuntimeException e) {
            // The extraction task is already durable; cache eviction is best effort.
            log.warn("question cache eviction skipped after post update: postId={}", postId, e);
        }
    }

    public void handlePostDeletedSynchronously(PostDeletedEvent event) {
        questionFacade.hidePostQuestions(
                requirePostId(event == null ? null : event.getPostId()));
    }

    void setKafkaEnabled(boolean kafkaEnabled) {
        this.kafkaEnabled = kafkaEnabled;
    }

    void setKafkaConsumerEnabled(boolean kafkaConsumerEnabled) {
        this.kafkaConsumerEnabled = kafkaConsumerEnabled;
    }

    private boolean kafkaOwnsDelivery() {
        return kafkaEnabled && kafkaConsumerEnabled;
    }

    private Long requirePostId(Long postId) {
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("postId is required for question synchronization");
        }
        return postId;
    }
}

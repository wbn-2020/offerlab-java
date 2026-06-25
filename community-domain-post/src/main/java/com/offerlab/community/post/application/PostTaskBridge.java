package com.offerlab.community.post.application;

import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.user.application.UserTaskApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostTaskBridge {

    private final UserTaskApplicationService taskService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostPublished(PostPublishedEvent event) {
        if (event == null || event.getAuthorId() == null || event.getPostId() == null) {
            return;
        }
        try {
            taskService.markPublishCompleted(event.getAuthorId(), event.getPostId());
        } catch (Exception e) {
            log.warn("user task publish completion skipped: authorId={} postId={} reason={}",
                    event.getAuthorId(), event.getPostId(), e.toString());
        }
    }
}

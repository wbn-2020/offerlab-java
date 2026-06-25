package com.offerlab.community.interaction.application;

import com.offerlab.community.interaction.api.event.CommentCreatedEvent;
import com.offerlab.community.interaction.api.event.PostFavoritedEvent;
import com.offerlab.community.interaction.api.event.PostLikedEvent;
import com.offerlab.community.user.application.UserTaskApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionTaskBridge {

    private final UserTaskApplicationService taskService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostLiked(PostLikedEvent event) {
        if (event == null || event.getUid() == null || event.getPostId() == null) {
            return;
        }
        completeInteraction(event.getUid(), "interaction.like", event.getPostId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostFavorited(PostFavoritedEvent event) {
        if (event == null || event.getUid() == null || event.getPostId() == null) {
            return;
        }
        completeInteraction(event.getUid(), "interaction.favorite", event.getPostId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(CommentCreatedEvent event) {
        if (event == null || event.getUid() == null) {
            return;
        }
        completeInteraction(event.getUid(), "interaction.comment",
                event.getCommentId() != null ? event.getCommentId() : event.getPostId());
    }

    private void completeInteraction(Long uid, String source, Long refId) {
        try {
            taskService.markInteractionCompleted(uid, source, refId);
        } catch (Exception e) {
            log.warn("user task interaction completion skipped: uid={} source={} refId={} reason={}",
                    uid, source, refId, e.toString());
        }
    }
}

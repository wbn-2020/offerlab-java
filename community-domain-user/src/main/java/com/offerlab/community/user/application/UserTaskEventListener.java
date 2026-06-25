package com.offerlab.community.user.application;

import com.offerlab.community.user.api.event.UserFollowedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserTaskEventListener {

    private final UserTaskApplicationService taskService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserFollowed(UserFollowedEvent event) {
        if (event == null || event.getFollowerId() == null || event.getFolloweeId() == null) {
            return;
        }
        try {
            taskService.markFollowCompleted(event.getFollowerId(), event.getFolloweeId());
        } catch (Exception e) {
            log.warn("user task follow completion skipped: followerId={} followeeId={} reason={}",
                    event.getFollowerId(), event.getFolloweeId(), e.toString());
        }
    }
}

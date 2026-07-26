package com.offerlab.community.notification.application;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotificationTransactionBoundaryTest {

    @Test
    void eventFacingNotificationWritesMustIsolateFailureFromTheInboxTransaction() throws Exception {
        assertRequiresNew("notifyLike", Long.class, Long.class, Integer.class, Long.class);
        assertRequiresNew("notifyCommentLike", Long.class, Long.class, Long.class, Long.class);
        assertRequiresNew("notifyComment", Long.class, Long.class, Long.class, Long.class);
        assertRequiresNew("notifyAnswerAccepted",
                Long.class, Long.class, Long.class, Long.class, Map.class);
        assertRequiresNew("notifyDiscussionFollowComment",
                Long.class, Long.class, Long.class, Long.class);
        assertRequiresNew("notifyDiscussionFollowQualityComment",
                Long.class, Long.class, Long.class, Long.class, String.class);
        assertRequiresNew("notifyFollower", Long.class, Long.class);
        assertRequiresNew("notifyFavorite", Long.class, Long.class, Long.class);
        assertRequiresNew("notifyMention", Long.class, Long.class, Long.class, Long.class);
        assertRequiresNew("notifySystem", Long.class, Long.class, Long.class, Map.class);
        assertRequiresNew("notifyReportReceipt",
                Long.class, String.class, Long.class, String.class, String.class);
        assertRequiresNew("notifyContactRequestReceived", Long.class, Long.class, Long.class);
        assertRequiresNew("notifyContactRequestAccepted", Long.class, Long.class, Long.class);
        assertRequiresNew("notifyContactRequestRejected", Long.class, Long.class, Long.class);
        assertRequiresNew("createFromRetryTask",
                Long.class, Long.class, Integer.class, Integer.class, Long.class, Map.class);
    }

    private static void assertRequiresNew(String methodName, Class<?>... parameterTypes)
            throws Exception {
        Transactional transactional = NotificationFacadeImpl.class
                .getDeclaredMethod(methodName, parameterTypes)
                .getAnnotation(Transactional.class);
        assertNotNull(transactional, methodName + " must define an explicit transaction boundary");
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation(),
                methodName + " must not mark the inbox transaction rollback-only");
    }
}

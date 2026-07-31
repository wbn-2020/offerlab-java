package com.offerlab.community.notification.realtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationSessionRegistryTest {

    @Test
    void keepsUsersIsolatedAndEnforcesPerUserLimit() {
        NotificationSessionRegistry registry = new NotificationSessionRegistry();
        TestWebSocketSession userOneFirst = new TestWebSocketSession("u1-a");
        TestWebSocketSession userOneSecond = new TestWebSocketSession("u1-b");
        TestWebSocketSession userTwo = new TestWebSocketSession("u2-a");

        assertTrue(registry.register(1L, userOneFirst.session(), 1));
        assertFalse(registry.register(1L, userOneSecond.session(), 1));
        assertTrue(registry.register(2L, userTwo.session(), 1));
        assertEquals(1, registry.sessionCount(1L));
        assertEquals(1, registry.sessionCount(2L));

        registry.unregister(userOneFirst.session());

        assertEquals(0, registry.sessionCount(1L));
        assertEquals(1, registry.sessionsFor(2L).size());
    }

    @Test
    void discardsClosedSessionsBeforeApplyingLimit() {
        NotificationSessionRegistry registry = new NotificationSessionRegistry();
        TestWebSocketSession closed = new TestWebSocketSession("closed");
        TestWebSocketSession replacement = new TestWebSocketSession("replacement");

        assertTrue(registry.register(3L, closed.session(), 1));
        closed.setOpen(false);

        assertTrue(registry.register(3L, replacement.session(), 1));
        assertEquals(1, registry.sessionCount(3L));
    }
}

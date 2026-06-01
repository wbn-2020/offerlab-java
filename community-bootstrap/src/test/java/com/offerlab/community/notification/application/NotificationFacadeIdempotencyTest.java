package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationMessageMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationMessagePO;
import com.offerlab.community.user.api.UserFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationFacadeIdempotencyTest {

    @Mock
    private NotificationMessageMapper mapper;
    @Mock
    private SnowflakeIdGenerator idGen;
    @Mock
    private UserFacade userFacade;

    private NotificationFacadeImpl facade;

    @BeforeEach
    void setUp() {
        facade = new NotificationFacadeImpl(mapper, idGen, new ObjectMapper(), userFacade);
        when(mapper.tableExists()).thenReturn(1);
        when(mapper.dedupKeyColumnExists()).thenReturn(1);
        when(userFacade.allowsLikeNotification(42L)).thenReturn(true);
    }

    @Test
    void normalAndReplayedNotificationsShareTheSameDedupKey() {
        when(idGen.nextId()).thenReturn(101L, 102L);
        when(mapper.insertIgnore(any(NotificationMessagePO.class))).thenReturn(1, 0);

        facade.notifyLike(42L, 7L, 1, 99L);
        facade.createFromRetryTask(42L, 7L, 1, 1, 99L, Map.of("action", "like"));

        ArgumentCaptor<NotificationMessagePO> captor = ArgumentCaptor.forClass(NotificationMessagePO.class);
        verify(mapper, times(2)).insertIgnore(captor.capture());
        verify(mapper, never()).insertLegacy(any());

        List<NotificationMessagePO> messages = captor.getAllValues();
        assertEquals(2, messages.size());
        assertEquals(messages.get(0).getDedupKey(), messages.get(1).getDedupKey());
        assertEquals("42:7:1:1:99:like", messages.get(0).getDedupKey());
        assertNotNull(messages.get(0).getContentJson());
    }
}

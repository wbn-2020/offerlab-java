package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.GrowthEventTrackCmd;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthEventMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrowthEventServiceTest {

    @Mock
    private GrowthEventMapper mapper;
    @Mock
    private SnowflakeIdGenerator idGenerator;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void publicTrackRejectsSensitiveGrowthEvents() {
        GrowthEventService service = new GrowthEventService(mapper, idGenerator);
        GrowthEventTrackCmd publicPostView = new GrowthEventTrackCmd();
        publicPostView.setEventType(GrowthEventService.PUBLIC_POST_VIEW);
        publicPostView.setContentId(300L);
        publicPostView.setDomain(1);
        GrowthEventTrackCmd crossDomainConsume = new GrowthEventTrackCmd();
        crossDomainConsume.setEventType(GrowthEventService.CROSS_DOMAIN_CONSUME);
        crossDomainConsume.setContentId(300L);
        crossDomainConsume.setDomain(1);

        boolean publicPostViewTracked = service.track(publicPostView);
        boolean crossDomainTracked = service.track(crossDomainConsume);

        assertFalse(publicPostViewTracked);
        assertFalse(crossDomainTracked);
        verifyNoInteractions(mapper, idGenerator);
    }

    @Test
    void publicTrackAllowsAnonymousAuthRedirectClick() {
        GrowthEventService service = new GrowthEventService(mapper, idGenerator);
        when(idGenerator.nextId()).thenReturn(1001L);
        when(mapper.tableExists()).thenReturn(1);
        GrowthEventTrackCmd authRedirect = new GrowthEventTrackCmd();
        authRedirect.setEventType(GrowthEventService.AUTH_REDIRECT_CLICK);
        authRedirect.setTargetType("POST");
        authRedirect.setTargetValue("300");
        authRedirect.setSourcePage("post.detail");

        boolean tracked = service.track(authRedirect);

        assertTrue(tracked);
        verify(mapper).insertEvent(argThat(matchesAuthRedirect()));
    }

    @Test
    void cleansEveryAllowedEventTypeInBoundedRetentionBatches() throws Exception {
        List<String> operations = new ArrayList<>();
        GrowthEventMapper cleanupMapper = (GrowthEventMapper) Proxy.newProxyInstance(
                GrowthEventMapper.class.getClassLoader(),
                new Class<?>[]{GrowthEventMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> 1;
                    case "deleteBefore" -> {
                        operations.add(args[0] + ":" + args[2]);
                        yield 0;
                    }
                    default -> defaultValue(method.getReturnType());
                });
        GrowthEventService service = new GrowthEventService(cleanupMapper, null);

        GrowthEventService.class.getMethod("cleanupExpiredEvents").invoke(service);

        assertEquals(10, operations.size());
        assertTrue(operations.stream().allMatch(item -> item.endsWith(":5000")));
    }

    private static ArgumentMatcher<com.offerlab.community.analytics.infrastructure.persistence.po.GrowthEventPO> matchesAuthRedirect() {
        return event -> event != null
                && GrowthEventService.AUTH_REDIRECT_CLICK.equals(event.getEventType())
                && "POST".equals(event.getTargetType())
                && "300".equals(event.getTargetValue())
                && "post.detail".equals(event.getSourcePage())
                && event.getUid() == null;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }
}

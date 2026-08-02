package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.incentive.api.quota.EntitlementQuotaFacade;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentAssistEnhancedRequestMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentAssistEnhancedServiceTest {

    @Mock
    private ContentAssistAiClient aiClient;
    @Mock
    private ContentAssistService contentAssistService;
    @Mock
    private EntitlementQuotaFacade entitlementQuotaFacade;
    @Mock
    private ContentAssistEnhancedRequestMapper requestMapper;
    @Mock
    private ContentAssistEnhancedFinalizer finalizer;
    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Test
    void recoveryTimeoutIsBoundedByTheReservationExecutionWindowLimit() throws ReflectiveOperationException {
        ContentAssistEnhancedService service = new ContentAssistEnhancedService(
                new ObjectMapper(),
                aiClient,
                contentAssistService,
                entitlementQuotaFacade,
                requestMapper,
                finalizer,
                idGenerator);
        setField(service, "enhancedRequestTimeoutSeconds", 3_600L);
        setField(service, "executionGraceSeconds", 30L);
        when(aiClient.maximumCompletionDurationMillis()).thenReturn(60_000L);

        assertEquals(900L, service.effectiveRecoveryTimeoutSeconds(3_600L));
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

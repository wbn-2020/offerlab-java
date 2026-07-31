package com.offerlab.community.infra.moderation;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContentModerationAuthorizationTest {

    @Test
    @SuppressWarnings("unchecked")
    void sourceAuthorizationRunsBeforeModerationWrites() {
        ContentModerationMapper mapper = mock(ContentModerationMapper.class);
        SnowflakeIdGenerator idGen = mock(SnowflakeIdGenerator.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        ObjectProvider<ContentModerationSourceAuthorizationHandler> handlers = mock(ObjectProvider.class);
        ContentModerationSourceAuthorizationHandler rejectingHandler =
                new ContentModerationSourceAuthorizationHandler() {
                    @Override
                    public boolean supports(String scope, String sourceType) {
                        return ContentModerationService.SCOPE_POST.equals(scope)
                                && ContentModerationService.SOURCE_POST.equals(sourceType);
                    }

                    @Override
                    public void requireAuthorized(Long uid, Long sourceId) {
                        throw new BizException(ErrorCode.FORBIDDEN);
                    }
                };
        when(handlers.orderedStream()).thenReturn(Stream.of(rejectingHandler));
        ContentModerationService service = new ContentModerationService(mapper, idGen, events, handlers);

        BizException ex = assertThrows(
                BizException.class,
                () -> service.checkContent(
                        8L,
                        ContentModerationService.SCOPE_POST,
                        ContentModerationService.SOURCE_POST,
                        42L,
                        "content that must never be persisted"
                )
        );

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verifyNoInteractions(mapper, idGen, events);
    }

    @Test
    @SuppressWarnings("unchecked")
    void newSourceModerationDoesNotRequireAnExistingSourceRow() {
        ContentModerationMapper mapper = mock(ContentModerationMapper.class);
        SnowflakeIdGenerator idGen = mock(SnowflakeIdGenerator.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        ObjectProvider<ContentModerationSourceAuthorizationHandler> handlers = mock(ObjectProvider.class);
        ContentModerationSourceAuthorizationHandler rejectingHandler =
                new ContentModerationSourceAuthorizationHandler() {
                    @Override
                    public boolean supports(String scope, String sourceType) {
                        return true;
                    }

                    @Override
                    public void requireAuthorized(Long uid, Long sourceId) {
                        throw new BizException(ErrorCode.POST_NOT_FOUND);
                    }
                };
        when(handlers.orderedStream()).thenReturn(Stream.of(rejectingHandler));
        ContentModerationService service = new ContentModerationService(mapper, idGen, events, handlers);

        ContentModerationService.ModerationDecision decision = service.checkNewSourceContent(
                7L,
                ContentModerationService.SCOPE_POST,
                ContentModerationService.SOURCE_POST,
                42L,
                ""
        );

        assertFalse(decision.reviewRequired());
        verifyNoInteractions(mapper, idGen, events);
    }
}

package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedExceptionDTO;
import com.offerlab.community.post.application.ContentAssistEnhancedOperationsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentAssistEnhancedAdminControllerTest {

    @Mock
    private ContentAssistEnhancedOperationsService operationsService;

    private ContentAssistEnhancedAdminController controller;

    @BeforeEach
    void setUp() {
        UserContext.set(99L);
        controller = new ContentAssistEnhancedAdminController(operationsService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void cursorAndSizeReturnPageResult() {
        ContentAssistEnhancedExceptionDTO item = ContentAssistEnhancedExceptionDTO.builder()
                .requestId(101L)
                .build();
        PageResult<ContentAssistEnhancedExceptionDTO> page = PageResult.of(List.of(item), "101", true);
        when(operationsService.exceptions(99L, "0", 20)).thenReturn(page);

        Result<?> result = controller.exceptions("0", 20, null);

        assertSame(page, result.getData());
        verify(operationsService).exceptions(99L, "0", 20);
    }

    @Test
    void legacyLimitRequestKeepsListResponse() {
        ContentAssistEnhancedExceptionDTO item = ContentAssistEnhancedExceptionDTO.builder()
                .requestId(101L)
                .build();
        PageResult<ContentAssistEnhancedExceptionDTO> page = PageResult.of(List.of(item), null, false);
        when(operationsService.exceptions(99L, null, 20)).thenReturn(page);

        Result<?> result = controller.exceptions(null, null, 20);

        assertEquals(List.of(item), result.getData());
        verify(operationsService).exceptions(99L, null, 20);
    }
}

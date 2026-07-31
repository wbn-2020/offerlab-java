package com.offerlab.community.api;

import com.offerlab.community.analytics.application.ProjectionHealthService;
import com.offerlab.community.analytics.controller.ProjectionHealthController;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectionHealthControllerApiTest {

    @Mock
    private ProjectionHealthService service;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new ProjectionHealthController(service), jwtService);
        when(jwtService.parseUid("token")).thenReturn(8L);
    }

    @Test
    void opaqueKnowledgeCursorIsDelegatedWithoutNumericConversion() throws Exception {
        String cursor = encodeCursor("kl1|50|PENDING_SUGGESTIONS");
        when(service.issues("KNOWLEDGE_LIFECYCLE", cursor, 20, 8L))
                .thenReturn(PageResult.empty());

        mvc.perform(get("/api/v1/admin/community-health/projections/KNOWLEDGE_LIFECYCLE/issues")
                        .header("Authorization", "Bearer token")
                        .param("cursor", cursor)
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hasMore").value(false));

        verify(service).issues("KNOWLEDGE_LIFECYCLE", cursor, 20, 8L);
    }

    @Test
    void cursorLongerThan256CharactersReturnsParamErrorBeforeService() throws Exception {
        String cursor = "x".repeat(257);
        when(service.issues("KNOWLEDGE_LIFECYCLE", cursor, 20, 8L))
                .thenThrow(new BizException(
                        ErrorCode.PARAM_ERROR.getCode(),
                        "cursor is invalid"));

        mvc.perform(get("/api/v1/admin/community-health/projections/KNOWLEDGE_LIFECYCLE/issues")
                        .header("Authorization", "Bearer token")
                        .param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verify(service).issues("KNOWLEDGE_LIFECYCLE", cursor, 20, 8L);
    }

    @Test
    void malformedCompositeCursorReturnsHttp400ParamError() throws Exception {
        when(service.issues("KNOWLEDGE_LIFECYCLE", "%%%", 20, 8L))
                .thenThrow(new BizException(
                        ErrorCode.PARAM_ERROR.getCode(),
                        "cursor is invalid"));

        mvc.perform(get("/api/v1/admin/community-health/projections/KNOWLEDGE_LIFECYCLE/issues")
                        .header("Authorization", "Bearer token")
                        .param("cursor", "%%%"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    private static String encodeCursor(String raw) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}

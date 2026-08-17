package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.exception.SystemException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.ops.application.ContentEnvironmentCacheInvalidationService;
import com.offerlab.community.ops.controller.ContentEnvironmentCacheAdminController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentEnvironmentCacheAdminControllerApiTest {

    @Mock private ContentEnvironmentCacheInvalidationService invalidationService;
    @Mock private AdminPermissionService adminPermissionService;
    @Mock private AdminAuditService adminAuditService;
    @Mock private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new ContentEnvironmentCacheAdminController(
                invalidationService, adminPermissionService, adminAuditService), jwtService);
    }

    @Test
    void adminCanRunAuditedExactInvalidation() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(invalidationService.invalidate(anyString(), eq(List.of(101L, 102L))))
                .thenAnswer(invocation -> new ContentEnvironmentCacheInvalidationService.InvalidationResult(
                        invocation.getArgument(0),
                        List.of(101L, 102L),
                        7,
                        1,
                        1L,
                        1L,
                        2L,
                        2,
                        List.of("recommendation"),
                        "LIVE_DATABASE_FILTERED"
                ));

        mvc.perform(post("/api/v1/admin/content-environment/cache-invalidation")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postIds": [101, 102],
                                  "remark": "R6 approved content classification",
                                  "confirmationPhrase": "CONFIRM"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.postIds[0]").value(101))
                .andExpect(jsonPath("$.data.exactCacheKeysEvicted").value(7))
                .andExpect(jsonPath("$.data.liveQueryDerivedMode").value("LIVE_DATABASE_FILTERED"));

        verify(adminPermissionService).requireAdmin(7L);
        verify(adminAuditService).requireWritable(
                eq("CONTENT_ENV_CACHE_INVALIDATE"), eq("POST_CACHE_BATCH"), anyString());
        verify(invalidationService).invalidate(anyString(), eq(List.of(101L, 102L)));
        verify(adminAuditService, org.mockito.Mockito.times(2)).recordRequired(
                eq(7L),
                eq("CONTENT_ENV_CACHE_INVALIDATE"),
                eq("POST_CACHE_BATCH"),
                anyString(),
                any(),
                any(),
                eq("R6 approved content classification")
        );
    }

    @Test
    void ordinaryUserCannotInvalidateCaches() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(11L);
        doThrow(new BizException(ErrorCode.FORBIDDEN))
                .when(adminPermissionService)
                .requireAdmin(11L);

        mvc.perform(post("/api/v1/admin/content-environment/cache-invalidation")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"postIds":[101],"remark":"approved","confirmationPhrase":"CONFIRM"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(invalidationService, adminAuditService);
    }

    @Test
    void riskConfirmationIsMandatory() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/admin/content-environment/cache-invalidation")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"postIds":[101],"remark":"approved","confirmationPhrase":"WRONG"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verify(adminPermissionService).requireAdmin(7L);
        verifyNoInteractions(invalidationService, adminAuditService);
    }

    @Test
    void auditUnavailabilityFailsClosedBeforeInvalidation() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        doThrow(new SystemException("audit unavailable"))
                .when(adminAuditService)
                .requireWritable(eq("CONTENT_ENV_CACHE_INVALIDATE"), eq("POST_CACHE_BATCH"), anyString());

        mvc.perform(post("/api/v1/admin/content-environment/cache-invalidation")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"postIds":[101],"remark":"approved","confirmationPhrase":"CONFIRM"}
                                """))
                .andExpect(status().isServiceUnavailable());

        verify(invalidationService, never()).invalidate(anyString(), any());
    }
}

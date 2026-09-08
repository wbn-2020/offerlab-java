package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.user.application.PasswordResetService;
import com.offerlab.community.user.application.UserApplicationService;
import com.offerlab.community.user.controller.AuthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerApiTest {
    @Mock
    private UserApplicationService userService;
    @Mock
    private PasswordResetService passwordResetService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new AuthController(userService, passwordResetService), jwtService);
    }

    @Test
    void loginReturnsToken() throws Exception {
        when(userService.login(eq("u@example.com"), eq("secret123"), anyString())).thenReturn("jwt-token");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"u@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }

    @Test
    void loginAcceptsAccountFieldAndKeepsEmailCompatibility() throws Exception {
        when(userService.login(eq("admin"), eq("secret123"), anyString())).thenReturn("jwt-token");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"admin\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }

    @Test
    void loginBusinessErrorUsesUnifiedResponse() throws Exception {
        when(userService.login(eq("u@example.com"), eq("bad-pass"), anyString()))
                .thenThrow(new BizException(ErrorCode.PASSWORD_ERROR));

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"u@example.com\",\"password\":\"bad-pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PASSWORD_ERROR.getCode()));
    }

    @Test
    void logoutWithoutAuthorizationHeaderIsIdempotentSuccess() throws Exception {
        mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verifyNoInteractions(userService);
    }

    @Test
    void registerRejectsInvalidBodyBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-email\",\"password\":\"123\",\"nickname\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(userService);
    }

    @Test
    void registerRequiresAgreementAndReturnsAuthenticatedSession() throws Exception {
        when(userService.register(
                eq("u@example.com"),
                eq("secret123"),
                eq("newbie"),
                eq(true),
                eq(true),
                eq(UserApplicationService.CURRENT_TERMS_VERSION),
                eq(UserApplicationService.CURRENT_PRIVACY_VERSION))).thenReturn(42L);
        when(userService.login(eq("u@example.com"), eq("secret123"), anyString())).thenReturn("register-token");

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "u@example.com",
                                  "password": "secret123",
                                  "nickname": "newbie",
                                  "termsAccepted": true,
                                  "privacyAccepted": true,
                                  "termsVersion": "2026-09-01",
                                  "privacyVersion": "2026-09-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uid").value(42))
                .andExpect(jsonPath("$.data.token").value("register-token"));

        verify(userService).login(eq("u@example.com"), eq("secret123"), anyString());
    }

    @Test
    void registerRejectsMissingAgreementBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "u@example.com",
                                  "password": "secret123",
                                  "nickname": "newbie",
                                  "termsAccepted": false,
                                  "privacyAccepted": true,
                                  "termsVersion": "2026-09-01",
                                  "privacyVersion": "2026-09-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(userService);
    }
}

package com.offerlab.community.api;

import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import com.offerlab.community.user.application.ContactRequestSettingsService;
import com.offerlab.community.user.application.UserApplicationService;
import com.offerlab.community.user.controller.UserController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerApiTest {

    @Mock
    private UserFacade userFacade;
    @Mock
    private UserApplicationService userService;
    @Mock
    private ContactRequestSettingsService contactRequestSettingsService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new UserController(userFacade, userService, contactRequestSettingsService), jwtService);
    }

    @Test
    void updateIntentRejectsInvalidPayloadBeforeCallingService() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(put("/api/v1/users/me/intent")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetCompanies": [
                                    "OpenAI", "Anthropic", "DeepMind", "Microsoft", "Google", "ByteDance", "Alibaba", "Tencent", "Baidu", "Meituan",
                                    "JD", "Didi", "Kuaishou", "Xiaohongshu", "Shopee", "Grab", "AWS", "Azure", "Cloudflare", "Databricks",
                                    "Snowflake"
                                  ],
                                  "yearsOfExp": 99
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(userService);
    }

    @Test
    void getMeUsesTheSharedPublicPostCountBeforeReturningTheBrief() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(userFacade.getUserBrief(7L)).thenReturn(UserBriefDTO.builder()
                .uid(7L)
                .nickname("user")
                .postCount(0L)
                .build());
        doAnswer(invocation -> {
            UserBriefDTO dto = invocation.getArgument(0);
            dto.setPostCount(1L);
            return null;
        }).when(userService).applyPublicPostCount(any(UserBriefDTO.class));

        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.uid").value(7))
                .andExpect(jsonPath("$.data.postCount").value(1));
    }
}

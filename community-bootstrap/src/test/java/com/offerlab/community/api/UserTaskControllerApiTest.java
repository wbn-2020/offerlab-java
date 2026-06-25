package com.offerlab.community.api;

import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.user.api.dto.UserTaskOverviewDTO;
import com.offerlab.community.user.application.UserTaskApplicationService;
import com.offerlab.community.user.controller.UserTaskController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserTaskControllerApiTest {
    @Mock
    private UserTaskApplicationService taskService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new UserTaskController(taskService), jwtService);
    }

    @Test
    void onboardingTaskEndpointsRequireAuthAndDelegateToService() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(taskService.getOnboardingTasks(7L)).thenReturn(UserTaskOverviewDTO.builder()
                .taskType("ONBOARDING")
                .active(true)
                .totalCount(4)
                .completedCount(1)
                .items(List.of())
                .build());

        mvc.perform(get("/api/v1/me/onboarding-tasks")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskType").value("ONBOARDING"))
                .andExpect(jsonPath("$.data.totalCount").value(4));

        mvc.perform(post("/api/v1/me/onboarding-tasks/VIEW_PUBLIC_CONTENT/complete")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(taskService).getOnboardingTasks(7L);
        verify(taskService).completeOnboardingTask(7L, "VIEW_PUBLIC_CONTENT");
    }

    @Test
    void dailyTaskEndpointsRequireAuthAndDelegateToService() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(taskService.getDailyTasks(7L)).thenReturn(UserTaskOverviewDTO.builder()
                .taskType("DAILY")
                .active(true)
                .totalCount(3)
                .completedCount(0)
                .items(List.of())
                .build());

        mvc.perform(get("/api/v1/me/daily-tasks")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskType").value("DAILY"))
                .andExpect(jsonPath("$.data.totalCount").value(3));

        mvc.perform(post("/api/v1/me/daily-tasks/DAILY_VIEW_PUBLIC_CONTENT/complete")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(taskService).getDailyTasks(7L);
        verify(taskService).completeDailyTask(7L, "DAILY_VIEW_PUBLIC_CONTENT");
    }
}

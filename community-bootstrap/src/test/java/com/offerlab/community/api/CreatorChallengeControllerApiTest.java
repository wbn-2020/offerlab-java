package com.offerlab.community.api;

import com.offerlab.community.analytics.api.dto.CreatorChallengeCompletionResultDTO;
import com.offerlab.community.analytics.api.dto.CreatorChallengeWorkspaceDTO;
import com.offerlab.community.analytics.application.CreatorChallengeService;
import com.offerlab.community.analytics.controller.CreatorChallengeAdminController;
import com.offerlab.community.analytics.controller.CreatorChallengeController;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CreatorChallengeControllerApiTest {

    @Mock
    private CreatorChallengeService creatorChallengeService;
    @Mock
    private JwtService jwtService;

    private MockMvc creatorMvc;
    private MockMvc adminMvc;

    @BeforeEach
    void setUp() {
        creatorMvc = ApiTestSupport.mvc(new CreatorChallengeController(creatorChallengeService), jwtService);
        adminMvc = ApiTestSupport.mvc(new CreatorChallengeAdminController(creatorChallengeService), jwtService);
    }

    @Test
    void creatorChallengeWorkspaceAndMutationsRequireLogin() throws Exception {
        creatorMvc.perform(get("/api/v1/creator-growth/challenges/workspace"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        creatorMvc.perform(post("/api/v1/creator-growth/challenges/11/join"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(creatorChallengeService);
    }

    @Test
    void authenticatedCreatorCanReadJoinAndCompleteChallenge() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(18L);
        CreatorChallengeWorkspaceDTO.CreatorChallengeDTO challenge = challenge("JOINED");
        when(creatorChallengeService.workspace(18L)).thenReturn(CreatorChallengeWorkspaceDTO.builder()
                .challenges(List.of(challenge))
                .badges(List.of())
                .boundaryCopy("No points or certification.")
                .build());
        when(creatorChallengeService.join(18L, 11L)).thenReturn(challenge);
        when(creatorChallengeService.complete(eq(18L), eq(11L), any())).thenReturn(
                CreatorChallengeCompletionResultDTO.builder()
                        .challenge(challenge)
                        .newlyAwardedBadges(List.of())
                        .replayed(false)
                        .build());

        creatorMvc.perform(get("/api/v1/creator-growth/challenges/workspace")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.challenges[0].id").value(11L))
                .andExpect(jsonPath("$.data.boundaryCopy").value("No points or certification."));

        creatorMvc.perform(post("/api/v1/creator-growth/challenges/11/join")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participationStatus").value("JOINED"));

        creatorMvc.perform(post("/api/v1/creator-growth/challenges/11/complete")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postId": 901
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(false))
                .andExpect(jsonPath("$.data.challenge.id").value(11L));

        verify(creatorChallengeService).workspace(18L);
        verify(creatorChallengeService).join(18L, 11L);
        verify(creatorChallengeService).complete(eq(18L), eq(11L), any());
    }

    @Test
    void adminChallengeEndpointsRequireLoginAndUseCurrentOperator() throws Exception {
        adminMvc.perform(get("/api/v1/creator-growth/admin/challenges"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        when(jwtService.parseUid("token")).thenReturn(18L);
        when(creatorChallengeService.adminChallenges(18L)).thenReturn(List.of(challenge(null)));

        adminMvc.perform(get("/api/v1/creator-growth/admin/challenges")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].challengeCode").value("PUBLIC_WRITING_WEEK"))
                .andExpect(jsonPath("$.data[0].participationStatus").doesNotExist());

        verify(creatorChallengeService).adminChallenges(18L);
    }

    private static CreatorChallengeWorkspaceDTO.CreatorChallengeDTO challenge(String participationStatus) {
        return CreatorChallengeWorkspaceDTO.CreatorChallengeDTO.builder()
                .id(11L)
                .challengeCode("PUBLIC_WRITING_WEEK")
                .title("Public writing week")
                .description("Publish one public reflection.")
                .domain(1)
                .postType(15)
                .status("PUBLISHED")
                .startsAt(LocalDateTime.of(2026, 8, 2, 8, 0))
                .endsAt(LocalDateTime.of(2026, 8, 9, 8, 0))
                .participationStatus(participationStatus)
                .build();
    }
}

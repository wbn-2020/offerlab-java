package com.offerlab.community.api;

import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.post.api.dto.ExpertCertificationApplicationDTO;
import com.offerlab.community.post.api.dto.ExpertCertificationApplicantApplicationDTO;
import com.offerlab.community.post.api.dto.ExpertCertificationEligibilityDTO;
import com.offerlab.community.post.application.ExpertCertificationService;
import com.offerlab.community.post.controller.ExpertCertificationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExpertCertificationControllerApiTest {

    @Mock
    private ExpertCertificationService expertCertificationService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new ExpertCertificationController(expertCertificationService), jwtService);
    }

    @Test
    void eligibilityUsesAuthenticatedApplicantContext() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(expertCertificationService.getEligibility(7L, 5)).thenReturn(ExpertCertificationEligibilityDTO.builder()
                .domain(5)
                .domainName("investment")
                .eligible(true)
                .riskAcknowledgementRequired(true)
                .riskWarning("Community content is not investment advice.")
                .explanation("You have enough public investment education posts for manual review.")
                .checks(List.of(ExpertCertificationEligibilityDTO.CheckItemDTO.builder()
                        .code("published_posts")
                        .passed(true)
                        .detail("3/3")
                        .build()))
                .build());

        mvc.perform(get("/api/v1/expert-certifications/eligibility")
                        .header("Authorization", "Bearer token")
                        .param("domain", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.domain").value(5))
                .andExpect(jsonPath("$.data.riskAcknowledgementRequired").value(true))
                .andExpect(jsonPath("$.data.riskWarning").value("Community content is not investment advice."));

        verify(expertCertificationService).getEligibility(7L, 5);
    }

    @Test
    void submitApplicationBindsRiskAcknowledgementAndEvidenceFields() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(expertCertificationService.submit(any(), eq(7L))).thenReturn(ExpertCertificationApplicantApplicationDTO.builder()
                .id(9001L)
                .applicantUid(7L)
                .domain(5)
                .status(ExpertCertificationService.STATUS_SUBMITTED)
                .statusLabel("SUBMITTED")
                .riskAcknowledged(true)
                .autoCertified(false)
                .build());

        mvc.perform(post("/api/v1/expert-certifications/applications")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "domain": 5,
                                  "evidenceSummary": "three public investment education posts",
                                  "evidenceLinks": ["https://example.test/post/1"],
                                  "riskAcknowledged": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(9001))
                .andExpect(jsonPath("$.data.status").value(ExpertCertificationService.STATUS_SUBMITTED))
                .andExpect(jsonPath("$.data.autoCertified").value(false))
                .andExpect(jsonPath("$.data.reviewerUid").doesNotExist())
                .andExpect(jsonPath("$.data.reviewNote").doesNotExist())
                .andExpect(jsonPath("$.data.revokedBy").doesNotExist())
                .andExpect(jsonPath("$.data.revokeNote").doesNotExist());

        verify(expertCertificationService).submit(any(), eq(7L));
    }

    @Test
    void listMineUsesAuthenticatedApplicantContext() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(expertCertificationService.listMine(7L, 2)).thenReturn(List.of(
                ExpertCertificationApplicantApplicationDTO.builder()
                        .id(9002L)
                        .applicantUid(7L)
                        .domain(2)
                        .status(ExpertCertificationService.STATUS_SUBMITTED)
                        .statusLabel("SUBMITTED")
                        .autoCertified(false)
                        .build()));

        mvc.perform(get("/api/v1/expert-certifications/applications/me")
                        .header("Authorization", "Bearer token")
                        .param("domain", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(9002))
                .andExpect(jsonPath("$.data[0].domain").value(2))
                .andExpect(jsonPath("$.data[0].reviewerUid").doesNotExist())
                .andExpect(jsonPath("$.data[0].reviewNote").doesNotExist())
                .andExpect(jsonPath("$.data[0].revokedBy").doesNotExist())
                .andExpect(jsonPath("$.data[0].revokeNote").doesNotExist());

        verify(expertCertificationService).listMine(7L, 2);
    }

    @Test
    void revokeEndpointUsesAuthenticatedApplicantContext() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(expertCertificationService.revoke(9001L, "withdrawn by applicant", 7L)).thenReturn(ExpertCertificationApplicantApplicationDTO.builder()
                .id(9001L)
                .applicantUid(7L)
                .domain(2)
                .status(ExpertCertificationService.STATUS_REVOKED)
                .statusLabel("REVOKED")
                .autoCertified(false)
                .build());

        mvc.perform(post("/api/v1/expert-certifications/applications/9001/revoke")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note": "withdrawn by applicant"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(ExpertCertificationService.STATUS_REVOKED))
                .andExpect(jsonPath("$.data.revokedBy").doesNotExist())
                .andExpect(jsonPath("$.data.reviewerUid").doesNotExist())
                .andExpect(jsonPath("$.data.reviewNote").doesNotExist())
                .andExpect(jsonPath("$.data.revokeNote").doesNotExist());

        verify(expertCertificationService).revoke(9001L, "withdrawn by applicant", 7L);
    }

    @Test
    void reviewQueueUsesAuthenticatedModeratorContext() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(88L);
        when(expertCertificationService.listReviewQueue(2, ExpertCertificationService.STATUS_SUBMITTED, 15, 88L))
                .thenReturn(List.of(
                        ExpertCertificationApplicationDTO.builder()
                                .id(9003L)
                                .domain(2)
                                .status(ExpertCertificationService.STATUS_SUBMITTED)
                                .statusLabel("SUBMITTED")
                                .reviewerUid(66L)
                                .reviewNote("internal note")
                                .autoCertified(false)
                                .build()));

        mvc.perform(get("/api/v1/expert-certifications/admin/applications")
                        .header("Authorization", "Bearer token")
                        .param("domain", "2")
                        .param("status", String.valueOf(ExpertCertificationService.STATUS_SUBMITTED))
                        .param("limit", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(9003))
                .andExpect(jsonPath("$.data[0].status").value(ExpertCertificationService.STATUS_SUBMITTED))
                .andExpect(jsonPath("$.data[0].reviewerUid").value(66))
                .andExpect(jsonPath("$.data[0].reviewNote").value("internal note"));

        verify(expertCertificationService).listReviewQueue(2, ExpertCertificationService.STATUS_SUBMITTED, 15, 88L);
    }

    @Test
    void reviewEndpointUsesAuthenticatedReviewerContext() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(88L);
        when(expertCertificationService.review(eq(9001L), any(), eq(88L))).thenReturn(ExpertCertificationApplicationDTO.builder()
                .id(9001L)
                .domain(2)
                .status(ExpertCertificationService.STATUS_APPROVED)
                .statusLabel("APPROVED")
                .reviewerUid(88L)
                .autoCertified(false)
                .build());

        mvc.perform(post("/api/v1/expert-certifications/admin/applications/9001/review")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "note": "manual pilot approval"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(ExpertCertificationService.STATUS_APPROVED))
                .andExpect(jsonPath("$.data.reviewerUid").value(88))
                .andExpect(jsonPath("$.data.autoCertified").value(false));

        verify(expertCertificationService).review(eq(9001L), any(), eq(88L));
    }
}

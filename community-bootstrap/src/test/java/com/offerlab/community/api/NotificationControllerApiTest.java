package com.offerlab.community.api;

import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.notification.api.NotificationFacade;
import com.offerlab.community.notification.controller.NotificationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerApiTest {

    @Mock
    private NotificationFacade facade;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new NotificationController(facade), jwtService);
    }

    @Test
    void readRejectsPayloadsThatExceedBatchLimit() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/notifications/read")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idsPayload(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(facade);
    }

    @Test
    void readRejectsNullAndNonPositiveIds() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        for (String body : List.of(
                "{\"ids\":[1,null,2]}",
                "{\"ids\":[1,0,2]}",
                "{\"ids\":[1,-2,3]}"
        )) {
            mvc.perform(post("/api/v1/notifications/read")
                            .header("Authorization", "Bearer token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
        }

        verifyNoInteractions(facade);
    }

    private static String idsPayload(int count) {
        StringBuilder builder = new StringBuilder("{\"ids\":[");
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                builder.append(',');
            }
            builder.append(i);
        }
        return builder.append("]}").toString();
    }
}

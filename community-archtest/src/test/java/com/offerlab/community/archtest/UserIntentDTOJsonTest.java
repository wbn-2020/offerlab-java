package com.offerlab.community.archtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.user.api.dto.UserIntentDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserIntentDTOJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_accept_frontend_target_city_alias() throws Exception {
        UserIntentDTO dto = objectMapper.readValue("""
                {
                  "targetCompanies": ["OpenAI"],
                  "targetPositions": ["Backend"],
                  "yearsOfExp": 3,
                  "targetCity": "Shanghai",
                  "techStack": ["Java"]
                }
                """, UserIntentDTO.class);

        assertEquals("Shanghai", dto.getExpectedCity());
        assertEquals("Shanghai", dto.getTargetCity());
    }

    @Test
    void should_serialize_canonical_and_frontend_city_names() throws Exception {
        UserIntentDTO dto = UserIntentDTO.builder()
                .expectedCity("Beijing")
                .build();

        String json = objectMapper.writeValueAsString(dto);

        assertTrue(json.contains("\"expectedCity\":\"Beijing\""));
        assertTrue(json.contains("\"targetCity\":\"Beijing\""));
    }
}

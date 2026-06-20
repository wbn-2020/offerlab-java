package com.offerlab.community.archtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.user.api.dto.UserIntentDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void should_round_trip_general_community_interests_without_breaking_target_city_alias() throws Exception {
        UserIntentDTO dto = objectMapper.readValue("""
                {
                  "targetCompanies": ["OpenAI"],
                  "targetPositions": ["Backend"],
                  "targetCity": "Shanghai",
                  "techStack": ["Java"],
                  "interestTopics": ["职场成长", "租房生活"],
                  "interestTags": ["效率工具", "城市生活"],
                  "contentPreferences": ["图文笔记", "经验复盘"]
                }
                """, UserIntentDTO.class);

        assertEquals("Shanghai", dto.getExpectedCity());
        assertEquals("Shanghai", dto.getTargetCity());
        assertEquals(List.of("职场成长", "租房生活"), dto.getInterestTopics());
        assertEquals(List.of("效率工具", "城市生活"), dto.getInterestTags());
        assertEquals(List.of("图文笔记", "经验复盘"), dto.getContentPreferences());

        String json = objectMapper.writeValueAsString(dto);

        assertTrue(json.contains("\"targetCity\":\"Shanghai\""));
        assertTrue(json.contains("\"interestTopics\""));
        assertTrue(json.contains("\"interestTags\""));
        assertTrue(json.contains("\"contentPreferences\""));
    }
}

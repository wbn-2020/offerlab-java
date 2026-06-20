package com.offerlab.community.post.api;

import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicContentFilterTest {

    @Test
    void detectsSyntheticMarkersAcrossPublicPostFields() {
        assertTrue(PublicContentFilter.isSyntheticPost(PostBriefDTO.builder()
                .title("DEEP-E2E Java interview")
                .build()));
        assertTrue(PublicContentFilter.isSyntheticPost(PostBriefDTO.builder()
                .summary("created by codex smoke flow")
                .build()));
        assertTrue(PublicContentFilter.isSyntheticPost(PostBriefDTO.builder()
                .author(UserBriefDTO.builder().nickname("CODEX-\u6D4B\u8BD5\u7528\u6237").build())
                .build()));
        assertTrue(PublicContentFilter.isSyntheticPost(PostBriefDTO.builder()
                .tags(List.of(TagDTO.builder().name("smoke-run").build()))
                .build()));
    }

    @Test
    void keepsNormalPublicContentVisible() {
        assertFalse(PublicContentFilter.isSyntheticPost(PostBriefDTO.builder()
                .title("Java backend interview review")
                .summary("Redis cache and Spring transaction notes")
                .author(UserBriefDTO.builder().nickname("OfferLab User").build())
                .tags(List.of(TagDTO.builder().name("Java").build()))
                .build()));
        assertFalse(PublicContentFilter.isSyntheticPost(PostBriefDTO.builder()
                .title("\u6DF1\u6D4B\u79D1\u6280 Java \u540E\u7AEF\u4E00\u9762\u590D\u76D8")
                .summary("Spring \u4E8B\u52A1\u548C Redis \u7F13\u5B58\u4E00\u81F4\u6027\u7B14\u8BB0")
                .author(UserBriefDTO.builder().nickname("OfferLab \u6F14\u793A\u7BA1\u7406\u5458").build())
                .tags(List.of(TagDTO.builder().name("\u6DF1\u6D4B\u79D1\u6280").build()))
                .build()));
    }
}

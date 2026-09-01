package com.offerlab.community.post.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.post.api.dto.PostDetailCacheDTO;
import com.offerlab.community.post.api.dto.PostDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostDetailCacheDTOTest {

    @Test
    void preservesVisibilityMetadataAcrossJsonRoundTrip() throws Exception {
        PostDTO source = PostDTO.builder()
                .id(995100000000000001L)
                .authorId(995000000000000001L)
                .visibility(1)
                .postStatus(1)
                .contentEnvironment("COMMUNITY")
                .title("cache contract")
                .build();

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(PostDetailCacheDTO.from(source));
        PostDetailCacheDTO restored = mapper.readValue(json, PostDetailCacheDTO.class);

        assertEquals(source.getAuthorId(), restored.getAuthorId());
        assertEquals(source.getVisibility(), restored.getVisibility());
        assertEquals(source.getPostStatus(), restored.getPostStatus());
        assertEquals(source.getContentEnvironment(), restored.getContentEnvironment());
        assertTrue(restored.hasVisibilityMetadata());
        assertEquals(source.getId(), restored.toPostDTO().getId());
    }
}

package com.offerlab.community.post.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostContentEnvironmentTest {

    @Test
    void onlyCommunityContentCanUsePublicVisibilityRules() {
        assertTrue(post(Post.CONTENT_ENVIRONMENT_COMMUNITY).isVisibleTo(null, false));
        assertFalse(post(Post.CONTENT_ENVIRONMENT_TEST).isVisibleTo(7L, true));
        assertFalse(post(Post.CONTENT_ENVIRONMENT_INTERNAL).isVisibleTo(7L, true));
        assertFalse(post(Post.CONTENT_ENVIRONMENT_UNCLASSIFIED).isVisibleTo(7L, true));
        assertFalse(post(null).isVisibleTo(7L, true));
        assertFalse(post("UNKNOWN").isVisibleTo(7L, true));
    }

    @Test
    void communityClassificationUsesAnExactAllowListValue() {
        assertTrue(Post.isCommunityContent("COMMUNITY"));
        assertFalse(Post.isCommunityContent("community"));
        assertFalse(Post.isCommunityContent(" COMMUNITY "));
        assertFalse(Post.isCommunityContent(null));
    }

    private static Post post(String environment) {
        return Post.builder()
                .id(100L)
                .authorId(7L)
                .postStatus(Post.STATUS_PUBLISHED)
                .visibility(Post.VIS_PUBLIC)
                .contentEnvironment(environment)
                .build();
    }
}

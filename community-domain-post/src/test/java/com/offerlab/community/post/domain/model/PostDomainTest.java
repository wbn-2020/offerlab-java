package com.offerlab.community.post.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostDomainTest {

    @Test
    void missingLegacyCodeStillMapsToTech() {
        assertThrows(IllegalArgumentException.class, () -> PostDomain.fromCode(null));
    }

    @Test
    void unknownPositiveCodeFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> PostDomain.fromCode(999));
    }
}

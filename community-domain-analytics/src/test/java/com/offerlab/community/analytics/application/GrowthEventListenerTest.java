package com.offerlab.community.analytics.application;

import com.offerlab.community.post.api.event.PublicPostViewedEvent;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GrowthEventListenerTest {

    @Mock
    private GrowthEventService growthEventService;
    @Mock
    private PostMapper postMapper;

    @Test
    void publicPostViewedEventRecordsTrustedGrowthEvent() {
        GrowthEventListener listener = new GrowthEventListener(growthEventService, postMapper);

        listener.onPublicPostViewed(PublicPostViewedEvent.builder()
                .postId(901L)
                .viewerUid(12L)
                .domain(3)
                .build());

        verify(growthEventService).recordTrustedEvent(
                GrowthEventService.PUBLIC_POST_VIEW,
                12L,
                3,
                901L,
                "POST",
                "901",
                "post.detail");
        verifyNoInteractions(postMapper);
    }

    @Test
    void publicPostViewedEventRequiresPostId() {
        GrowthEventListener listener = new GrowthEventListener(growthEventService, postMapper);

        listener.onPublicPostViewed(PublicPostViewedEvent.builder()
                .viewerUid(12L)
                .domain(3)
                .build());

        verifyNoInteractions(growthEventService, postMapper);
    }
}

package com.offerlab.community.post.application;

import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostContentMaintenanceTaskReadServiceTest {

    @Test
    void returnsOnlyRequestedPublicPostIdsFromScopedMapperResult() {
        ContentMaintenanceTaskMapper mapper = mock(ContentMaintenanceTaskMapper.class);
        PostContentMaintenanceTaskReadService service = new PostContentMaintenanceTaskReadService(mapper);
        when(mapper.listActivePublicSourcePostIdsForAuthor(8L, Set.of(1001L, 1002L)))
                .thenReturn(Arrays.asList(1002L, 9999L, null));

        Set<Long> result = service.findActivePublicSourcePostIds(8L, List.of(1001L, 1002L));

        assertEquals(Set.of(1002L), result);
        verify(mapper).listActivePublicSourcePostIdsForAuthor(8L, Set.of(1001L, 1002L));
    }

    @Test
    void invalidAuthorOrNoPostIdsDoesNotQueryMaintenanceTasks() {
        ContentMaintenanceTaskMapper mapper = mock(ContentMaintenanceTaskMapper.class);
        PostContentMaintenanceTaskReadService service = new PostContentMaintenanceTaskReadService(mapper);

        assertEquals(Set.of(), service.findActivePublicSourcePostIds(null, List.of(1001L)));
        assertEquals(Set.of(), service.findActivePublicSourcePostIds(8L, Arrays.asList(0L, null)));
    }
}

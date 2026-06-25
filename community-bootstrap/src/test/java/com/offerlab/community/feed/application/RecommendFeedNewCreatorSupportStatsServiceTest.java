package com.offerlab.community.feed.application;

import com.offerlab.community.feed.infrastructure.persistence.mapper.RecommendFeedNewCreatorSupportStatMapper;
import com.offerlab.community.feed.infrastructure.persistence.po.RecommendFeedNewCreatorSupportStatPO;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendFeedNewCreatorSupportStatsServiceTest {

    @Mock
    private RecommendFeedNewCreatorSupportStatMapper mapper;
    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Test
    void recordRecommendFeedResponseSkipsInsertWhenTableIsNotMigrated() {
        RecommendFeedNewCreatorSupportStatsService service =
                new RecommendFeedNewCreatorSupportStatsService(mapper, idGenerator);
        when(mapper.tableExists()).thenReturn(0);

        assertDoesNotThrow(() -> service.recordRecommendFeedResponse(7L, 2, 6, 2));

        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordRecommendFeedResponsePersistsRecommendPageDeliveryAndSupportHits() {
        RecommendFeedNewCreatorSupportStatsService service =
                new RecommendFeedNewCreatorSupportStatsService(mapper, idGenerator);
        when(mapper.tableExists()).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(123456L);

        service.recordRecommendFeedResponse(7L, 2, 6, 2);

        ArgumentCaptor<RecommendFeedNewCreatorSupportStatPO> captor =
                ArgumentCaptor.forClass(RecommendFeedNewCreatorSupportStatPO.class);
        verify(mapper).insert(captor.capture());
        RecommendFeedNewCreatorSupportStatPO record = captor.getValue();
        assertEquals(123456L, record.getId());
        assertEquals(7L, record.getViewerUid());
        assertEquals(2, record.getDomain());
        assertEquals(6, record.getDeliveredItemCount());
        assertEquals(2, record.getSupportHitItemCount());
    }

    @Test
    void recordRecommendFeedResponseSwallowsInsertFailure() {
        RecommendFeedNewCreatorSupportStatsService service =
                new RecommendFeedNewCreatorSupportStatsService(mapper, idGenerator);
        when(mapper.tableExists()).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(123456L);
        doThrow(new RuntimeException("insert failed")).when(mapper).insert(org.mockito.ArgumentMatchers.any());

        assertDoesNotThrow(() -> service.recordRecommendFeedResponse(7L, null, 3, 1));
    }
}

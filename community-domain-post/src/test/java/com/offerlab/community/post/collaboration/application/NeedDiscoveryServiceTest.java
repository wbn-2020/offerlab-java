package com.offerlab.community.post.collaboration.application;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.collaboration.api.NeedDiscoveryItemDTO;
import com.offerlab.community.post.collaboration.infrastructure.persistence.NeedDiscoveryMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.NeedDiscoveryRows;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeedDiscoveryServiceTest {

    @Test
    void discoveryReasonsAreDeterministicAndCursorUsesRequestedSort() {
        FakeDiscoveryMapper mapper = new FakeDiscoveryMapper();
        NeedDiscoveryRows.NeedRow row = new NeedDiscoveryRows.NeedRow();
        row.setId(100L);
        row.setCreatorUid(7L);
        row.setDomain(2);
        row.setSourceType("POST");
        row.setSourceRefId(11L);
        row.setContentFormat("QUESTION");
        row.setTitle("Interview question");
        row.setDescription("A public need");
        row.setStatus("OPEN");
        row.setStalled(1);
        row.setViewerDomainMatch(1);
        row.setViewerFormatMatch(1);
        row.setCreateTime(LocalDateTime.of(2026, 7, 19, 10, 0));
        row.setUpdateTime(LocalDateTime.of(2026, 7, 19, 11, 0));
        mapper.rows = List.of(row);

        NeedDiscoveryService service = new NeedDiscoveryService(mapper);
        PageResult<NeedDiscoveryItemDTO> result = service.list(
                "interview", 2, "OPEN", "QUESTION", "POST", "STALLED_FIRST",
                "0", 10, 42L);

        assertEquals(1, result.getItems().size());
        NeedDiscoveryItemDTO item = result.getItems().get(0);
        assertTrue(item.getStalled());
        assertEquals(List.of(
                "FILTER_DOMAIN_MATCH",
                "FILTER_CONTENT_FORMAT_MATCH",
                "FILTER_SOURCE_TYPE_MATCH",
                "PUBLIC_DOMAIN_CONTRIBUTION_MATCH",
                "PUBLIC_CONTENT_FORMAT_CONTRIBUTION_MATCH"
        ), item.getMatchReasons());
        assertEquals("STALLED_FIRST", mapper.lastSort);
        assertEquals(42L, mapper.lastUid);
    }

    @Test
    void anonymousViewerWithoutPublicContributionMatchesGetsNoViewerReasons() {
        FakeDiscoveryMapper mapper = new FakeDiscoveryMapper();
        NeedDiscoveryRows.NeedRow row = new NeedDiscoveryRows.NeedRow();
        row.setId(101L);
        row.setDomain(3);
        row.setSourceType("COMMUNITY");
        row.setContentFormat("GUIDE");
        row.setTitle("Public guide need");
        row.setDescription("Visible without viewer-specific matches");
        row.setStatus("OPEN");
        row.setViewerDomainMatch(0);
        row.setViewerFormatMatch(0);
        row.setStalled(0);
        row.setCreateTime(LocalDateTime.of(2026, 7, 19, 12, 0));
        row.setUpdateTime(LocalDateTime.of(2026, 7, 19, 12, 0));
        mapper.rows = List.of(row);

        PageResult<NeedDiscoveryItemDTO> result = new NeedDiscoveryService(mapper).list(
                null, null, null, null, null, "LATEST", "0", 10, null);

        assertEquals(1, result.getItems().size());
        assertEquals(List.of(), result.getItems().get(0).getMatchReasons());
        assertEquals(null, mapper.lastUid);
    }

    @Test
    void authenticatedViewerWithoutPublicContributionMatchesGetsNoViewerReasons() {
        FakeDiscoveryMapper mapper = new FakeDiscoveryMapper();
        NeedDiscoveryRows.NeedRow row = new NeedDiscoveryRows.NeedRow();
        row.setId(102L);
        row.setDomain(3);
        row.setSourceType("COMMUNITY");
        row.setContentFormat("GUIDE");
        row.setTitle("Guide need");
        row.setDescription("No contribution match");
        row.setStatus("OPEN");
        row.setViewerDomainMatch(0);
        row.setViewerFormatMatch(0);
        row.setStalled(0);
        row.setCreateTime(LocalDateTime.of(2026, 7, 19, 13, 0));
        row.setUpdateTime(LocalDateTime.of(2026, 7, 19, 13, 0));
        mapper.rows = List.of(row);

        PageResult<NeedDiscoveryItemDTO> result = new NeedDiscoveryService(mapper).list(
                null, null, null, null, null, "LATEST", "0", 10, 42L);

        assertEquals(1, result.getItems().size());
        assertEquals(List.of(), result.getItems().get(0).getMatchReasons());
        assertEquals(42L, mapper.lastUid);
    }

    private static final class FakeDiscoveryMapper implements NeedDiscoveryMapper {
        private List<NeedDiscoveryRows.NeedRow> rows = List.of();
        private String lastSort;
        private Long lastUid;

        @Override
        public List<NeedDiscoveryRows.NeedRow> listNeeds(
                Long uid, Integer domain, String status, String contentFormat,
                String sourceType, String keyword, String sort,
                LocalDateTime cursorTime, Long cursorId, Integer cursorStalled, int limit) {
            this.lastUid = uid;
            this.lastSort = sort;
            return rows;
        }
    }
}

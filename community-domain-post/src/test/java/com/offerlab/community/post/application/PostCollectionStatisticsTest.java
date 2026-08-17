package com.offerlab.community.post.application;

import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.redis.cache.MultiLevelCache;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostTrustSignalsMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import com.offerlab.community.user.api.UserFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCollectionStatisticsTest {

    @Mock private PostRepository postRepo;
    @Mock private PostMapper postMapper;
    @Mock private PostExtensionMapper extensionMapper;
    @Mock private PostCounterMapper counterMapper;
    @Mock private TagMapper tagMapper;
    @Mock private PostTrustSignalsMapper trustSignalsMapper;
    @Mock private PostCounterRedis postCounterRedis;
    @Mock private PostVersionHistoryService versionHistoryService;
    @Mock private MultiLevelCache<PostDTO> multiLevelCache;
    @Mock private PostApplicationService postService;
    @Mock private UserFacade userFacade;
    @Mock private MigrationCheckService migrationCheckService;
    @Mock private AdminPermissionService adminPermissionService;

    @InjectMocks
    private PostFacadeImpl facade;

    @Test
    void zeroContentIsAnAvailableEmptyStatistic() {
        stubActiveTag(11L);
        when(tagMapper.countPublicPostsByTag(11L)).thenReturn(0L);
        when(tagMapper.countPublicPostTypesByTag(11L)).thenReturn(List.of());

        TagDTO detail = facade.getTag(11L);

        assertTrue(detail.getStatisticsAvailable());
        assertEquals(0L, detail.getPostCount());
        assertEquals(Map.of(), detail.getTypeDistribution());
    }

    @Test
    void singleTypeStatisticUsesTheFullCollectionCount() {
        stubActiveTag(12L);
        when(tagMapper.countPublicPostsByTag(12L)).thenReturn(1L);
        when(tagMapper.countPublicPostTypesByTag(12L))
                .thenReturn(List.of(Map.of("type", 2, "count", 1L)));

        TagDTO detail = facade.getTag(12L);

        assertTrue(detail.getStatisticsAvailable());
        assertEquals(1L, detail.getPostCount());
        assertEquals(Map.of("2", 1L), detail.getTypeDistribution());
    }

    @Test
    void multiPageCollectionKeepsAllTypeCountsInsteadOfUsingOnePage() {
        stubActiveTag(13L);
        when(tagMapper.countPublicPostsByTag(13L)).thenReturn(37L);
        when(tagMapper.countPublicPostTypesByTag(13L)).thenReturn(List.of(
                Map.of("type", 1, "count", 16L),
                Map.of("type", 2, "count", 12L),
                Map.of("type", 4, "count", 9L)));

        TagDTO detail = facade.getTag(13L);

        assertTrue(detail.getStatisticsAvailable());
        assertEquals(37L, detail.getPostCount());
        assertEquals(Map.of("1", 16L, "2", 12L, "4", 9L), detail.getTypeDistribution());
        verify(tagMapper).countPublicPostsByTag(13L);
        verify(tagMapper).countPublicPostTypesByTag(13L);
    }

    @Test
    void aggregateFailureIsDifferentFromARealEmptyCollection() {
        stubActiveTag(14L);
        when(tagMapper.countPublicPostsByTag(14L)).thenThrow(new IllegalStateException("statistics unavailable"));

        TagDTO detail = facade.getTag(14L);

        assertFalse(detail.getStatisticsAvailable());
        assertEquals(0L, detail.getPostCount());
        assertEquals(Map.of(), detail.getTypeDistribution());
    }

    @Test
    void tagListUsesPublicPostCountInsteadOfHistoricalUsageCount() {
        TagPO java = tag(21L, "Java", 6L);
        TagPO redis = tag(22L, "Redis", 4L);
        when(migrationCheckService.tagGovernanceReady()).thenReturn(true);
        when(tagMapper.selectActiveTags()).thenReturn(List.of(java, redis));
        when(tagMapper.countPublicPostsByTags(List.of(21L, 22L))).thenReturn(List.of(
                Map.of("TAG_ID", "21", "POST_COUNT", 1L),
                Map.of("tagId", 22L, "postCount", 3L)));

        List<TagDTO> tags = facade.listTags();

        assertEquals(List.of(1L, 3L), tags.stream().map(TagDTO::getPostCount).toList());
        assertEquals(List.of(6L, 4L), tags.stream().map(TagDTO::getUseCount).toList());
        assertTrue(tags.stream().allMatch(tag -> Boolean.TRUE.equals(tag.getStatisticsAvailable())));
    }

    @Test
    void tagListTreatsNullAggregateRowsAsAvailableZeroCounts() {
        when(migrationCheckService.tagGovernanceReady()).thenReturn(true);
        when(tagMapper.selectActiveTags()).thenReturn(List.of(tag(23L, "MySQL", 8L)));
        when(tagMapper.countPublicPostsByTags(List.of(23L))).thenReturn(null);

        List<TagDTO> tags = facade.listTags();

        assertEquals(0L, tags.get(0).getPostCount());
        assertTrue(tags.get(0).getStatisticsAvailable());
    }

    private void stubActiveTag(Long tagId) {
        TagPO tag = tag(tagId, "Java", 0L);
        when(migrationCheckService.tagGovernanceReady()).thenReturn(true);
        when(tagMapper.selectActiveByIds(List.of(tagId))).thenReturn(List.of(tag));
    }

    private static TagPO tag(Long tagId, String name, Long useCount) {
        TagPO tag = new TagPO();
        tag.setId(tagId);
        tag.setTagName(name);
        tag.setTagType(1);
        tag.setUseCount(useCount);
        tag.setTagStatus(1);
        tag.setMergeTargetId(null);
        tag.setIsDeleted(0);
        return tag;
    }
}

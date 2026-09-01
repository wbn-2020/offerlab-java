package com.offerlab.community.post.application;

import com.offerlab.community.infra.redis.cache.MultiLevelCache;
import com.offerlab.community.post.api.dto.PostDetailCacheDTO;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostTrustSignalsMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.application.PostApplicationService;
import com.offerlab.community.post.application.PostVersionHistoryService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.user.api.UserFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostFacadeContentEnvironmentTest {

    @Mock
    private PostRepository postRepo;
    @Mock
    private PostMapper postMapper;
    @Mock
    private PostExtensionMapper extensionMapper;
    @Mock
    private PostCounterMapper counterMapper;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private PostTrustSignalsMapper trustSignalsMapper;
    @Mock
    private PostCounterRedis postCounterRedis;
    @Mock
    private PostVersionHistoryService versionHistoryService;
    @Mock
    private MultiLevelCache<PostDetailCacheDTO> multiLevelCache;
    @Mock
    private PostApplicationService postService;
    @Mock
    private UserFacade userFacade;
    @Mock
    private MigrationCheckService migrationCheckService;
    @Mock
    private AdminPermissionService adminPermissionService;

    @InjectMocks
    private PostFacadeImpl facade;

    @Test
    void cachedDetailWithoutContentEnvironmentFailsClosed() {
        PostDetailCacheDTO staleCachedDetail = PostDetailCacheDTO.builder()
                .id(741177956554252288L)
                .postStatus(Post.STATUS_PUBLISHED)
                .visibility(Post.VIS_PUBLIC)
                .build();
        when(multiLevelCache.get(anyString(), any(Function.class), eq(PostDetailCacheDTO.class)))
                .thenReturn(staleCachedDetail);

        assertNull(facade.getPost(741177956554252288L, null));

        verify(multiLevelCache, org.mockito.Mockito.times(2))
                .get(anyString(), any(Function.class), eq(PostDetailCacheDTO.class));
        verify(multiLevelCache).evict(anyString());
        verifyNoInteractions(postRepo, postMapper, extensionMapper, counterMapper, tagMapper,
                postCounterRedis, userFacade);
    }
}

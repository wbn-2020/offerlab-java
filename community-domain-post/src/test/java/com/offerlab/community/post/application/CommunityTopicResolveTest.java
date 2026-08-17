package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicFollowMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicTagMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.user.api.UserFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityTopicResolveTest {

    @Mock private CommunityTopicMapper topicMapper;
    @Mock private CommunityTopicFollowMapper topicFollowMapper;
    @Mock private CommunityTopicTagMapper topicTagMapper;
    @Mock private TagMapper tagMapper;
    @Mock private PostMapper postMapper;
    @Mock private PostExtensionMapper extensionMapper;
    @Mock private PostCounterMapper counterMapper;
    @Mock private UserFacade userFacade;
    @Mock private SnowflakeIdGenerator idGen;
    @Mock private AdminAuditService auditService;
    @Mock private MigrationCheckService migrationCheckService;
    @Mock private PostFacade postFacade;

    @InjectMocks
    private CommunityTopicService service;

    @Test
    void missingTopicResolvesToNullWithoutChangingTheLegacyDetail404Contract() {
        when(migrationCheckService.communityTopicReady()).thenReturn(true);
        when(topicMapper.selectBySlug("does-not-exist")).thenReturn(null);
        when(topicMapper.selectBySlugOrName("does-not-exist", "Does Not Exist")).thenReturn(null);

        assertNull(service.resolvePublic("does-not-exist", null));

        BizException error = assertThrows(BizException.class,
                () -> service.getPublic("does-not-exist", null));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), error.getCode());
    }
}

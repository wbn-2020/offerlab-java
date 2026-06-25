package com.offerlab.community.post.application;

import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.CommunityTopicDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class DefaultContentAssistTaxonomyService implements ContentAssistTaxonomyService {

    private final PostFacade postFacade;
    private final CommunityTopicService communityTopicService;

    @Override
    public List<TagDTO> listTags() {
        return postFacade.listTags();
    }

    @Override
    public List<CommunityTopicDTO> listTopics() {
        return communityTopicService.listPublic(null, 30);
    }
}

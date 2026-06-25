package com.offerlab.community.post.application;

import com.offerlab.community.post.api.dto.CommunityTopicDTO;
import com.offerlab.community.post.api.dto.TagDTO;

import java.util.List;

interface ContentAssistTaxonomyService {

    List<TagDTO> listTags();

    List<CommunityTopicDTO> listTopics();
}

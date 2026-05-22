package com.offerlab.community.search.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.api.dto.PostBriefDTO;

import java.util.List;

public interface SearchFacade {

    PageResult<PostBriefDTO> searchPosts(String keyword, String company, String position,
                                         Integer type, String sort, String cursor, int size);

    List<String> suggest(String prefix, int size);

    List<String> getHotKeywords(int size);
}

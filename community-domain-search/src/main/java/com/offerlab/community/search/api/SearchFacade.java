package com.offerlab.community.search.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.search.api.dto.SearchTrustFilter;

import java.util.List;

public interface SearchFacade {

    PageResult<PostBriefDTO> searchPosts(String keyword, String company, String position,
                                         Integer type, String sort, String cursor, int size);

    PageResult<PostBriefDTO> searchPosts(String keyword, String company, String position,
                                         Integer type, String sort, String cursor, int size,
                                         boolean includeTestData);

    default PageResult<PostBriefDTO> searchPosts(String keyword, String company, String position,
                                                 Integer type, Integer domain, String sort, String cursor, int size) {
        return searchPosts(keyword, company, position, type, sort, cursor, size);
    }

    default PageResult<PostBriefDTO> searchPosts(String keyword, String company, String position,
                                                 Integer type, Integer domain, String sort, String cursor, int size,
                                                 boolean includeTestData) {
        return searchPosts(keyword, company, position, type, sort, cursor, size, includeTestData);
    }

    default PageResult<PostBriefDTO> searchPosts(String keyword, String company, String position,
                                                 Integer type, Integer domain, String sort, String cursor, int size,
                                                 boolean includeTestData, SearchTrustFilter trustFilter) {
        return searchPosts(keyword, company, position, type, domain, sort, cursor, size, includeTestData);
    }

    List<String> suggest(String prefix, int size);

    List<String> getHotKeywords(int size);
}

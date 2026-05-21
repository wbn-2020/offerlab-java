package com.offerlab.community.feed.api.dto;

import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedItemVO {
    private PostBriefDTO post;
    private UserBriefDTO author;
    private PostCounterDTO counter;
    private MyInteraction myInteraction;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MyInteraction {
        private Boolean liked;
        private Boolean favorited;
    }
}

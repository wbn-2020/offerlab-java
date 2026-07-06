package com.offerlab.community.feed.application;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributionGovernanceGuardTest {

    @Test
    void feedAndSearchDistributionMustReuseCurrentVisibilityAndNeutralReasons() throws Exception {
        String feedFacade = read("community-domain-feed/src/main/java/com/offerlab/community/feed/application/FeedFacadeImpl.java");
        String searchFacade = read("community-domain-search/src/main/java/com/offerlab/community/search/application/SearchFacadeImpl.java");
        String publicFilter = read("community-domain-post/src/main/java/com/offerlab/community/post/api/PublicContentFilter.java");

        assertTrue(publicFilter.contains("isDistributablePost"),
                "PublicContentFilter must expose a shared distribution eligibility guard.");
        assertTrue(publicFilter.contains("isUnsafeSuggestionText"),
                "PublicContentFilter must expose a shared unsafe search suggestion guard.");
        assertTrue(publicFilter.contains("广告") && publicFilter.contains("支付") && publicFilter.contains("会员")
                        && publicFilter.contains("隐私画像") && publicFilter.contains("AI 精准")
                        && publicFilter.contains("专家建议") && publicFilter.contains("权威推荐"),
                "Shared backend guard must block commercial, private-profile, AI-precision, and endorsement wording.");

        assertTrue(feedFacade.contains("PublicContentFilter.isDistributablePost"),
                "Recommend, latest, hot, and cross-domain feed entries must reuse current distribution visibility.");
        assertTrue(feedFacade.contains("filterDistributablePosts"),
                "FeedFacadeImpl must filter candidate pools through one distribution helper.");
        assertTrue(feedFacade.contains("neutralRecommendationReason"),
                "FeedFacadeImpl must normalize unsafe or high-risk recommendation reasons.");
        assertTrue(feedFacade.contains("isHighRiskDomain"),
                "High-risk channel recommendations must use neutral wording.");

        assertTrue(searchFacade.contains("PublicContentFilter.isUnsafeSuggestionText"),
                "Search suggestions and hot keywords must filter unsafe commercial, privacy, and endorsement terms.");
        assertTrue(searchFacade.contains("visibleSuggestionSources"),
                "Search suggestions must still check current post visibility before rendering.");
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("..").resolve(relative).normalize());
    }
}

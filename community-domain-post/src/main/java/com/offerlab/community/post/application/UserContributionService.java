package com.offerlab.community.post.application;

import com.offerlab.community.post.api.dto.UserContributionDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.user.api.UserFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserContributionService {

    private static final String SOURCE_BACKEND_AGGREGATE = "backend_aggregate";
    private static final String SOURCE_PROFILE_RESTRICTED = "profile_restricted";

    private final PostMapper postMapper;
    private final UserFacade userFacade;

    public UserContributionDTO getContribution(Long uid, Long viewerUid) {
        if (uid == null || uid <= 0) {
            return empty(uid, SOURCE_BACKEND_AGGREGATE, true);
        }
        boolean profileVisible = userFacade.isProfileVisible(viewerUid, uid);
        if (!profileVisible) {
            UserContributionDTO restricted = empty(uid, SOURCE_PROFILE_RESTRICTED, false);
            restricted.setProfileVisible(false);
            return restricted;
        }

        Map<String, Object> row = postMapper.aggregatePublicContributionByAuthor(uid);
        long postCount = asLong(row, "postCount");
        long featuredCount = asLong(row, "featuredCount");
        long likeCount = asLong(row, "likeCount");
        long favoriteCount = asLong(row, "favoriteCount");
        long commentCount = asLong(row, "commentCount");
        long viewCount = asLong(row, "viewCount");
        int score = score(postCount, featuredCount, likeCount, favoriteCount, commentCount);

        return UserContributionDTO.builder()
                .uid(uid)
                .score(score)
                .level(level(score))
                .badge(badge(score, featuredCount))
                .postCount(postCount)
                .featuredCount(featuredCount)
                .likeCount(likeCount)
                .favoriteCount(favoriteCount)
                .commentCount(commentCount)
                .viewCount(viewCount)
                .source(SOURCE_BACKEND_AGGREGATE)
                .estimated(false)
                .profileVisible(true)
                .build();
    }

    private static UserContributionDTO empty(Long uid, String source, boolean visible) {
        return UserContributionDTO.builder()
                .uid(uid)
                .score(0)
                .level(level(0))
                .badge(badge(0, 0))
                .postCount(0L)
                .featuredCount(0L)
                .likeCount(0L)
                .favoriteCount(0L)
                .commentCount(0L)
                .viewCount(0L)
                .source(source)
                .estimated(false)
                .profileVisible(visible)
                .build();
    }

    private static int score(long postCount, long featuredCount, long likeCount, long favoriteCount, long commentCount) {
        long raw = postCount * 10 + featuredCount * 30 + likeCount + favoriteCount * 2 + commentCount * 2;
        return raw > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, raw);
    }

    private static String level(int score) {
        if (score >= 500) return "L5 社区专家";
        if (score >= 240) return "L4 深度作者";
        if (score >= 120) return "L3 经验沉淀者";
        if (score >= 40) return "L2 活跃分享者";
        return "L1 新作者";
    }

    private static String badge(int score, long featuredCount) {
        if (featuredCount >= 5) return "精选作者";
        if (score >= 240) return "高质量贡献者";
        if (score >= 120) return "持续输出";
        if (score >= 40) return "社区新星";
        return "开始沉淀";
    }

    private static long asLong(Map<String, Object> row, String key) {
        if (row == null || !row.containsKey(key)) {
            return 0L;
        }
        Object value = row.get(key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.longValue();
        }
        if (value instanceof BigInteger integer) {
            return integer.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}

package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.PublicUpdateResourceFacade;
import com.offerlab.community.post.api.dto.CommunityTopicDTO;
import com.offerlab.community.post.api.dto.ContentSeriesDTO;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PublicUpdateResourceDTO;
import com.offerlab.community.post.collaboration.api.CollaborationModels.NeedDTO;
import com.offerlab.community.post.collaboration.application.CollaborationService;
import com.offerlab.community.post.infrastructure.persistence.CommunitySpaceRows;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunitySpaceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicUpdateResourceService implements PublicUpdateResourceFacade {

    private final PostFacade postFacade;
    private final CommunityTopicService topicService;
    private final ContentSeriesService contentSeriesService;
    private final CollaborationService collaborationService;
    private final CommunitySpaceMapper spaceMapper;

    @Override
    public PublicUpdateResourceDTO resolvePublic(String sourceType,
                                                  String sourceId,
                                                  Long fallbackPostId,
                                                  String requestedPath) {
        String normalizedType = normalizeType(sourceType);
        try {
            return switch (normalizedType) {
                case "TOPIC" -> resolveTopic(sourceId, fallbackPostId);
                case "SERIES" -> resolveCollaborationSeries(sourceId, fallbackPostId);
                case "COLLECTION" -> resolveCollection(sourceId, fallbackPostId);
                case "NEED" -> resolveNeed(sourceId);
                case "POST", "DISCUSSION" -> resolvePost(firstPositive(parseLong(sourceId), fallbackPostId));
                default -> resolvePost(firstPositive(fallbackPostId, postIdFromPath(requestedPath)));
            };
        } catch (BizException e) {
            return null;
        } catch (RuntimeException e) {
            log.warn("public update resource revalidation failed, sourceType={} sourceId={}",
                    normalizedType, sourceId, e);
            return null;
        }
    }

    private PublicUpdateResourceDTO resolveTopic(String sourceId, Long fallbackPostId) {
        if (!StringUtils.hasText(sourceId)) {
            return null;
        }
        String topicSlug = sourceId.trim();
        Long topicId = parseLong(topicSlug);
        if (topicId != null) {
            topicSlug = spaceMapper.selectPublicTopicSlug(topicId);
            if (!StringUtils.hasText(topicSlug)) {
                return null;
            }
        }
        CommunityTopicDTO topic = topicService.getPublic(topicSlug, null);
        if (fallbackPostId != null
                && spaceMapper.publicTopicContainsPost(topic.getSlug(), fallbackPostId) <= 0) {
            return null;
        }
        return PublicUpdateResourceDTO.builder()
                .sourceType("TOPIC")
                .sourceId(topic.getSlug())
                .title(topic.getName())
                .canonicalPath("/topics/" + encodePath(topic.getSlug()))
                .updatedAt(topic.getUpdateTime())
                .postId(fallbackPostId)
                .build();
    }

    private PublicUpdateResourceDTO resolveCollection(String sourceId, Long fallbackPostId) {
        Long seriesId = parseLong(sourceId);
        if (seriesId == null) {
            return null;
        }
        ContentSeriesDTO series = contentSeriesService.getPublicDetail(seriesId);
        if (fallbackPostId != null
                && spaceMapper.publicContentSeriesContainsPost(seriesId, fallbackPostId) <= 0) {
            return null;
        }
        return PublicUpdateResourceDTO.builder()
                .sourceType("COLLECTION")
                .sourceId(String.valueOf(series.getId()))
                .title(series.getTitle())
                .canonicalPath("/collections/" + series.getId())
                .updatedAt(series.getUpdateTime())
                .postId(fallbackPostId)
                .build();
    }

    private PublicUpdateResourceDTO resolveCollaborationSeries(String sourceId,
                                                                Long fallbackPostId) {
        Long seriesId = parseLong(sourceId);
        if (seriesId == null) {
            return null;
        }
        CommunitySpaceRows.CollaborationSeriesRow series =
                spaceMapper.selectPublicCollaborationSeries(seriesId);
        if (series == null) {
            return null;
        }
        if (fallbackPostId != null
                && spaceMapper.publicCollaborationSeriesContainsPost(
                        seriesId, fallbackPostId) <= 0) {
            return null;
        }
        return PublicUpdateResourceDTO.builder()
                .sourceType("SERIES")
                .sourceId(String.valueOf(series.getId()))
                .title(series.getTitle())
                .canonicalPath("/collaboration/series/" + series.getId())
                .updatedAt(series.getUpdateTime())
                .postId(fallbackPostId)
                .build();
    }

    private PublicUpdateResourceDTO resolveNeed(String sourceId) {
        Long needId = parseLong(sourceId);
        if (needId == null) {
            return null;
        }
        NeedDTO need = collaborationService.getNeed(needId, null);
        if (need == null) {
            return null;
        }
        Long publicResolutionPostId = need.getResolutionPostId();
        if (publicResolutionPostId != null && resolvePost(publicResolutionPostId) == null) {
            publicResolutionPostId = null;
        }
        return PublicUpdateResourceDTO.builder()
                .sourceType("NEED")
                .sourceId(String.valueOf(need.getId()))
                .title(need.getTitle())
                .canonicalPath("/collaboration/needs/" + need.getId())
                .updatedAt(need.getUpdateTime())
                .postId(publicResolutionPostId)
                .build();
    }

    private PublicUpdateResourceDTO resolvePost(Long postId) {
        if (postId == null || postId <= 0) {
            return null;
        }
        PostDTO post = postFacade.getPost(postId, null);
        if (post == null) {
            return null;
        }
        return PublicUpdateResourceDTO.builder()
                .sourceType("POST")
                .sourceId(String.valueOf(post.getId()))
                .title(post.getTitle())
                .canonicalPath("/post/" + post.getId())
                .updatedAt(post.getUpdateTime())
                .postId(post.getId())
                .build();
    }

    private static String normalizeType(String sourceType) {
        return StringUtils.hasText(sourceType)
                ? sourceType.trim().toUpperCase(Locale.ROOT)
                : "POST";
    }

    private static Long postIdFromPath(String path) {
        if (!StringUtils.hasText(path) || !path.startsWith("/post/")) {
            return null;
        }
        String id = path.substring("/post/".length()).split("[?#]", 2)[0];
        return parseLong(id);
    }

    private static Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long firstPositive(Long first, Long second) {
        if (first != null && first > 0) {
            return first;
        }
        return second != null && second > 0 ? second : null;
    }

    private static String encodePath(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}

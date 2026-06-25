package com.offerlab.community.post.application;

import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.KnowledgeRelationEdgeDTO;
import com.offerlab.community.post.api.dto.KnowledgeRelationGraphDTO;
import com.offerlab.community.post.api.dto.KnowledgeRelationNodeDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicTagMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentSeriesMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentSeriesPostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.po.CommunityTopicPO;
import com.offerlab.community.post.infrastructure.persistence.po.ContentSeriesPO;
import com.offerlab.community.post.infrastructure.persistence.po.ContentSeriesPostPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeRelationServiceTest {

    @Test
    void domainSeedBuildsLightweightPublicGraphWithoutSeriesMetadata() {
        KnowledgeRelationService service = new KnowledgeRelationService(
                postFacade(),
                postMapper(),
                topicMapper(),
                topicTagMapper(),
                seriesMapper(),
                seriesPostMapper()
        );

        KnowledgeRelationGraphDTO graph = service.explore(null, null, null, 1, null, 8);

        assertEquals(8, graph.getLimit());
        assertTrue(graph.getNodes().stream().map(KnowledgeRelationNodeDTO::getKey).toList().contains("domain:1"));
        assertTrue(graph.getNodes().stream().map(KnowledgeRelationNodeDTO::getKey).toList().contains("post:101"));
        assertTrue(graph.getNodes().stream().map(KnowledgeRelationNodeDTO::getKey).toList().contains("tag:201"));
        assertTrue(graph.getNodes().stream().map(KnowledgeRelationNodeDTO::getKey).toList().contains("topic:301"));
        assertTrue(graph.getEdges().stream().map(KnowledgeRelationEdgeDTO::getRelation).toList().contains("domain_post"));
        assertTrue(graph.getEdges().stream().map(KnowledgeRelationEdgeDTO::getRelation).toList().contains("post_tag"));
        assertTrue(graph.getEdges().stream().map(KnowledgeRelationEdgeDTO::getRelation).toList().contains("topic_tag"));
        assertTrue(graph.getNodes().stream().noneMatch(node -> "series".equals(node.getType())));
        assertTrue(graph.getEdges().stream().noneMatch(edge -> "series_post".equals(edge.getRelation())));
    }

    private static PostFacade postFacade() {
        return (PostFacade) Proxy.newProxyInstance(
                PostFacade.class.getClassLoader(),
                new Class<?>[]{PostFacade.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "batchGetPosts" -> postsById((Collection<Long>) args[0]);
                    case "toString" -> "PostFacadeStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static PostMapper postMapper() {
        return (PostMapper) Proxy.newProxyInstance(
                PostMapper.class.getClassLoader(),
                new Class<?>[]{PostMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectPublicPosts" -> List.of(post(101L), post(102L));
                    case "toString" -> "PostMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static CommunityTopicMapper topicMapper() {
        return (CommunityTopicMapper) Proxy.newProxyInstance(
                CommunityTopicMapper.class.getClassLoader(),
                new Class<?>[]{CommunityTopicMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectOnlineTopicsByTagIds" -> List.of(topic(301L, "backend-infra", "Backend Infra"));
                    case "toString" -> "CommunityTopicMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static CommunityTopicTagMapper topicTagMapper() {
        return (CommunityTopicTagMapper) Proxy.newProxyInstance(
                CommunityTopicTagMapper.class.getClassLoader(),
                new Class<?>[]{CommunityTopicTagMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectTagsByTopicId" -> List.of(tag(201L, "Java"), tag(202L, "Spring Cloud"));
                    case "toString" -> "CommunityTopicTagMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static ContentSeriesMapper seriesMapper() {
        return (ContentSeriesMapper) Proxy.newProxyInstance(
                ContentSeriesMapper.class.getClassLoader(),
                new Class<?>[]{ContentSeriesMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> 1;
                    case "selectByPostIds" -> List.of(series(401L, "Java Growth Path", 1));
                    case "toString" -> "ContentSeriesMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static ContentSeriesPostMapper seriesPostMapper() {
        return (ContentSeriesPostMapper) Proxy.newProxyInstance(
                ContentSeriesPostMapper.class.getClassLoader(),
                new Class<?>[]{ContentSeriesPostMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> 1;
                    case "selectActiveByPostIds" -> List.of(seriesPost(501L, 401L, 101L));
                    case "toString" -> "ContentSeriesPostMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static PostPO post(Long id) {
        PostPO post = new PostPO();
        post.setId(id);
        post.setAuthorId(7L);
        post.setTitle("post-" + id);
        post.setCreateTime(LocalDateTime.now());
        post.setUpdateTime(post.getCreateTime());
        return post;
    }

    private static CommunityTopicPO topic(Long id, String slug, String name) {
        CommunityTopicPO topic = new CommunityTopicPO();
        topic.setId(id);
        topic.setSlug(slug);
        topic.setTopicName(name);
        topic.setTopicStatus(1);
        return topic;
    }

    private static TagPO tag(Long id, String name) {
        TagPO tag = new TagPO();
        tag.setId(id);
        tag.setTagName(name);
        tag.setTagStatus(1);
        return tag;
    }

    private static ContentSeriesPO series(Long id, String title, Integer domain) {
        ContentSeriesPO po = new ContentSeriesPO();
        po.setId(id);
        po.setTitle(title);
        po.setDomain(domain);
        po.setCreatorUid(7L);
        return po;
    }

    private static ContentSeriesPostPO seriesPost(Long id, Long seriesId, Long postId) {
        ContentSeriesPostPO po = new ContentSeriesPostPO();
        po.setId(id);
        po.setSeriesId(seriesId);
        po.setPostId(postId);
        return po;
    }

    private static Map<Long, PostBriefDTO> postsById(Collection<Long> postIds) {
        return postIds.stream()
                .filter(id -> id == 101L || id == 102L)
                .collect(java.util.stream.Collectors.toMap(
                        id -> id,
                        KnowledgeRelationServiceTest::postBrief,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
    }

    private static PostBriefDTO postBrief(Long id) {
        if (id == 101L) {
            return PostBriefDTO.builder()
                    .id(101L)
                    .title("Java interview notes")
                    .domain(1)
                    .tags(List.of(TagDTO.builder().id(201L).name("Java").build()))
                    .createTime(LocalDateTime.now())
                    .build();
        }
        return PostBriefDTO.builder()
                .id(102L)
                .title("Spring Cloud guide")
                .domain(1)
                .tags(List.of(TagDTO.builder().id(202L).name("Spring Cloud").build()))
                .createTime(LocalDateTime.now())
                .build();
    }
}

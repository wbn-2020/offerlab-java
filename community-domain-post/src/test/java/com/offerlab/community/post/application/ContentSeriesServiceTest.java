package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.dto.ContentSeriesAddPostCmd;
import com.offerlab.community.post.api.dto.ContentSeriesCreateCmd;
import com.offerlab.community.post.api.dto.ContentSeriesDTO;
import com.offerlab.community.post.api.dto.ContentSeriesUpdateCmd;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentSeriesMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentSeriesPostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ContentSeriesPO;
import com.offerlab.community.post.infrastructure.persistence.po.ContentSeriesPostPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentSeriesServiceTest {

    @Test
    void readFallsBackToEmptyListWhenSeriesSchemaIsNotReady() {
        ContentSeriesService service = newService(new SeriesMapperState(0), new SeriesPostMapperState(), new LinkedHashMap<>());

        assertEquals(List.of(), service.listMine(7L));
    }

    @Test
    void writeFailsWithMigrationHintWhenSeriesSchemaIsNotReady() {
        ContentSeriesService service = newService(new SeriesMapperState(0), new SeriesPostMapperState(), new LinkedHashMap<>());

        BizException ex = assertThrows(BizException.class, () -> service.create(createCmd("未迁移系列", 1), 7L));

        assertEquals(ErrorCode.DEPENDENCY_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("db/migration/20260624_content_series.sql"));
    }

    @Test
    void createAndUpdateKeepSeriesOwnedByCreator() {
        SeriesMapperState seriesState = new SeriesMapperState(1);
        ContentSeriesService service = newService(seriesState, new SeriesPostMapperState(), new LinkedHashMap<>());

        ContentSeriesDTO created = service.create(createCmd("阶段3系列", 2), 7L);
        Long seriesId = created.getId();

        assertEquals(7L, created.getCreatorUid());
        assertEquals("阶段3系列", created.getTitle());
        assertEquals(2, created.getDomain());

        ContentSeriesDTO updated = service.update(seriesId, updateCmd("阶段3系列-更新", 1, "改成技术领域"), 7L);

        assertEquals("阶段3系列-更新", updated.getTitle());
        assertEquals(1, updated.getDomain());
        assertEquals("改成技术领域", updated.getDescription());

        BizException forbidden = assertThrows(BizException.class,
                () -> service.update(seriesId, updateCmd("越权修改", 1, "forbidden"), 8L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), forbidden.getCode());
    }

    @Test
    void addPostRequiresSeriesOwnerAndPostAuthorAndReturnsProgress() {
        SeriesMapperState seriesState = new SeriesMapperState(1);
        SeriesPostMapperState relationState = new SeriesPostMapperState();
        Map<Long, PostPO> posts = new LinkedHashMap<>();
        ContentSeriesService service = newService(seriesState, relationState, posts);

        ContentSeriesDTO created = service.create(createCmd("求职内容系列", 2), 7L);
        Long seriesId = created.getId();

        posts.put(9001L, post(9001L, 8L, Post.STATUS_PUBLISHED));
        BizException forbidden = assertThrows(BizException.class,
                () -> service.addPost(seriesId, addPostCmd(9001L), 7L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), forbidden.getCode());

        posts.put(9002L, post(9002L, 7L, Post.STATUS_PUBLISHED));
        ContentSeriesDTO afterAdd = service.addPost(seriesId, addPostCmd(9002L), 7L);

        assertEquals(1L, afterAdd.getProgress().getPublishedPostCount());
        assertEquals(1L, afterAdd.getProgress().getTotalPostCount());
        assertEquals(100, afterAdd.getProgress().getCompletionRate());
        assertEquals(1, relationState.activeLinks(seriesId).size());
        assertEquals(0, relationState.activeLinks(seriesId).get(0).getSortOrder());
    }

    private static ContentSeriesService newService(SeriesMapperState seriesState,
                                                   SeriesPostMapperState relationState,
                                                   Map<Long, PostPO> posts) {
        return new ContentSeriesService(
                seriesMapper(seriesState, relationState, posts),
                seriesPostMapper(relationState),
                postMapper(posts),
                new SnowflakeIdGenerator()
        );
    }

    private static ContentSeriesMapper seriesMapper(SeriesMapperState seriesState,
                                                    SeriesPostMapperState relationState,
                                                    Map<Long, PostPO> posts) {
        return (ContentSeriesMapper) Proxy.newProxyInstance(
                ContentSeriesMapper.class.getClassLoader(),
                new Class<?>[]{ContentSeriesMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> seriesState.tableExists;
                    case "selectMine" -> seriesState.selectMine((Long) args[0]);
                    case "selectProgressBySeriesIds" -> progressRows((Collection<Long>) args[0], relationState, posts);
                    case "insert" -> {
                        ContentSeriesPO po = (ContentSeriesPO) args[0];
                        seriesState.seriesById.put(po.getId(), po);
                        yield 1;
                    }
                    case "touchSeries" -> {
                        ContentSeriesPO po = seriesState.seriesById.get((Long) args[0]);
                        if (po != null) {
                            po.setUpdateTime(LocalDateTime.now());
                            seriesState.seriesById.put(po.getId(), po);
                            yield 1;
                        }
                        yield 0;
                    }
                    case "selectById" -> seriesState.seriesById.get((Long) args[0]);
                    case "updateById" -> {
                        ContentSeriesPO po = (ContentSeriesPO) args[0];
                        seriesState.seriesById.put(po.getId(), po);
                        yield 1;
                    }
                    case "toString" -> "ContentSeriesMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static ContentSeriesPostMapper seriesPostMapper(SeriesPostMapperState relationState) {
        return (ContentSeriesPostMapper) Proxy.newProxyInstance(
                ContentSeriesPostMapper.class.getClassLoader(),
                new Class<?>[]{ContentSeriesPostMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> relationState.tableExists;
                    case "existsActiveRelation" -> relationState.exists((Long) args[0], (Long) args[1]) ? 1 : 0;
                    case "selectMaxSortOrder" -> relationState.maxSortOrder((Long) args[0]);
                    case "insert" -> {
                        relationState.links.add((ContentSeriesPostPO) args[0]);
                        yield 1;
                    }
                    case "toString" -> "ContentSeriesPostMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static PostMapper postMapper(Map<Long, PostPO> posts) {
        return (PostMapper) Proxy.newProxyInstance(
                PostMapper.class.getClassLoader(),
                new Class<?>[]{PostMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectById" -> posts.get((Long) args[0]);
                    case "toString" -> "PostMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static List<Map<String, Object>> progressRows(Collection<Long> seriesIds,
                                                          SeriesPostMapperState relationState,
                                                          Map<Long, PostPO> posts) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long seriesId : seriesIds) {
            long total = 0L;
            long published = 0L;
            for (ContentSeriesPostPO link : relationState.activeLinks(seriesId)) {
                PostPO post = posts.get(link.getPostId());
                if (post != null) {
                    total++;
                    if (Objects.equals(post.getPostStatus(), Post.STATUS_PUBLISHED)) {
                        published++;
                    }
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seriesId", seriesId);
            row.put("publishedPostCount", published);
            row.put("totalPostCount", total);
            rows.add(row);
        }
        return rows;
    }

    private static ContentSeriesCreateCmd createCmd(String title, Integer domain) {
        ContentSeriesCreateCmd cmd = new ContentSeriesCreateCmd();
        cmd.setTitle(title);
        cmd.setDescription("description");
        cmd.setDomain(domain);
        return cmd;
    }

    private static ContentSeriesUpdateCmd updateCmd(String title, Integer domain, String description) {
        ContentSeriesUpdateCmd cmd = new ContentSeriesUpdateCmd();
        cmd.setTitle(title);
        cmd.setDescription(description);
        cmd.setDomain(domain);
        return cmd;
    }

    private static ContentSeriesAddPostCmd addPostCmd(Long postId) {
        ContentSeriesAddPostCmd cmd = new ContentSeriesAddPostCmd();
        cmd.setPostId(postId);
        return cmd;
    }

    private static PostPO post(Long id, Long authorId, Integer status) {
        PostPO po = new PostPO();
        po.setId(id);
        po.setAuthorId(authorId);
        po.setPostStatus(status);
        po.setCreateTime(LocalDateTime.now());
        po.setUpdateTime(LocalDateTime.now());
        return po;
    }

    private static final class SeriesMapperState {
        private final int tableExists;
        private final Map<Long, ContentSeriesPO> seriesById = new LinkedHashMap<>();

        private SeriesMapperState(int tableExists) {
            this.tableExists = tableExists;
        }

        private List<ContentSeriesPO> selectMine(Long creatorUid) {
            return seriesById.values().stream()
                    .filter(series -> Objects.equals(series.getCreatorUid(), creatorUid))
                    .toList();
        }
    }

    private static final class SeriesPostMapperState {
        private final int tableExists = 1;
        private final List<ContentSeriesPostPO> links = new ArrayList<>();

        private boolean exists(Long seriesId, Long postId) {
            return activeLinks(seriesId).stream().anyMatch(link -> Objects.equals(link.getPostId(), postId));
        }

        private Integer maxSortOrder(Long seriesId) {
            return activeLinks(seriesId).stream()
                    .map(ContentSeriesPostPO::getSortOrder)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(null);
        }

        private List<ContentSeriesPostPO> activeLinks(Long seriesId) {
            return links.stream()
                    .filter(link -> Objects.equals(link.getSeriesId(), seriesId))
                    .filter(link -> !Objects.equals(link.getIsDeleted(), 1))
                    .toList();
        }
    }
}

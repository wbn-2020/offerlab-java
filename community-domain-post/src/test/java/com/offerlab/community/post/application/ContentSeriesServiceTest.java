package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.ContentSeriesAddPostCmd;
import com.offerlab.community.post.api.dto.ContentSeriesCreateCmd;
import com.offerlab.community.post.api.dto.ContentSeriesDTO;
import com.offerlab.community.post.api.dto.ContentSeriesUpdateCmd;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostUpdateCmd;
import com.offerlab.community.post.api.dto.PostVersionHistoryDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentSeriesMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentSeriesPostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ContentSeriesPO;
import com.offerlab.community.post.infrastructure.persistence.po.ContentSeriesPostPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
        ContentSeriesService service = newService(
                new SeriesMapperState(1),
                new SeriesPostMapperState(),
                new LinkedHashMap<>(),
                new MigrationCheckStub(false));

        assertEquals(List.of(), service.listMine(7L));
    }

    @Test
    void writeFailsWithMigrationHintWhenSeriesSchemaIsNotReady() {
        ContentSeriesService service = newService(
                new SeriesMapperState(1),
                new SeriesPostMapperState(),
                new LinkedHashMap<>(),
                new MigrationCheckStub(false));

        BizException ex = assertThrows(BizException.class, () -> service.create(createCmd("未迁移系列", 1), 7L));

        assertEquals(ErrorCode.DEPENDENCY_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("db/migration/20260624_content_series.sql"));
    }

    @Test
    void writeFailsClosedWhenReadinessIsBlockedEvenIfTablesExist() {
        SeriesMapperState seriesState = new SeriesMapperState(1);
        SeriesPostMapperState relationState = new SeriesPostMapperState();
        ContentSeriesService service = newService(seriesState, relationState, new LinkedHashMap<>(), new MigrationCheckStub(false));

        BizException ex = assertThrows(BizException.class, () -> service.create(createCmd("半迁移系列", 1), 7L));

        assertEquals(ErrorCode.DEPENDENCY_ERROR.getCode(), ex.getCode());
        assertTrue(seriesState.seriesById.isEmpty());
        assertTrue(relationState.links.isEmpty());
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
        assertEquals(2, created.getVisibility());

        ContentSeriesDTO updated = service.update(seriesId, updateCmd("阶段3系列-更新", 1, "改成技术领域", 2), 7L);

        assertEquals("阶段3系列-更新", updated.getTitle());
        assertEquals(1, updated.getDomain());
        assertEquals(2, updated.getVisibility());
        assertEquals("改成技术领域", updated.getDescription());

        BizException forbidden = assertThrows(BizException.class,
                () -> service.update(seriesId, updateCmd("越权修改", 1, "forbidden"), 8L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), forbidden.getCode());
    }

    @Test
    void publicSeriesMustBeExplicitlyOptedIn() {
        SeriesMapperState seriesState = new SeriesMapperState(1);
        ContentSeriesService service = newService(seriesState, new SeriesPostMapperState(), new LinkedHashMap<>());

        ContentSeriesDTO defaultSeries = service.create(createCmd("默认私密合集", 1), 7L);

        assertEquals(2, defaultSeries.getVisibility());
        BizException hidden = assertThrows(BizException.class, () -> service.getPublicDetail(defaultSeries.getId()));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), hidden.getCode());
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

    @Test
    void addPostRejectsNonPublicUnpublishedOrDeletedPostsBeforeLinking() {
        SeriesMapperState seriesState = new SeriesMapperState(1);
        SeriesPostMapperState relationState = new SeriesPostMapperState();
        Map<Long, PostPO> posts = new LinkedHashMap<>();
        ContentSeriesService service = newService(seriesState, relationState, posts);

        ContentSeriesDTO created = service.create(createCmd("public-safe-series", 1, 1), 7L);
        Long seriesId = created.getId();

        posts.put(9101L, post(9101L, 7L, Post.STATUS_PUBLISHED, Post.VIS_SELF, Post.TYPE_NOTE, "private", "private"));
        posts.put(9102L, post(9102L, 7L, Post.STATUS_DRAFT, Post.VIS_PUBLIC, Post.TYPE_NOTE, "draft", "draft"));
        posts.put(9103L, deletedPost(9103L, 7L));

        BizException privatePost = assertThrows(BizException.class,
                () -> service.addPost(seriesId, addPostCmd(9101L), 7L));
        BizException draftPost = assertThrows(BizException.class,
                () -> service.addPost(seriesId, addPostCmd(9102L), 7L));
        BizException deletedPost = assertThrows(BizException.class,
                () -> service.addPost(seriesId, addPostCmd(9103L), 7L));

        assertEquals(ErrorCode.INVALID_STATUS.getCode(), privatePost.getCode());
        assertEquals(ErrorCode.INVALID_STATUS.getCode(), draftPost.getCode());
        assertEquals(ErrorCode.INVALID_STATUS.getCode(), deletedPost.getCode());
        assertTrue(relationState.activeLinks(seriesId).isEmpty());
    }

    @Test
    void publicDetailAndUserListOnlyExposePublicSeries() {
        SeriesMapperState seriesState = new SeriesMapperState(1);
        ContentSeriesService service = newService(seriesState, new SeriesPostMapperState(), new LinkedHashMap<>());

        ContentSeriesDTO publicSeries = service.create(createCmd("公开合集", 1, 1), 7L);
        ContentSeriesDTO privateSeries = service.create(createCmd("私密合集", 1, 2), 7L);

        assertEquals("公开合集", service.getPublicDetail(publicSeries.getId()).getTitle());
        BizException hidden = assertThrows(BizException.class, () -> service.getPublicDetail(privateSeries.getId()));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), hidden.getCode());

        List<ContentSeriesDTO> visible = service.listPublicByUser(7L, 0, 10);
        assertEquals(1, visible.size());
        assertEquals(publicSeries.getId(), visible.get(0).getId());
    }

    @Test
    void removePostRequiresSeriesOwnerAndSoftDeletesRelation() {
        SeriesMapperState seriesState = new SeriesMapperState(1);
        SeriesPostMapperState relationState = new SeriesPostMapperState();
        Map<Long, PostPO> posts = new LinkedHashMap<>();
        ContentSeriesService service = newService(seriesState, relationState, posts);

        ContentSeriesDTO created = service.create(createCmd("可整理合集", 2), 7L);
        posts.put(9002L, post(9002L, 7L, Post.STATUS_PUBLISHED));
        service.addPost(created.getId(), addPostCmd(9002L), 7L);

        BizException forbidden = assertThrows(BizException.class,
                () -> service.removePost(created.getId(), 9002L, 8L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), forbidden.getCode());

        ContentSeriesDTO afterRemove = service.removePost(created.getId(), 9002L, 7L);

        assertEquals(0L, afterRemove.getProgress().getTotalPostCount());
        assertTrue(relationState.activeLinks(created.getId()).isEmpty());
    }

    @Test
    void removedPostCanBeAddedBackToTheSameSeries() {
        SeriesMapperState seriesState = new SeriesMapperState(1);
        SeriesPostMapperState relationState = new SeriesPostMapperState();
        Map<Long, PostPO> posts = new LinkedHashMap<>();
        ContentSeriesService service = newService(seriesState, relationState, posts);

        ContentSeriesDTO created = service.create(createCmd("可重新整理合集", 2, 1), 7L);
        posts.put(9002L, post(9002L, 7L, Post.STATUS_PUBLISHED));

        service.addPost(created.getId(), addPostCmd(9002L), 7L);
        service.removePost(created.getId(), 9002L, 7L);
        ContentSeriesDTO afterAddBack = service.addPost(created.getId(), addPostCmd(9002L), 7L);

        assertEquals(1L, afterAddBack.getProgress().getTotalPostCount());
        assertEquals(1, relationState.links.size());
        assertEquals(0, relationState.links.get(0).getIsDeleted());
    }

    @Test
    void publicPostsUseSeriesOrderCursorAndKeepEnrichedBriefs() {
        SeriesMapperState seriesState = new SeriesMapperState(1);
        SeriesPostMapperState relationState = new SeriesPostMapperState();
        Map<Long, PostPO> posts = new LinkedHashMap<>();
        ContentSeriesService service = newService(seriesState, relationState, posts);

        ContentSeriesDTO created = service.create(createCmd("公开分页合集", 1, 1), 7L);
        posts.put(9003L, post(9003L, 7L, Post.STATUS_PUBLISHED, Post.VIS_PUBLIC, Post.TYPE_NOTE, "第三篇", "第三篇正文"));
        posts.put(9002L, post(9002L, 7L, Post.STATUS_PUBLISHED, Post.VIS_PUBLIC, Post.TYPE_RESOURCE, "第二篇", "第二篇正文"));
        posts.put(9001L, post(9001L, 7L, Post.STATUS_PUBLISHED, Post.VIS_PUBLIC, Post.TYPE_BLOG, "第一篇", "第一篇正文"));
        service.addPost(created.getId(), addPostCmd(9003L, 0), 7L);
        service.addPost(created.getId(), addPostCmd(9001L, 1), 7L);
        service.addPost(created.getId(), addPostCmd(9002L, 2), 7L);

        Long firstPageLastRelationId = relationState.activeLinks(created.getId()).stream()
                .filter(link -> Objects.equals(link.getPostId(), 9001L))
                .map(ContentSeriesPostPO::getId)
                .findFirst()
                .orElseThrow();

        PageResult<PostBriefDTO> firstPage = service.listPublicPosts(created.getId(), 0, 2);
        PageResult<PostBriefDTO> secondPage = service.listPublicPosts(created.getId(), Long.parseLong(firstPage.getNextCursor()), 2);

        assertEquals(List.of(9003L, 9001L), firstPage.getItems().stream().map(PostBriefDTO::getId).toList());
        assertEquals(String.valueOf(firstPageLastRelationId), firstPage.getNextCursor());
        assertEquals(7L, firstPage.getItems().get(0).getAuthor().getUid());
        assertEquals(0L, firstPage.getItems().get(0).getCounter().getViewCount());
        assertEquals(1, firstPage.getItems().get(0).getDomain());
        assertEquals(List.of(9002L), secondPage.getItems().stream().map(PostBriefDTO::getId).toList());
    }

    @Test
    void publicProgressHidesPrivateAndUnpublishedPostsWithoutBreakingOwnerProgress() {
        SeriesMapperState seriesState = new SeriesMapperState(1);
        SeriesPostMapperState relationState = new SeriesPostMapperState();
        Map<Long, PostPO> posts = new LinkedHashMap<>();
        ContentSeriesService service = newService(seriesState, relationState, posts);

        ContentSeriesDTO created = service.create(createCmd("公开统计合集", 1, 1), 7L);
        posts.put(9001L, post(9001L, 7L, Post.STATUS_PUBLISHED, Post.VIS_PUBLIC, Post.TYPE_NOTE, "公开内容", "公开正文"));
        posts.put(9002L, post(9002L, 7L, Post.STATUS_PUBLISHED, Post.VIS_SELF, Post.TYPE_NOTE, "私密内容", "私密正文"));
        posts.put(9003L, post(9003L, 7L, Post.STATUS_DRAFT, Post.VIS_PUBLIC, Post.TYPE_NOTE, "草稿内容", "草稿正文"));
        service.addPost(created.getId(), addPostCmd(9001L), 7L);
        relationState.addLegacyLink(created.getId(), 9002L, 1);
        relationState.addLegacyLink(created.getId(), 9003L, 2);

        ContentSeriesDTO owned = service.listMine(7L).stream()
                .filter(series -> Objects.equals(series.getId(), created.getId()))
                .findFirst()
                .orElseThrow();
        ContentSeriesDTO publicDetail = service.getPublicDetail(created.getId());

        assertEquals(3L, owned.getProgress().getTotalPostCount());
        assertEquals(2L, owned.getProgress().getPublishedPostCount());
        assertEquals(1L, publicDetail.getProgress().getTotalPostCount());
        assertEquals(1L, publicDetail.getProgress().getPublishedPostCount());
    }

    @Test
    void publicProgressSqlOnlyCountsPublicPublishedPosts() throws Exception {
        String mapperSource = Files.readString(Path.of(
                "src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/ContentSeriesMapper.java"));

        assertTrue(mapperSource.contains("selectPublicProgressBySeriesIds"));
        assertTrue(mapperSource.contains("p.post_status = 1 AND p.visibility = 1"),
                "public progress must not count private, draft, reviewing, or taken-down posts as public content");
    }

    private static ContentSeriesService newService(SeriesMapperState seriesState,
                                                   SeriesPostMapperState relationState,
                                                   Map<Long, PostPO> posts) {
        return newService(seriesState, relationState, posts, new MigrationCheckStub(true));
    }

    private static ContentSeriesService newService(SeriesMapperState seriesState,
                                                   SeriesPostMapperState relationState,
                                                   Map<Long, PostPO> posts,
                                                   MigrationCheckService migrationCheckService) {
        return new ContentSeriesService(
                seriesMapper(seriesState, relationState, posts),
                seriesPostMapper(relationState),
                postMapper(posts, relationState),
                postFacade(posts),
                new SnowflakeIdGenerator(),
                migrationCheckService,
                new ContentModerationStub()
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
                    case "selectPublicById" -> seriesState.selectPublicById((Long) args[0]);
                    case "selectPublicByCreatorUid" -> seriesState.selectPublicByCreatorUid((Long) args[0], (Long) args[1], (Integer) args[2]);
                    case "selectProgressBySeriesIds" -> progressRows((Collection<Long>) args[0], relationState, posts);
                    case "selectPublicProgressBySeriesIds" -> publicProgressRows((Collection<Long>) args[0], relationState, posts);
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
                    case "restoreDeletedRelation" -> relationState.restore((Long) args[0], (Long) args[1], (Integer) args[2]);
                    case "selectMaxSortOrder" -> relationState.maxSortOrder((Long) args[0]);
                    case "selectActiveRelationId" -> relationState.activeRelationId((Long) args[0], (Long) args[1]);
                    case "insert" -> {
                        relationState.links.add((ContentSeriesPostPO) args[0]);
                        yield 1;
                    }
                    case "softDeleteRelation" -> relationState.softDelete((Long) args[0], (Long) args[1]);
                    case "toString" -> "ContentSeriesPostMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static PostMapper postMapper(Map<Long, PostPO> posts, SeriesPostMapperState relationState) {
        return (PostMapper) Proxy.newProxyInstance(
                PostMapper.class.getClassLoader(),
                new Class<?>[]{PostMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectById" -> posts.get((Long) args[0]);
                    case "selectPublicPostsByContentSeries" -> selectPublicPosts(
                            posts,
                            (Long) args[0],
                            (Long) args[1],
                            (Integer) args[2],
                            relationState);
                    case "toString" -> "PostMapperStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static PostFacade postFacade(Map<Long, PostPO> posts) {
        return (PostFacade) Proxy.newProxyInstance(
                PostFacade.class.getClassLoader(),
                new Class<?>[]{PostFacade.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "batchGetPosts" -> briefMap(posts, (Collection<Long>) args[0]);
                    case "toString" -> "PostFacadeStub";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static List<PostPO> selectPublicPosts(Map<Long, PostPO> posts,
                                                  Long seriesId,
                                                  long cursor,
                                                  int limit,
                                                  SeriesPostMapperState relationState) {
        ContentSeriesPostPO cursorLink = cursor <= 0
                ? null
                : relationState.activeLinks(seriesId).stream()
                .filter(link -> Objects.equals(link.getId(), cursor))
                .findFirst()
                .orElse(null);
        return relationState.activeLinks(seriesId).stream()
                .filter(link -> cursorLink == null || compareSeriesLinks(link, cursorLink) > 0)
                .sorted(ContentSeriesServiceTest::compareSeriesLinks)
                .map(link -> posts.get(link.getPostId()))
                .filter(Objects::nonNull)
                .filter(post -> Objects.equals(post.getPostStatus(), Post.STATUS_PUBLISHED))
                .filter(post -> Objects.equals(post.getVisibility(), Post.VIS_PUBLIC))
                .limit(limit)
                .toList();
    }

    private static int compareSeriesLinks(ContentSeriesPostPO left, ContentSeriesPostPO right) {
        int bySortOrder = Comparator.nullsLast(Integer::compareTo).compare(left.getSortOrder(), right.getSortOrder());
        if (bySortOrder != 0) {
            return bySortOrder;
        }
        return Comparator.nullsLast(Long::compareTo).compare(left.getId(), right.getId());
    }

    private static Map<Long, PostBriefDTO> briefMap(Map<Long, PostPO> posts, Collection<Long> ids) {
        Map<Long, PostBriefDTO> result = new HashMap<>();
        for (Long id : ids) {
            PostPO post = posts.get(id);
            if (post == null) {
                continue;
            }
            result.put(id, PostBriefDTO.builder()
                    .id(post.getId())
                    .authorId(post.getAuthorId())
                    .author(UserBriefDTO.builder().uid(post.getAuthorId()).nickname("用户" + post.getAuthorId()).build())
                    .postType(post.getPostType())
                    .title(post.getTitle())
                    .summary(post.getContent())
                    .domain(1)
                    .counter(PostCounterDTO.builder()
                            .postId(post.getId())
                            .viewCount(0L)
                            .likeCount(0L)
                            .commentCount(0L)
                            .favoriteCount(0L)
                            .build())
                    .createTime(post.getCreateTime())
                    .build());
        }
        return result;
    }

    private static List<Map<String, Object>> progressRows(Collection<Long> seriesIds,
                                                          SeriesPostMapperState relationState,
                                                          Map<Long, PostPO> posts) {
        return progressRows(seriesIds, relationState, posts, false);
    }

    private static List<Map<String, Object>> publicProgressRows(Collection<Long> seriesIds,
                                                                SeriesPostMapperState relationState,
                                                                Map<Long, PostPO> posts) {
        return progressRows(seriesIds, relationState, posts, true);
    }

    private static List<Map<String, Object>> progressRows(Collection<Long> seriesIds,
                                                          SeriesPostMapperState relationState,
                                                          Map<Long, PostPO> posts,
                                                          boolean publicOnly) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long seriesId : seriesIds) {
            long total = 0L;
            long published = 0L;
            for (ContentSeriesPostPO link : relationState.activeLinks(seriesId)) {
                PostPO post = posts.get(link.getPostId());
                if (post != null) {
                    boolean publicPublished = Objects.equals(post.getPostStatus(), Post.STATUS_PUBLISHED)
                            && Objects.equals(post.getVisibility(), Post.VIS_PUBLIC);
                    if (!publicOnly || publicPublished) {
                        total++;
                    }
                    if (Objects.equals(post.getPostStatus(), Post.STATUS_PUBLISHED)
                            && (!publicOnly || Objects.equals(post.getVisibility(), Post.VIS_PUBLIC))) {
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
        return createCmd(title, domain, null);
    }

    private static ContentSeriesCreateCmd createCmd(String title, Integer domain, Integer visibility) {
        ContentSeriesCreateCmd cmd = new ContentSeriesCreateCmd();
        cmd.setTitle(title);
        cmd.setDescription("description");
        cmd.setDomain(domain);
        cmd.setVisibility(visibility);
        return cmd;
    }

    private static ContentSeriesUpdateCmd updateCmd(String title, Integer domain, String description) {
        return updateCmd(title, domain, description, null);
    }

    private static ContentSeriesUpdateCmd updateCmd(String title, Integer domain, String description, Integer visibility) {
        ContentSeriesUpdateCmd cmd = new ContentSeriesUpdateCmd();
        cmd.setTitle(title);
        cmd.setDescription(description);
        cmd.setDomain(domain);
        cmd.setVisibility(visibility);
        return cmd;
    }

    private static ContentSeriesAddPostCmd addPostCmd(Long postId) {
        ContentSeriesAddPostCmd cmd = new ContentSeriesAddPostCmd();
        cmd.setPostId(postId);
        return cmd;
    }

    private static ContentSeriesAddPostCmd addPostCmd(Long postId, Integer sortOrder) {
        ContentSeriesAddPostCmd cmd = addPostCmd(postId);
        cmd.setSortOrder(sortOrder);
        return cmd;
    }

    private static PostPO post(Long id, Long authorId, Integer status) {
        return post(id, authorId, status, Post.VIS_PUBLIC, Post.TYPE_NOTE, "标题" + id, "正文" + id);
    }

    private static PostPO post(Long id, Long authorId, Integer status, Integer visibility, Integer postType, String title, String content) {
        PostPO po = new PostPO();
        po.setId(id);
        po.setAuthorId(authorId);
        po.setPostType(postType);
        po.setTitle(title);
        po.setContent(content);
        po.setVisibility(visibility);
        po.setPostStatus(status);
        po.setCreateTime(LocalDateTime.now());
        po.setUpdateTime(LocalDateTime.now());
        return po;
    }

    private static PostPO deletedPost(Long id, Long authorId) {
        PostPO po = post(id, authorId, Post.STATUS_PUBLISHED);
        po.setIsDeleted(1);
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

        private ContentSeriesPO selectPublicById(Long seriesId) {
            ContentSeriesPO series = seriesById.get(seriesId);
            return series != null && Objects.equals(series.getVisibility(), 1) && !Objects.equals(series.getIsDeleted(), 1)
                    ? series
                    : null;
        }

        private List<ContentSeriesPO> selectPublicByCreatorUid(Long creatorUid, long cursor, int limit) {
            return seriesById.values().stream()
                    .filter(series -> Objects.equals(series.getCreatorUid(), creatorUid))
                    .filter(series -> Objects.equals(series.getVisibility(), 1))
                    .filter(series -> !Objects.equals(series.getIsDeleted(), 1))
                    .filter(series -> cursor <= 0 || series.getId() < cursor)
                    .limit(limit)
                    .toList();
        }
    }

    private static final class SeriesPostMapperState {
        private final int tableExists = 1;
        private final List<ContentSeriesPostPO> links = new ArrayList<>();

        private void addLegacyLink(Long seriesId, Long postId, Integer sortOrder) {
            ContentSeriesPostPO link = new ContentSeriesPostPO();
            link.setId(10_000L + links.size());
            link.setSeriesId(seriesId);
            link.setPostId(postId);
            link.setSortOrder(sortOrder);
            link.setCreateTime(LocalDateTime.now());
            link.setUpdateTime(LocalDateTime.now());
            links.add(link);
        }

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

        private Long activeRelationId(Long seriesId, Long postId) {
            return activeLinks(seriesId).stream()
                    .filter(link -> Objects.equals(link.getPostId(), postId))
                    .map(ContentSeriesPostPO::getId)
                    .findFirst()
                    .orElse(null);
        }

        private int softDelete(Long seriesId, Long postId) {
            int count = 0;
            for (ContentSeriesPostPO link : links) {
                if (Objects.equals(link.getSeriesId(), seriesId)
                        && Objects.equals(link.getPostId(), postId)
                        && !Objects.equals(link.getIsDeleted(), 1)) {
                    link.setIsDeleted(1);
                    count++;
                }
            }
            return count;
        }

        private int restore(Long seriesId, Long postId, Integer sortOrder) {
            for (ContentSeriesPostPO link : links) {
                if (Objects.equals(link.getSeriesId(), seriesId)
                        && Objects.equals(link.getPostId(), postId)
                        && Objects.equals(link.getIsDeleted(), 1)) {
                    link.setIsDeleted(0);
                    link.setSortOrder(sortOrder);
                    return 1;
                }
            }
            return 0;
        }

        private List<ContentSeriesPostPO> activeLinks(Long seriesId) {
            return links.stream()
                    .filter(link -> Objects.equals(link.getSeriesId(), seriesId))
                    .filter(link -> !Objects.equals(link.getIsDeleted(), 1))
                    .toList();
        }
    }

    private static final class MigrationCheckStub extends MigrationCheckService {
        private final boolean contentSeriesReady;

        private MigrationCheckStub(boolean contentSeriesReady) {
            super(null);
            this.contentSeriesReady = contentSeriesReady;
        }

        @Override
        public boolean contentSeriesReady() {
            return contentSeriesReady;
        }
    }

    private static final class ContentModerationStub extends ContentModerationService {
        private ContentModerationStub() {
            super(null, null, null);
        }

        @Override
        public void requireUserCanPublish(Long uid) {
        }

        @Override
        public ModerationDecision checkContent(Long uid, String scope, String sourceType, Long sourceId, String... values) {
            return new ModerationDecision(false, "ALLOW", null, null);
        }
    }
}

package com.offerlab.community.post.application;

import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.OperationCandidateDTO;
import com.offerlab.community.post.api.dto.OperationTopicCmd;
import com.offerlab.community.post.api.dto.OperationTopicCandidateHintCmd;
import com.offerlab.community.post.api.dto.OperationTopicDTO;
import com.offerlab.community.post.api.dto.OperationTopicSectionCmd;
import com.offerlab.community.post.api.dto.OperationTopicSectionDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.OperationCurationItemMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.OperationSlotItemMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.OperationSlotMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.OperationTopicMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.OperationTopicSectionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.po.OperationTopicPO;
import com.offerlab.community.post.infrastructure.persistence.po.OperationTopicSectionPO;
import com.offerlab.community.infra.audit.AdminAuditLogMapper;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationTopicScopeTest {

    @Test
    void topicScopeDerivesFromDomainOnly() {
        assertEquals(OperationCurationService.SCOPE_CROSS_DOMAIN, OperationCurationService.topicScopeOf(null));
        assertEquals(OperationCurationService.SCOPE_DOMAIN, OperationCurationService.topicScopeOf(Post.DOMAIN_TECH));
        assertEquals(OperationCurationService.SCOPE_DOMAIN, OperationCurationService.topicScopeOf(Post.DOMAIN_INVESTMENT));
    }

    @Test
    void scopedTopicAcceptsSameDomainPost() {
        OperationCandidateDTO candidate = receiveHint(topic(Post.DOMAIN_TECH), post(101L, Post.DOMAIN_TECH));
        assertTrue(candidate.getBlockReasons().isEmpty(),
                "same-domain post must pass with no block reasons: " + candidate.getBlockReasons());
        assertEquals(Boolean.TRUE, candidate.getOperable());
    }

    @Test
    void scopedTopicBlocksOffDomainPost() {
        OperationCandidateDTO candidate = receiveHint(topic(Post.DOMAIN_TECH), post(101L, Post.DOMAIN_CAREER));
        assertTrue(candidate.getBlockReasons().contains(
                        OperationCurationService.BLOCK_DOMAIN_MISMATCH_FOR_SCOPED_TOPIC),
                "off-domain post must be blocked for a scoped topic");
        assertFalse(Boolean.TRUE.equals(candidate.getOperable()));
    }

    @Test
    void scopedTopicBlocksUnknownDomainPost() {
        OperationCandidateDTO candidate = receiveHint(topic(Post.DOMAIN_TECH), post(101L, null));
        assertTrue(candidate.getBlockReasons().contains(OperationCurationService.BLOCK_POST_DOMAIN_UNKNOWN),
                "unclassified post must be blocked for a scoped topic");
        assertFalse(Boolean.TRUE.equals(candidate.getOperable()));
    }

    @Test
    void crossDomainTopicAcceptsAnyKnownDomainPost() {
        OperationCandidateDTO candidate = receiveHint(topic(null), post(101L, Post.DOMAIN_CAREER));
        assertTrue(candidate.getBlockReasons().isEmpty(),
                "cross-domain topic must not block known-domain posts: " + candidate.getBlockReasons());
        assertEquals(Boolean.TRUE, candidate.getOperable());
    }

    @Test
    void crossDomainTopicAcceptsUnknownDomainPost() {
        OperationCandidateDTO candidate = receiveHint(topic(null), post(101L, null));
        assertTrue(candidate.getBlockReasons().isEmpty(),
                "cross-domain topic must accept unclassified posts and present them honestly");
        assertEquals(Boolean.TRUE, candidate.getOperable());
    }

    @Test
    void directCreateRejectsOffDomainSectionBeforeTopicInsert() {
        AtomicBoolean inserted = new AtomicBoolean();
        OperationCurationService service = service(null,
                Map.of(101L, post(101L, Post.DOMAIN_CAREER)),
                List.of(), inserted, new AtomicBoolean());

        BizException error = assertThrows(BizException.class,
                () -> service.createTopic(topicCmd(Post.DOMAIN_TECH, List.of(sectionCmd(101L))), 9L));

        assertTrue(error.getMessage().contains("another channel"));
        assertFalse(inserted.get(), "invalid sections must be rejected before the topic row is inserted");
    }

    @Test
    void directUpdateRejectsOffDomainSectionBeforeTopicUpdate() {
        AtomicBoolean updated = new AtomicBoolean();
        OperationTopicPO topic = topic(null);
        OperationCurationService service = service(topic,
                Map.of(101L, post(101L, Post.DOMAIN_CAREER)),
                List.of(), new AtomicBoolean(), updated);

        BizException error = assertThrows(BizException.class,
                () -> service.updateTopic(topic.getId(),
                        topicCmd(Post.DOMAIN_TECH, List.of(sectionCmd(101L))), 9L));

        assertTrue(error.getMessage().contains("another channel"));
        assertFalse(updated.get(), "invalid replacement sections must be rejected before updating topic scope");
    }

    @Test
    void staleDraftRevisionRejectsBeforeTopicUpdate() {
        AtomicBoolean updated = new AtomicBoolean();
        OperationTopicPO topic = topic(Post.DOMAIN_TECH);
        topic.setDraftRevision(3);
        OperationTopicCmd cmd = topicCmd(Post.DOMAIN_TECH, null);
        cmd.setExpectedDraftRevision(2);
        OperationCurationService service = service(topic, Map.of(), List.of(),
                new AtomicBoolean(), updated);

        BizException error = assertThrows(BizException.class,
                () -> service.updateTopic(topic.getId(), cmd, 9L));

        assertEquals(com.offerlab.community.common.result.ErrorCode.CONCURRENT_MODIFICATION.getCode(),
                error.getCode());
        assertFalse(updated.get(), "stale clients must not reach the mapper update");
    }

    @Test
    void mapperOptimisticLockFailureDoesNotReplaceSections() {
        AtomicBoolean updated = new AtomicBoolean();
        AtomicBoolean sectionsSoftDeleted = new AtomicBoolean();
        OperationTopicPO topic = topic(Post.DOMAIN_TECH);
        OperationCurationService service = service(topic,
                Map.of(101L, post(101L, Post.DOMAIN_TECH)),
                List.of(), new AtomicBoolean(), updated, 0, sectionsSoftDeleted);

        BizException error = assertThrows(BizException.class,
                () -> service.updateTopic(topic.getId(),
                        topicCmd(Post.DOMAIN_TECH, List.of(sectionCmd(101L))), 9L));

        assertEquals(com.offerlab.community.common.result.ErrorCode.CONCURRENT_MODIFICATION.getCode(),
                error.getCode());
        assertTrue(updated.get(), "the mapper CAS must be attempted");
        assertFalse(sectionsSoftDeleted.get(),
                "replacement sections must not be touched after a failed optimistic-lock update");
    }

    @Test
    void draftRevisionUsesMybatisOptimisticLock() throws Exception {
        assertNotNull(OperationTopicPO.class.getDeclaredField("draftRevision").getAnnotation(Version.class),
                "draftRevision must stay annotated with @Version");
    }

    @Test
    void createRequiresExplicitTopicScope() {
        AtomicBoolean inserted = new AtomicBoolean();
        OperationTopicCmd cmd = topicCmd(Post.DOMAIN_TECH, List.of(sectionCmd(101L)));
        cmd.setTopicScope(null);
        OperationCurationService service = service(null,
                Map.of(101L, post(101L, Post.DOMAIN_TECH)),
                List.of(), inserted, new AtomicBoolean());

        BizException error = assertThrows(BizException.class,
                () -> service.createTopic(cmd, 9L));

        assertTrue(error.getMessage().contains("scope is required"));
        assertFalse(inserted.get());
    }

    @Test
    void sectionKeyCannotRepresentDifferentLogicalSections() {
        AtomicBoolean inserted = new AtomicBoolean();
        OperationTopicSectionCmd first = sectionCmd(101L);
        OperationTopicSectionCmd second = sectionCmd(102L);
        second.setTitle("另一个章节");
        OperationCurationService service = service(null,
                Map.of(
                        101L, post(101L, Post.DOMAIN_TECH),
                        102L, post(102L, Post.DOMAIN_TECH)),
                List.of(), inserted, new AtomicBoolean());

        BizException error = assertThrows(BizException.class,
                () -> service.createTopic(
                        topicCmd(Post.DOMAIN_TECH, List.of(first, second)), 9L));

        assertTrue(error.getMessage().contains("multiple titles"));
        assertFalse(inserted.get());
    }

    @Test
    void narrowingScopeRejectsExistingOffDomainSections() {
        AtomicBoolean updated = new AtomicBoolean();
        OperationTopicPO topic = topic(null);
        OperationTopicSectionPO section = section(101L);
        OperationCurationService service = service(topic,
                Map.of(101L, post(101L, Post.DOMAIN_CAREER)),
                List.of(section), new AtomicBoolean(), updated);

        BizException error = assertThrows(BizException.class,
                () -> service.updateTopic(topic.getId(), topicCmd(Post.DOMAIN_TECH, null), 9L));

        assertTrue(error.getMessage().contains("another channel"));
        assertFalse(updated.get(), "scope narrowing must validate persisted sections before updating the topic");
    }

    @Test
    void publishCheckAndPublishRejectOffDomainSections() {
        OperationTopicPO topic = topic(Post.DOMAIN_TECH);
        OperationTopicSectionPO section = section(101L);
        OperationCurationService service = service(topic,
                Map.of(101L, post(101L, Post.DOMAIN_CAREER)),
                List.of(section), new AtomicBoolean(), new AtomicBoolean());

        Map<String, Object> check = service.checkTopicPublish(topic.getId());
        assertEquals(Boolean.FALSE, check.get("canPublish"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) check.get("items");
        Map<String, Object> scopeItem = items.stream()
                .filter(item -> "topic_scope_consistent".equals(item.get("code")))
                .findFirst()
                .orElseThrow();
        assertEquals(Boolean.FALSE, scopeItem.get("passed"));
        assertEquals(0, check.get(OperationCurationService.TOPIC_DRAFT_REVISION_KEY));

        BizException error = assertThrows(BizException.class,
                () -> service.publishTopic(topic.getId(), 9L, 0, "publish"));
        assertTrue(error.getMessage().contains("outside its channel scope"));
    }

    @Test
    void archivedTopicPublishCheckFailsStatusGate() {
        OperationTopicPO topic = topic(Post.DOMAIN_TECH);
        topic.setTopicStatus(OperationCurationService.STATUS_ARCHIVED);
        OperationCurationService service = service(topic,
                Map.of(101L, post(101L, Post.DOMAIN_TECH)),
                List.of(section(101L)), new AtomicBoolean(), new AtomicBoolean());

        Map<String, Object> check = service.checkTopicPublish(topic.getId());

        assertEquals(Boolean.FALSE, check.get("canPublish"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) check.get("items");
        Map<String, Object> statusItem = items.stream()
                .filter(item -> "publish_status_allowed".equals(item.get("code")))
                .findFirst()
                .orElseThrow();
        assertEquals(Boolean.FALSE, statusItem.get("passed"));
    }

    @Test
    void successfulLifecycleMutationsReturnAdvancedDraftRevision() {
        OperationTopicPO topic = topic(Post.DOMAIN_TECH);
        topic.setCurrentVersion(0);
        OperationCurationService service = service(topic,
                Map.of(101L, post(101L, Post.DOMAIN_TECH)),
                List.of(section(101L)), new AtomicBoolean(), new AtomicBoolean());

        OperationTopicDTO firstPublish = service.publishTopic(topic.getId(), 9L, 0, "publish one");
        assertEquals(1, firstPublish.getDraftRevision());

        OperationTopicDTO secondPublish = service.publishTopic(topic.getId(), 9L, 1, "publish two");
        assertEquals(2, secondPublish.getDraftRevision());

        OperationTopicDTO rollback = service.rollbackTopic(topic.getId(), 9L, 2, "rollback");
        assertEquals(3, rollback.getDraftRevision());

        OperationTopicDTO archive = service.archiveTopic(topic.getId(), 9L, 3, "archive");
        assertEquals(4, archive.getDraftRevision());
    }

    @Test
    void publicTopicFiltersLegacyOffDomainSnapshotSections() throws Exception {
        OperationTopicPO topic = topic(Post.DOMAIN_TECH);
        topic.setTopicStatus(OperationCurationService.STATUS_PUBLISHED);
        PostBriefDTO sameDomain = post(101L, Post.DOMAIN_TECH);
        PostBriefDTO offDomain = post(102L, Post.DOMAIN_CAREER);
        OperationTopicDTO snapshot = OperationTopicDTO.builder()
                .id(topic.getId())
                .slug(topic.getSlug())
                .name(topic.getTopicName())
                .domain(topic.getDomain())
                .status(OperationCurationService.STATUS_PUBLISHED)
                .source("remote")
                .sections(List.of(snapshotSection(101L), snapshotSection(102L)))
                .build();
        snapshot.getSections().forEach(section -> section.setSectionKey(null));
        topic.setPublishedSnapshotJson(new ObjectMapper().writeValueAsString(snapshot));
        topic.setDraftRevision(7);
        OperationCurationService service = service(topic,
                Map.of(101L, sameDomain, 102L, offDomain),
                List.of(), new AtomicBoolean(), new AtomicBoolean());

        OperationTopicDTO result = service.getPublicTopic(topic.getSlug());

        assertNotNull(result);
        assertEquals(1, result.getSections().size());
        assertEquals(101L, result.getSections().get(0).getSourceId());
        assertNull(result.getDraftRevision(), "public snapshots must not expose the admin draft revision");
        assertTrue(result.getSections().get(0).getSectionKey().startsWith("legacy-"));
        assertEquals(64, result.getSections().get(0).getSectionKey().length(),
                "legacy snapshots must receive the same stable key shape as the SQL migration");
    }

    private OperationCandidateDTO receiveHint(OperationTopicPO topic, PostBriefDTO post) {
        OperationCurationService service = service(topic, Map.of(post.getId(), post),
                List.of(), new AtomicBoolean(), new AtomicBoolean());
        OperationTopicCandidateHintCmd hint = new OperationTopicCandidateHintCmd();
        hint.setSourceType("POST");
        hint.setSourceId(post.getId());
        List<OperationCandidateDTO> candidates = service.receiveTopicCandidateHints(
                topic.getId(), List.of(hint), 9L);
        assertEquals(1, candidates.size());
        return candidates.get(0);
    }

    private static OperationTopicPO topic(Integer domain) {
        OperationTopicPO po = new OperationTopicPO();
        po.setId(7L);
        po.setSlug("cross-view");
        po.setTopicName("跨频道视角");
        po.setOperationType("TOPIC");
        po.setDomain(domain);
        po.setTopicStatus("DRAFT");
        po.setDraftRevision(0);
        po.setIsDeleted(0);
        return po;
    }

    private static OperationTopicCmd topicCmd(Integer domain, List<OperationTopicSectionCmd> sections) {
        OperationTopicCmd cmd = new OperationTopicCmd();
        cmd.setSlug("cross-view");
        cmd.setName("跨频道视角");
        cmd.setOperationType("TOPIC");
        cmd.setTopicScope(OperationCurationService.SCOPE_DOMAIN);
        cmd.setDomain(domain);
        cmd.setExpectedDraftRevision(0);
        cmd.setSections(sections);
        return cmd;
    }

    private static OperationTopicSectionCmd sectionCmd(Long postId) {
        OperationTopicSectionCmd cmd = new OperationTopicSectionCmd();
        cmd.setSectionKey("section-main");
        cmd.setTitle("专题章节");
        cmd.setSourceType(OperationCurationService.SOURCE_POST);
        cmd.setSourceId(postId);
        cmd.setStatus(OperationCurationService.ITEM_ACTIVE);
        cmd.setReasonText("公开收录理由");
        cmd.setReasonConfirmed(true);
        return cmd;
    }

    private static OperationTopicSectionPO section(Long postId) {
        OperationTopicSectionPO section = new OperationTopicSectionPO();
        section.setId(postId + 1_000);
        section.setTopicId(7L);
        section.setSectionKey("section-main");
        section.setSectionTitle("专题章节");
        section.setSourceType(OperationCurationService.SOURCE_POST);
        section.setSourceId(postId);
        section.setSectionStatus(OperationCurationService.ITEM_ACTIVE);
        section.setSortOrder(10);
        section.setNote("公开收录理由");
        section.setIsDeleted(0);
        return section;
    }

    private static OperationTopicSectionDTO snapshotSection(Long postId) {
        return OperationTopicSectionDTO.builder()
                .id(postId + 1_000)
                .sectionKey("section-main")
                .title("专题章节")
                .sourceType(OperationCurationService.SOURCE_POST)
                .sourceId(postId)
                .status(OperationCurationService.ITEM_ACTIVE)
                .sortOrder(10)
                .reasonText("公开收录理由")
                .reasonConfirmed(true)
                .items(List.of())
                .build();
    }

    private static PostBriefDTO post(Long id, Integer domain) {
        return PostBriefDTO.builder()
                .id(id)
                .title("真实经验分享")
                .summary("一段公开可见的经验记录")
                .domain(domain)
                .build();
    }

    private static OperationCurationService service(OperationTopicPO topic,
                                                     Map<Long, PostBriefDTO> posts,
                                                     List<OperationTopicSectionPO> sections,
                                                     AtomicBoolean inserted,
                                                     AtomicBoolean updated) {
        return service(topic, posts, sections, inserted, updated, 1, new AtomicBoolean());
    }

    private static OperationCurationService service(OperationTopicPO topic,
                                                     Map<Long, PostBriefDTO> posts,
                                                     List<OperationTopicSectionPO> sections,
                                                     AtomicBoolean inserted,
                                                     AtomicBoolean updated,
                                                     int updateResult,
                                                     AtomicBoolean sectionsSoftDeleted) {
        return new OperationCurationService(
                unsupported(PostMapper.class),
                unsupported(OperationCurationItemMapper.class),
                unsupported(OperationSlotMapper.class),
                unsupported(OperationSlotItemMapper.class),
                topicMapper(topic, inserted, updated, updateResult),
                sectionMapper(sections, sectionsSoftDeleted),
                postFacade(posts),
                auditService(),
                new SnowflakeIdGenerator(),
                new ObjectMapper(),
                null);
    }

    private static OperationTopicMapper topicMapper(OperationTopicPO topic,
                                                    AtomicBoolean inserted,
                                                    AtomicBoolean updated,
                                                    int updateResult) {
        return (OperationTopicMapper) Proxy.newProxyInstance(
                OperationTopicScopeTest.class.getClassLoader(),
                new Class<?>[] {OperationTopicMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectById" -> topic;
                    case "selectBySlug" -> topic;
                    case "insert" -> {
                        inserted.set(true);
                        yield 1;
                    }
                    case "updateById" -> {
                        updated.set(true);
                        if (updateResult == 1 && args[0] instanceof OperationTopicPO updatedTopic) {
                            int revision = updatedTopic.getDraftRevision() == null
                                    ? 0
                                    : updatedTopic.getDraftRevision();
                            updatedTopic.setDraftRevision(revision + 1);
                        }
                        yield updateResult;
                    }
                    case "toString" -> "OperationTopicMapperStub";
                    case "hashCode" -> 0;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static AdminAuditService auditService() {
        AdminAuditLogMapper mapper = (AdminAuditLogMapper) Proxy.newProxyInstance(
                OperationTopicScopeTest.class.getClassLoader(),
                new Class<?>[] {AdminAuditLogMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists", "insert" -> 1;
                    case "toString" -> "AdminAuditLogMapperStub";
                    case "hashCode" -> 0;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new AdminAuditService(mapper, new SnowflakeIdGenerator(), new ObjectMapper());
    }

    private static OperationTopicSectionMapper sectionMapper(List<OperationTopicSectionPO> sections,
                                                             AtomicBoolean softDeleted) {
        return (OperationTopicSectionMapper) Proxy.newProxyInstance(
                OperationTopicScopeTest.class.getClassLoader(),
                new Class<?>[] {OperationTopicSectionMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "listByTopic" -> sections;
                    case "softDeleteByTopic" -> {
                        softDeleted.set(true);
                        yield 0;
                    }
                    case "insert" -> 1;
                    case "toString" -> "OperationTopicSectionMapperStub";
                    case "hashCode" -> 0;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static PostFacade postFacade(Map<Long, PostBriefDTO> posts) {
        return (PostFacade) Proxy.newProxyInstance(
                OperationTopicScopeTest.class.getClassLoader(),
                new Class<?>[] {PostFacade.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "batchGetPosts" -> posts;
                    case "toString" -> "PostFacadeStub";
                    case "hashCode" -> 0;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T unsupported(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                OperationTopicScopeTest.class.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Stub";
                    case "hashCode" -> 0;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(type.getSimpleName() + "." + method.getName());
                });
    }
}

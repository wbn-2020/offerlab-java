package com.offerlab.community.post.reference.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.dto.PostReferenceCreateCmd;
import com.offerlab.community.post.api.dto.PostReferenceDTO;
import com.offerlab.community.post.api.dto.PostReferenceReorderCmd;
import com.offerlab.community.post.api.dto.PostReferenceReorderItemCmd;
import com.offerlab.community.post.api.dto.PostReferenceUpdateCmd;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceMapper;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceRows.PostAccessRow;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceRows.ReferenceRow;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostReferenceServiceTest {

    @Test
    void createRequiresPostAuthorAndAppliesAsciiUrlAndBrokenSemantics() {
        MapperState state = new MapperState(900L, 7L);
        PostReferenceService service = service(state);

        BizException forbidden = assertThrows(BizException.class,
                () -> service.create(900L, createCmd("ACTIVE", "ignored"), 8L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), forbidden.getCode());

        PostReferenceDTO created = service.create(900L, createCmd("ACTIVE", "stale reason"), 7L);

        assertEquals("https://8.8.8.8/caf%C3%A9?q=1", created.getNormalizedUrl());
        assertEquals("8.8.8.8", created.getSourceDomain());
        assertEquals("ACTIVE", created.getReferenceStatus());
        assertNull(created.getBrokenReason());
        assertEquals(1, created.getRevision());
        assertEquals(0, created.getSortOrder());
        assertTrue(created.getLastConfirmedAt() != null);

        PostReferenceCreateCmd broken = createCmd("BROKEN", null);
        BizException missingReason = assertThrows(BizException.class,
                () -> service.create(900L, broken, 7L));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), missingReason.getCode());
    }

    @Test
    void publicReadRechecksPostVisibilityAfterLoadingReferences() {
        MapperState state = new MapperState(900L, 7L);
        state.rows.put(1L, row(1L, 900L, 7L, 0, 1));
        state.hideOnSecondPublicCheck = true;
        PostReferenceService service = service(state);

        BizException hidden = assertThrows(BizException.class, () -> service.listPublic(900L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), hidden.getCode());
        assertEquals(2, state.publicChecks);
    }

    @Test
    void updateDeleteAndReorderUseExpectedRevision() {
        MapperState state = new MapperState(900L, 7L);
        state.rows.put(1L, row(1L, 900L, 7L, 0, 2));
        state.rows.put(2L, row(2L, 900L, 7L, 1, 4));
        PostReferenceService service = service(state);

        PostReferenceUpdateCmd stale = updateCmd(1);
        BizException staleUpdate = assertThrows(BizException.class,
                () -> service.update(900L, 1L, stale, 7L));
        assertEquals(ErrorCode.INVALID_STATUS.getCode(), staleUpdate.getCode());

        PostReferenceUpdateCmd current = updateCmd(2);
        PostReferenceDTO updated = service.update(900L, 1L, current, 7L);
        assertEquals(3, updated.getRevision());
        assertEquals("BROKEN", updated.getReferenceStatus());
        assertEquals("HTTP 404", updated.getBrokenReason());
        assertNull(updated.getLastConfirmedAt());

        PostReferenceReorderCmd reorder = reorderCmd(item(2L, 4), item(1L, 3));
        List<PostReferenceDTO> reordered = service.reorder(900L, reorder, 7L);
        assertEquals(List.of(2L, 1L), reordered.stream().map(PostReferenceDTO::getId).toList());
        assertEquals(List.of(5, 4), reordered.stream().map(PostReferenceDTO::getRevision).toList());

        BizException staleDelete = assertThrows(BizException.class,
                () -> service.delete(900L, 1L, 3, 7L));
        assertEquals(ErrorCode.INVALID_STATUS.getCode(), staleDelete.getCode());

        service.delete(900L, 1L, 4, 7L);
        assertEquals(1, state.rows.size());
    }

    @Test
    void reorderRejectsPartialOrDuplicateListsBeforeWriting() {
        MapperState state = new MapperState(900L, 7L);
        state.rows.put(1L, row(1L, 900L, 7L, 0, 1));
        state.rows.put(2L, row(2L, 900L, 7L, 1, 1));
        PostReferenceService service = service(state);

        BizException partial = assertThrows(BizException.class,
                () -> service.reorder(900L, reorderCmd(item(1L, 1)), 7L));
        BizException duplicate = assertThrows(BizException.class,
                () -> service.reorder(900L, reorderCmd(item(1L, 1), item(1L, 1)), 7L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), partial.getCode());
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), duplicate.getCode());
        assertEquals(0, state.reorderWrites);
    }

    private static PostReferenceService service(MapperState state) {
        return new PostReferenceService(mapper(state), new SnowflakeIdGenerator(1, 1));
    }

    private static PostReferenceCreateCmd createCmd(String status, String brokenReason) {
        PostReferenceCreateCmd cmd = new PostReferenceCreateCmd();
        cmd.setReferenceType("source");
        cmd.setTitle("Primary source");
        cmd.setUrl("HTTPS://8.8.8.8:443/a/../caf\u00e9?q=1#section");
        cmd.setNote("Useful context");
        cmd.setReferenceStatus(status);
        cmd.setBrokenReason(brokenReason);
        return cmd;
    }

    private static PostReferenceUpdateCmd updateCmd(int revision) {
        PostReferenceUpdateCmd cmd = new PostReferenceUpdateCmd();
        cmd.setReferenceType("data");
        cmd.setTitle("Updated source");
        cmd.setUrl("https://8.8.4.4/data");
        cmd.setReferenceStatus("BROKEN");
        cmd.setBrokenReason("HTTP 404");
        cmd.setExpectedRevision(revision);
        return cmd;
    }

    private static PostReferenceReorderItemCmd item(Long id, int revision) {
        PostReferenceReorderItemCmd item = new PostReferenceReorderItemCmd();
        item.setReferenceId(id);
        item.setExpectedRevision(revision);
        return item;
    }

    private static PostReferenceReorderCmd reorderCmd(PostReferenceReorderItemCmd... items) {
        PostReferenceReorderCmd cmd = new PostReferenceReorderCmd();
        cmd.setItems(List.of(items));
        return cmd;
    }

    private static ReferenceRow row(Long id, Long postId, Long ownerUid, int sortOrder, int revision) {
        ReferenceRow row = new ReferenceRow();
        row.setId(id);
        row.setPostId(postId);
        row.setOwnerUid(ownerUid);
        row.setReferenceType("SOURCE");
        row.setTitle("Reference " + id);
        row.setUrl("https://8.8.8.8/" + id);
        row.setNormalizedUrl(row.getUrl());
        row.setSourceDomain("8.8.8.8");
        row.setReferenceStatus("ACTIVE");
        row.setSortOrder(sortOrder);
        row.setRevision(revision);
        row.setLastConfirmedAt(LocalDateTime.now());
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        row.setIsDeleted(0);
        return row;
    }

    private static ReferenceRow copy(ReferenceRow source) {
        ReferenceRow copy = new ReferenceRow();
        copy.setId(source.getId());
        copy.setPostId(source.getPostId());
        copy.setOwnerUid(source.getOwnerUid());
        copy.setReferenceType(source.getReferenceType());
        copy.setTitle(source.getTitle());
        copy.setUrl(source.getUrl());
        copy.setNormalizedUrl(source.getNormalizedUrl());
        copy.setSourceDomain(source.getSourceDomain());
        copy.setNote(source.getNote());
        copy.setBrokenReason(source.getBrokenReason());
        copy.setReferenceStatus(source.getReferenceStatus());
        copy.setSortOrder(source.getSortOrder());
        copy.setRevision(source.getRevision());
        copy.setLastConfirmedAt(source.getLastConfirmedAt());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setIsDeleted(source.getIsDeleted());
        return copy;
    }

    private static PostReferenceMapper mapper(MapperState state) {
        return (PostReferenceMapper) Proxy.newProxyInstance(
                PostReferenceMapper.class.getClassLoader(),
                new Class<?>[]{PostReferenceMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectPostForUpdate" -> state.post;
                    case "countPublicPost" -> {
                        state.publicChecks++;
                        yield state.hideOnSecondPublicCheck && state.publicChecks >= 2 ? 0 : 1;
                    }
                    case "selectActiveByPostId" -> state.rows.values().stream()
                            .map(PostReferenceServiceTest::copy)
                            .sorted(Comparator.comparing(ReferenceRow::getSortOrder)
                                    .thenComparing(ReferenceRow::getId))
                            .toList();
                    case "selectActiveById" -> {
                        ReferenceRow current = state.rows.get((Long) args[1]);
                        yield current == null ? null : copy(current);
                    }
                    case "selectActiveIdByNormalizedUrl" -> state.rows.values().stream()
                            .filter(row -> Objects.equals(row.getNormalizedUrl(), args[1]))
                            .map(ReferenceRow::getId)
                            .findFirst()
                            .orElse(null);
                    case "selectMaxSortOrder" -> state.rows.values().stream()
                            .map(ReferenceRow::getSortOrder)
                            .max(Integer::compareTo)
                            .orElse(-1);
                    case "insert" -> {
                        ReferenceRow inserted = copy((ReferenceRow) args[0]);
                        state.rows.put(inserted.getId(), inserted);
                        yield 1;
                    }
                    case "updateIfRevision" -> {
                        ReferenceRow requested = (ReferenceRow) args[0];
                        Integer expected = (Integer) args[1];
                        ReferenceRow current = state.rows.get(requested.getId());
                        if (current == null || !Objects.equals(current.getRevision(), expected)) {
                            yield 0;
                        }
                        ReferenceRow updated = copy(requested);
                        updated.setRevision(expected + 1);
                        state.rows.put(updated.getId(), updated);
                        yield 1;
                    }
                    case "softDeleteIfRevision" -> {
                        Long id = (Long) args[1];
                        Integer expected = (Integer) args[3];
                        ReferenceRow current = state.rows.get(id);
                        if (current == null || !Objects.equals(current.getRevision(), expected)) {
                            yield 0;
                        }
                        state.rows.remove(id);
                        yield 1;
                    }
                    case "reorderIfRevision" -> {
                        Long id = (Long) args[1];
                        Integer sortOrder = (Integer) args[3];
                        Integer expected = (Integer) args[4];
                        ReferenceRow current = state.rows.get(id);
                        if (current == null || !Objects.equals(current.getRevision(), expected)) {
                            yield 0;
                        }
                        current.setSortOrder(sortOrder);
                        current.setRevision(expected + 1);
                        state.reorderWrites++;
                        yield 1;
                    }
                    case "toString" -> "PostReferenceMapperTestProxy";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class MapperState {
        private final PostAccessRow post = new PostAccessRow();
        private final Map<Long, ReferenceRow> rows = new LinkedHashMap<>();
        private int publicChecks;
        private int reorderWrites;
        private boolean hideOnSecondPublicCheck;

        private MapperState(Long postId, Long authorId) {
            post.setId(postId);
            post.setAuthorId(authorId);
            post.setPostStatus(Post.STATUS_PUBLISHED);
            post.setVisibility(Post.VIS_PUBLIC);
            post.setIsDeleted(0);
        }
    }
}

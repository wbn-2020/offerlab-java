package com.offerlab.community.post.collaboration.application;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.collaboration.api.CollaborationModels.NeedDTO;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationRows.NeedRow;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollaborationNeedTotalTest {

    @Test
    void myClaimedNeedPageUsesExactFilteredCountAtGrowthProfilePageSize() {
        AtomicReference<Object[]> countArgs = new AtomicReference<>();
        AtomicReference<Object[]> listArgs = new AtomicReference<>();
        CollaborationMapper mapper = mapper((method, args) -> switch (method) {
            case "countNeedsByClaimant" -> {
                countArgs.set(args.clone());
                yield 12L;
            }
            case "listNeedsByClaimant" -> {
                listArgs.set(args.clone());
                yield List.of(need(30L, 7L), need(20L, 7L));
            }
            default -> throw new UnsupportedOperationException(method);
        });

        PageResult<NeedDTO> result = service(mapper)
                .listMyClaimedNeeds(7L, "CLAIMED", 0, 1);

        assertEquals(1, result.getItems().size());
        assertEquals(12L, result.getTotal());
        assertTrue(result.getHasMore());
        assertEquals("30", result.getNextCursor());
        assertEquals(7L, countArgs.get()[0]);
        assertEquals("CLAIMED", countArgs.get()[1]);
        assertEquals(7L, listArgs.get()[0]);
        assertEquals("CLAIMED", listArgs.get()[1]);
        assertEquals(0L, listArgs.get()[2]);
        assertEquals(2, listArgs.get()[3]);
    }

    @Test
    void myCreatedNeedPageUsesExactFilteredCountAtGrowthProfilePageSize() {
        AtomicReference<Object[]> countArgs = new AtomicReference<>();
        AtomicReference<Object[]> listArgs = new AtomicReference<>();
        CollaborationMapper mapper = mapper((method, args) -> switch (method) {
            case "countNeedsByCreator" -> {
                countArgs.set(args.clone());
                yield 9L;
            }
            case "listNeedsByCreator" -> {
                listArgs.set(args.clone());
                yield List.of(need(20L, 8L), need(10L, 8L));
            }
            default -> throw new UnsupportedOperationException(method);
        });

        PageResult<NeedDTO> result = service(mapper)
                .listMyCreatedNeeds(8L, null, 25L, 1);

        assertEquals(1, result.getItems().size());
        assertEquals(9L, result.getTotal());
        assertTrue(result.getHasMore());
        assertEquals("20", result.getNextCursor());
        assertEquals(8L, countArgs.get()[0]);
        assertNull(countArgs.get()[1]);
        assertEquals(8L, listArgs.get()[0]);
        assertNull(listArgs.get()[1]);
        assertEquals(25L, listArgs.get()[2]);
        assertEquals(2, listArgs.get()[3]);
    }

    @Test
    void publicNeedPageUsesExactFilteredCountInsteadOfVisiblePageSize() {
        AtomicReference<Object[]> countArgs = new AtomicReference<>();
        AtomicReference<Object[]> listArgs = new AtomicReference<>();
        List<NeedRow> rows = List.of(need(30L, 9L), need(20L, 9L), need(10L, 9L));
        CollaborationMapper mapper = mapper((method, args) -> switch (method) {
                    case "countNeeds" -> {
                        countArgs.set(args.clone());
                        yield 37L;
                    }
                    case "listNeeds" -> {
                        listArgs.set(args.clone());
                        yield rows;
                    }
                    default -> throw new UnsupportedOperationException(method);
                });

        PageResult<NeedDTO> result = service(mapper).listNeeds(null, "OPEN", null, 0, 2);

        assertEquals(2, result.getItems().size());
        assertEquals(37L, result.getTotal());
        assertTrue(result.getHasMore());
        assertEquals("20", result.getNextCursor());
        assertNull(countArgs.get()[0]);
        assertEquals("OPEN", countArgs.get()[1]);
        assertNull(listArgs.get()[0]);
        assertEquals("OPEN", listArgs.get()[1]);
        assertEquals(0L, listArgs.get()[3]);
        assertEquals(3, listArgs.get()[4]);
    }

    @Test
    void countQueriesMatchTheirListFiltersAndIgnoreTheCursor() throws Exception {
        assertCountSql("countNeeds", new Class<?>[] {Integer.class, String.class},
                "n.domain = #{domain}");
        assertCountSql("countNeedsByClaimant", new Class<?>[] {Long.class, String.class},
                "n.claimed_by_uid = #{uid}");
        assertCountSql("countNeedsByCreator", new Class<?>[] {Long.class, String.class},
                "n.creator_uid = #{uid}");
    }

    private static void assertCountSql(String methodName,
                                       Class<?>[] parameterTypes,
                                       String ownershipPredicate) throws Exception {
        Select select = CollaborationMapper.class
                .getMethod(methodName, parameterTypes)
                .getAnnotation(Select.class);
        assertNotNull(select);
        String sql = String.join("\n", select.value());
        assertTrue(sql.contains("FROM t_collab_content_need n"));
        assertTrue(sql.contains("n.moderation_hidden = 0"));
        assertTrue(sql.contains(ownershipPredicate));
        assertTrue(sql.contains("n.need_status = #{status}"));
        assertFalse(sql.contains("#{cursor}"),
                "total must cover the full filtered result set, not only the remaining cursor window");
    }

    private static CollaborationMapper mapper(MapperBehavior behavior) {
        return (CollaborationMapper) Proxy.newProxyInstance(
                CollaborationNeedTotalTest.class.getClassLoader(),
                new Class<?>[] {CollaborationMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existingTableCount" -> 20;
                    case "existingCriticalColumnCount" -> 60;
                    case "toString" -> "CollaborationMapperStub";
                    case "hashCode" -> 0;
                    case "equals" -> proxy == args[0];
                    default -> behavior.invoke(method.getName(), args);
                });
    }

    private static CollaborationService service(CollaborationMapper mapper) {
        return new CollaborationService(mapper, null, null, null, null, null, null, null, null);
    }

    private static NeedRow need(Long id, Long ownerUid) {
        NeedRow row = new NeedRow();
        row.setId(id);
        row.setCreatorUid(ownerUid);
        row.setClaimedByUid(ownerUid);
        row.setDomain(3);
        row.setStatus("OPEN");
        row.setTitle("Need " + id);
        row.setFollowerCount(0);
        row.setFollowed(0);
        return row;
    }

    @FunctionalInterface
    private interface MapperBehavior {
        Object invoke(String method, Object[] args);
    }
}

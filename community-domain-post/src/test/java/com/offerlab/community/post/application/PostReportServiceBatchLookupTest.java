package com.offerlab.community.post.application;

import com.offerlab.community.post.api.dto.PostReportDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostReportMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostReportPO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostReportServiceBatchLookupTest {

    @Test
    void listRecentBatchLoadsReportedPostsInsteadOfFindingEachOne() {
        CountingPostRepository postRepository = new CountingPostRepository(Map.of(
                11L, post(11L, "First post", "First report content"),
                12L, post(12L, "Second post", "Second report content")
        ));
        PostReportService service = new PostReportService(
                postRepository,
                mapperReturning(List.of(report(101L, 11L), report(102L, 12L), report(103L, 11L))),
                null,
                null,
                null,
                null,
                null,
                null
        );

        List<PostReportDTO> reports = service.listRecent(null, 10, true);

        assertEquals(3, reports.size());
        assertEquals(1, postRepository.batchFindByIdsCalls, "report list should batch load reported posts once");
        assertEquals(List.of(11L, 12L), postRepository.lastBatchIds, "batch lookup should de-duplicate post ids in report order");
        assertEquals(0, postRepository.findByIdCalls, "report list must not call findById once per report row");
    }

    private static PostReportMapper mapperReturning(List<PostReportPO> reports) {
        return (PostReportMapper) Proxy.newProxyInstance(
                PostReportMapper.class.getClassLoader(),
                new Class<?>[]{PostReportMapper.class},
                (proxy, method, args) -> {
                    if ("selectRecent".equals(method.getName()) && method.getParameterCount() == 3) {
                        return reports;
                    }
                    if ("toString".equals(method.getName())) {
                        return "PostReportMapperStub";
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private static PostReportPO report(Long id, Long postId) {
        PostReportPO report = new PostReportPO();
        report.setId(id);
        report.setPostId(postId);
        report.setReporterUid(9000L + id);
        report.setReason("spam");
        report.setDetail("duplicate content");
        report.setReportStatus(PostReportService.STATUS_PENDING);
        return report;
    }

    private static Post post(Long id, String title, String content) {
        return Post.builder()
                .id(id)
                .title(title)
                .content(content)
                .visibility(Post.VIS_PUBLIC)
                .postStatus(Post.STATUS_PUBLISHED)
                .build();
    }

    private static class CountingPostRepository implements PostRepository {
        private final Map<Long, Post> posts;
        private int findByIdCalls;
        private int batchFindByIdsCalls;
        private List<Long> lastBatchIds = List.of();

        CountingPostRepository(Map<Long, Post> posts) {
            this.posts = posts;
        }

        @Override
        public void save(Post post) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Post> findById(Long id) {
            findByIdCalls++;
            return Optional.ofNullable(posts.get(id));
        }

        @Override
        public Map<Long, Post> batchFindByIds(Collection<Long> ids) {
            batchFindByIdsCalls++;
            lastBatchIds = ids.stream().toList();
            Map<Long, Post> result = new LinkedHashMap<>();
            for (Long id : ids) {
                if (posts.containsKey(id)) {
                    result.put(id, posts.get(id));
                }
            }
            return result;
        }

        @Override
        public boolean update(Post post) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean updateStatusIfCurrent(Long postId, Integer expectedStatus, Integer nextStatus, Integer expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void softDelete(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Post> findByAuthor(Long authorId, long cursor, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Post> findLatest(long cursor, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Post> findPosts(Long authorId, Long tagId, Integer postType, Boolean featured, Integer domain, long cursor, int size) {
            throw new UnsupportedOperationException();
        }
    }
}

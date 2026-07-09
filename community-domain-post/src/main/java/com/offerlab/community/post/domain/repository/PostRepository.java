package com.offerlab.community.post.domain.repository;

import com.offerlab.community.post.domain.model.Post;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PostRepository {

    void save(Post post);

    Optional<Post> findById(Long id);

    Map<Long, Post> batchFindByIds(Collection<Long> ids);

    boolean update(Post post);

    boolean updateStatusIfCurrent(Long postId, Integer expectedStatus, Integer nextStatus, Integer expectedVersion);

    void softDelete(Long id);

    /** 按作者 + 时间倒序，cursor 为上一页最后一条的 createTime epoch ms；首屏传 0 */
    List<Post> findByAuthor(Long authorId, long cursor, int size);

    /** 全站最新（公开 + 已发布） */
    List<Post> findLatest(long cursor, int size);

    List<Post> findPosts(Long authorId, Long tagId, Integer postType, Boolean featured, Integer domain, long cursor, int size);

    default List<Post> findPosts(Long authorId, Long tagId, Integer postType, long cursor, int size) {
        return findPosts(authorId, tagId, postType, null, null, cursor, size);
    }
}

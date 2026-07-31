package com.offerlab.community.post.infrastructure.persistence;

import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostExtensionMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostExtensionPO;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PostRepositoryImplDomainTest {

    @Test
    void keepsMissingDomainUnclassifiedForLegacyPosts() throws Exception {
        Post post = mapToDomain(postPo(), null);

        assertNull(post.getDomain());
    }

    @Test
    void keepsIllegalLegacyDomainUnclassifiedInsteadOfMisclassifyingItAsTech() throws Exception {
        PostExtensionPO extension = new PostExtensionPO();
        extension.setPostId(7001L);
        extension.setPostType(Post.TYPE_TECH_ARTICLE);
        extension.setExtJson("{\"domain\":999}");

        Post post = mapToDomain(postPo(), extension);

        assertNull(post.getDomain());
    }

    @Test
    void findLatestLoadsExtensionDomainBeforeDefaultingLegacyPosts() {
        PostExtensionPO careerExtension = new PostExtensionPO();
        careerExtension.setPostId(7001L);
        careerExtension.setPostType(Post.TYPE_TECH_ARTICLE);
        careerExtension.setExtJson("{\"domain\":2}");
        PostMapper postMapper = fake(PostMapper.class, "selectList", List.of(postPo()));
        PostExtensionMapper extensionMapper = fake(PostExtensionMapper.class, "selectBatchIds", List.of(careerExtension));
        PostRepositoryImpl repository = new PostRepositoryImpl(postMapper, extensionMapper, null, null);

        List<Post> posts = repository.findLatest(0, 20);

        assertEquals(Post.DOMAIN_CAREER, posts.get(0).getDomain());
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, String supportedMethod, Object value) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> method.getName().equals(supportedMethod) ? value : null);
    }

    private static Post mapToDomain(PostPO post, PostExtensionPO extension) throws Exception {
        Method method = PostRepositoryImpl.class.getDeclaredMethod("toDomain", PostPO.class, PostExtensionPO.class);
        method.setAccessible(true);
        return (Post) method.invoke(null, post, extension);
    }

    private static PostPO postPo() {
        PostPO post = new PostPO();
        post.setId(7001L);
        post.setAuthorId(17L);
        post.setPostType(Post.TYPE_TECH_ARTICLE);
        post.setTitle("legacy post");
        post.setContent("legacy content");
        post.setVisibility(Post.VIS_PUBLIC);
        post.setPostStatus(Post.STATUS_PUBLISHED);
        post.setCreateTime(LocalDateTime.of(2026, 1, 1, 12, 0));
        post.setUpdateTime(LocalDateTime.of(2026, 1, 1, 12, 0));
        return post;
    }
}

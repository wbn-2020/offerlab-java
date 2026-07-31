package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.api.dto.PostDraftCmd;
import com.offerlab.community.post.api.dto.PostDraftDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostDraftMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostDraftPO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class PostDraftServiceDomainTest {

    @Test
    void draftWithoutDomainRemainsUnclassified() {
        AtomicReference<PostDraftPO> stored = new AtomicReference<>();
        PostDraftMapper draftMapper = (PostDraftMapper) Proxy.newProxyInstance(
                PostDraftMapper.class.getClassLoader(),
                new Class<?>[]{PostDraftMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> 1;
                    case "insert" -> {
                        stored.set((PostDraftPO) args[0]);
                        yield 1;
                    }
                    case "selectByUser" -> stored.get();
                    case "toString" -> "PostDraftMapperFake";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
        PostDraftService service = new PostDraftService(
                draftMapper,
                new SnowflakeIdGenerator(),
                new ObjectMapper());

        PostDraftDTO draft = service.save(PostDraftCmd.builder()
                .uid(7L)
                .postType(15)
                .title("未分类草稿")
                .content("先保存内容，发布前再选择频道。")
                .build());

        assertNull(draft.getDomain());
        assertFalse(draft.getExtJson().contains("\"domain\""));
    }
}

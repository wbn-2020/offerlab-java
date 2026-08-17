package com.offerlab.community.search.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchContentEnvironmentGuardTest {

    @Test
    void indexDocumentsQueriesSuggestionsAndReadinessMustRequireCommunity() throws Exception {
        String indexer = read("src/main/java/com/offerlab/community/search/application/PostSearchIndexer.java");
        String facade = read("src/main/java/com/offerlab/community/search/application/SearchFacadeImpl.java");
        String mapper = read("../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java");

        assertTrue(indexer.contains("!Post.isCommunityContent(post.getContentEnvironment())"));
        assertTrue(indexer.contains("doc.put(\"contentEnvironment\", post.getContentEnvironment())"));
        assertTrue(indexer.contains("props.put(\"contentEnvironment\", keyword)"));
        assertTrue(indexer.contains("Post.CONTENT_ENVIRONMENT_COMMUNITY"));
        assertTrue(facade.contains("\"contentEnvironment\"")
                && facade.contains("Post.CONTENT_ENVIRONMENT_COMMUNITY"));
        assertTrue(mapper.contains("selectPublicPostsForIndexAfterId")
                && mapper.contains("p.content_environment = 'COMMUNITY'"));
        assertTrue(mapper.contains("selectPublicSeoPosts"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

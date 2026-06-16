package com.offerlab.community.search.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchSynonymRecallGuardTest {

    @Test
    void tagSynonymsMustParticipateInIndexingEsSearchSuggestAndMysqlFallback() throws Exception {
        String indexer = read("src/main/java/com/offerlab/community/search/application/PostSearchIndexer.java");
        String facade = read("src/main/java/com/offerlab/community/search/application/SearchFacadeImpl.java");
        String postMapper = read("../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java");
        String tagMapper = read("../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/TagMapper.java");

        assertTrue(indexer.contains("doc.put(\"tagSynonyms\", tagSynonyms)"), "post index documents must expose tag synonyms as a searchable field");
        assertTrue(indexer.contains("doc.put(\"tagSearchTerms\", uniqueText(tagSearchTerms))"), "post index documents must combine tag names and synonyms for recall");
        assertTrue(indexer.contains(".synonyms(parseSynonyms(tag.getSynonyms()))"), "indexer must carry synonyms from PostTagView into TagDTO");
        assertTrue(indexer.contains("doc.put(\"synonyms\", uniqueText(tag.getSynonyms()))"), "nested tag documents must retain synonyms for ES source reads");
        assertTrue(indexer.contains("props.put(\"tagSynonyms\", text)"), "fresh ES mappings must define tagSynonyms");
        assertTrue(indexer.contains("props.put(\"tagSearchTerms\", text)"), "fresh ES mappings must define tagSearchTerms");
        assertTrue(indexer.contains("props.put(\"tags\", Map.of(\"type\", \"nested\", \"properties\", Map.of(\"synonyms\", text)))"),
                "existing ES mappings must preserve the nested tags type while adding tag synonyms");

        assertTrue(facade.contains("\"tagSynonyms^2\""), "ES keyword search must query tag synonyms");
        assertTrue(facade.contains("\"tagSearchTerms^2\""), "ES keyword search must query combined tag search terms");
        assertTrue(facade.contains("Map.of(\"match_phrase\", Map.of(\"tagSynonyms\", clean(company)))"), "tech-stack filter must match tag synonyms");
        assertTrue(facade.contains("Map.of(\"match_phrase\", Map.of(\"tagSearchTerms\", clean(company)))"), "tech-stack filter must match combined tag terms");
        assertTrue(facade.contains("Map.of(\"match_phrase\", Map.of(\"tagSynonyms\", clean(position)))"), "scenario filter must match tag synonyms");
        assertTrue(facade.contains("Map.of(\"match_phrase_prefix\", Map.of(\"tagSynonyms\", prefix))"), "ES suggestions must include tag synonyms");
        assertTrue(facade.contains("\"tagNames\", \"tagSynonyms\", \"tagSearchTerms\""), "ES suggest source must request tag recall fields");
        assertTrue(facade.contains("addArrayMatches(result, source.path(\"tagSynonyms\"), prefix)"), "ES suggestions must render synonym matches");
        assertTrue(facade.contains("addTagMatches(result, tags.getOrDefault(post.getId(), List.of()), p)"), "MySQL suggestions must render synonym matches");
        assertTrue(facade.contains(".synonyms(parseSynonyms(tag.getSynonyms()))"), "MySQL fallback tags must retain synonyms");
        assertTrue(facade.contains(".synonyms(textArray(tag.path(\"synonyms\")))"), "ES results must retain nested tag synonyms");

        assertTrue(postMapper.contains("t.synonyms LIKE CONCAT('%', #{keyword}, '%')"), "keyword MySQL fallback must match tag synonyms");
        assertTrue(postMapper.contains("t.synonyms LIKE CONCAT('%', #{company}, '%')"), "tech-stack MySQL filter must match tag synonyms");
        assertTrue(postMapper.contains("t.synonyms LIKE CONCAT('%', #{position}, '%')"), "scenario MySQL filter must match tag synonyms");
        assertTrue(postMapper.contains("t.synonyms LIKE CONCAT('%', #{prefix}, '%')"), "MySQL suggestions must match tag synonyms");
        assertTrue(tagMapper.contains("synonyms LIKE CONCAT('%', #{keyword}, '%')"), "tag governance search must remain synonym-aware");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

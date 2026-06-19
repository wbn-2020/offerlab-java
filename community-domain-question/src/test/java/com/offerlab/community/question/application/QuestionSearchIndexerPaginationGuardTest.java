package com.offerlab.community.question.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.infra.es.config.ElasticsearchProperties;
import com.offerlab.community.question.api.dto.QuestionQuery;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionSearchIndexerPaginationGuardTest {

    @Test
    void searchCapsDeepFromSizeWindowBeforeCallingElasticsearch() {
        CapturingElasticsearchClient elasticsearch = new CapturingElasticsearchClient();
        QuestionSearchIndexer indexer = new QuestionSearchIndexer(elasticsearch, null, null, event -> {
        });
        QuestionQuery query = new QuestionQuery();
        query.setKeyword("redis");

        indexer.search(query, 20_000, 500);

        assertNotNull(elasticsearch.lastSearchBody, "search body should be sent to Elasticsearch when index is ready");
        int from = (Integer) elasticsearch.lastSearchBody.get("from");
        int size = (Integer) elasticsearch.lastSearchBody.get("size");
        assertTrue(size <= 100, "question ES search size must be capped defensively");
        assertTrue(from >= 0, "question ES search offset must never be negative");
        assertTrue(from + size <= 10_000, "question ES search must stay inside Elasticsearch's result window");
    }

    private static class CapturingElasticsearchClient extends ElasticsearchHttpClient {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
        private Map<String, Object> lastSearchBody;

        CapturingElasticsearchClient() {
            super(HttpClient.newHttpClient(), OBJECT_MAPPER, properties());
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean indexExists(String index) {
            return true;
        }

        @Override
        public boolean updateMapping(String index, Map<String, Object> properties) {
            return true;
        }

        @Override
        public Optional<JsonNode> search(String index, Map<String, Object> body) {
            lastSearchBody = body;
            try {
                return Optional.of(OBJECT_MAPPER.readTree("{\"hits\":{\"hits\":[]}}"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private static ElasticsearchProperties properties() {
            ElasticsearchProperties properties = new ElasticsearchProperties();
            properties.setQuestionIndex("question_test_idx");
            return properties;
        }
    }
}

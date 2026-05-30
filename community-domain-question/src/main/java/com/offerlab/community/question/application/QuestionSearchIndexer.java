package com.offerlab.community.question.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.offerlab.community.infra.es.client.ElasticsearchHttpClient;
import com.offerlab.community.question.api.dto.QuestionQuery;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewQuestionMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewQuestionTagMapper;
import com.offerlab.community.question.infrastructure.persistence.po.InterviewQuestionPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionSearchIndexer {
    private final ElasticsearchHttpClient elasticsearch;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewQuestionTagMapper questionTagMapper;

    private final AtomicBoolean indexReady = new AtomicBoolean(false);

    public boolean ensureQuestionIndex() {
        if (!elasticsearch.enabled() || !elasticsearch.available()) {
            indexReady.set(false);
            return false;
        }
        if (indexReady.get() && elasticsearch.indexExists(elasticsearch.questionIndex())) {
            return true;
        }
        if (elasticsearch.indexExists(elasticsearch.questionIndex())) {
            ensureStructuredFieldMapping();
            indexReady.set(true);
            return true;
        }
        boolean created = elasticsearch.createIndex(elasticsearch.questionIndex(), questionIndexMapping());
        indexReady.set(created);
        return created;
    }

    public boolean indexQuestion(Long questionId) {
        if (questionId == null || !ensureQuestionIndex()) {
            return false;
        }
        List<InterviewQuestionPO> rows = questionMapper.selectVisibleByIds(List.of(questionId), true);
        if (rows.isEmpty()) {
            return elasticsearch.deleteDocument(elasticsearch.questionIndex(), String.valueOf(questionId));
        }
        return elasticsearch.indexDocument(elasticsearch.questionIndex(), String.valueOf(questionId), toDocument(rows.get(0)));
    }

    public boolean deleteQuestion(Long questionId) {
        if (questionId == null || !ensureQuestionIndex()) {
            return false;
        }
        return elasticsearch.deleteDocument(elasticsearch.questionIndex(), String.valueOf(questionId));
    }

    public Map<String, Object> rebuildAll() {
        if (!ensureQuestionIndex()) {
            return Map.of("accepted", false, "indexed", 0, "failed", 0, "indexName", elasticsearch.questionIndex());
        }
        List<InterviewQuestionPO> rows = questionMapper.selectAllIndexable(10000);
        int indexed = 0;
        int failed = 0;
        for (InterviewQuestionPO row : rows) {
            if (elasticsearch.indexDocument(elasticsearch.questionIndex(), String.valueOf(row.getId()), toDocument(row))) {
                indexed++;
            } else {
                failed++;
            }
        }
        return Map.of("accepted", true, "indexed", indexed, "failed", failed, "total", rows.size(), "indexName", elasticsearch.questionIndex());
    }

    public Optional<List<QuestionSearchHit>> search(QuestionQuery query, int offset, int limit) {
        if (!ensureQuestionIndex()) {
            return Optional.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", buildQuery(query));
        body.put("sort", buildSort(query == null ? null : query.getSort()));
        body.put("from", offset);
        body.put("size", limit);
        if (!clean(query == null ? null : query.getKeyword()).isBlank()) {
            body.put("highlight", Map.of(
                    "pre_tags", List.of("<em>"),
                    "post_tags", List.of("</em>"),
                    "fields", Map.of(
                            "questionText", Map.of("number_of_fragments", 0),
                            "examPoint", Map.of("number_of_fragments", 0),
                            "answerHint", Map.of("fragment_size", 120, "number_of_fragments", 1)
                    )
            ));
        }
        return elasticsearch.search(elasticsearch.questionIndex(), body).map(this::extractHits);
    }

    private Map<String, Object> toDocument(InterviewQuestionPO row) {
        List<Map<String, Object>> tags = questionTagMapper.selectTagsByQuestionIds(List.of(row.getId())).stream()
                .map(tag -> {
                    Map<String, Object> doc = new LinkedHashMap<>();
                    doc.put("id", tag.getId());
                    doc.put("name", tag.getTagName());
                    doc.put("tagType", tag.getTagType());
                    return doc;
                })
                .toList();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", String.valueOf(row.getId()));
        doc.put("questionId", row.getId());
        doc.put("canonicalId", row.getCanonicalId());
        doc.put("questionText", nullToEmpty(row.getQuestionText()));
        doc.put("answerHint", nullToEmpty(row.getAnswerHint()));
        doc.put("examPoint", nullToEmpty(row.getExamPoint()));
        doc.put("referenceAnswer", nullToEmpty(row.getReferenceAnswer()));
        doc.put("sourceSnippet", nullToEmpty(row.getSourceSnippet()));
        doc.put("qualityReason", nullToEmpty(row.getQualityReason()));
        doc.put("company", nullToEmpty(row.getCompany()));
        doc.put("position", nullToEmpty(row.getPosition()));
        doc.put("interviewRound", nullToEmpty(row.getInterviewRound()));
        doc.put("difficulty", nullToEmpty(row.getDifficulty()));
        doc.put("sourcePostId", row.getSourcePostId());
        doc.put("sourceAuthorUid", row.getSourceAuthorUid());
        doc.put("status", row.getStatus());
        doc.put("appearCount", row.getAppearCount() == null ? 0 : row.getAppearCount());
        doc.put("qualityScore", row.getQualityScore() == null ? 0 : row.getQualityScore());
        doc.put("tagNames", tags.stream().map(tag -> String.valueOf(tag.get("name"))).toList());
        doc.put("tags", tags);
        doc.put("createTime", row.getCreateTime() == null ? 0L : row.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli());
        doc.put("updateTime", row.getUpdateTime() == null ? 0L : row.getUpdateTime().toInstant(ZoneOffset.UTC).toEpochMilli());
        return doc;
    }

    private Map<String, Object> buildQuery(QuestionQuery query) {
        QuestionQuery q = query == null ? new QuestionQuery() : query;
        List<Object> must = new ArrayList<>();
        List<Object> filter = new ArrayList<>();
        String keyword = clean(q.getKeyword());
        if (keyword.isBlank()) {
            must.add(Map.of("match_all", Map.of()));
        } else {
            must.add(Map.of("multi_match", Map.of(
                    "query", keyword,
                    "fields", List.of("questionText^3", "examPoint^2", "answerHint", "referenceAnswer", "sourceSnippet", "company^2", "position", "tagNames"),
                    "type", "best_fields",
                    "operator", "or"
            )));
        }
        filter.add(Map.of("term", Map.of("status", QuestionConstants.QUESTION_APPROVED)));
        addMatchFilter(filter, "company", q.getCompany());
        addTermFilter(filter, "position", q.getPosition());
        addTermFilter(filter, "difficulty", q.getDifficulty());
        addTermFilter(filter, "interviewRound", q.getRound());
        if (q.getStartTime() != null || q.getEndTime() != null) {
            Map<String, Object> range = new LinkedHashMap<>();
            if (q.getStartTime() != null) {
                range.put("gte", q.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli());
            }
            if (q.getEndTime() != null) {
                range.put("lte", q.getEndTime().toInstant(ZoneOffset.UTC).toEpochMilli());
            }
            filter.add(Map.of("range", Map.of("createTime", range)));
        }
        if (q.getTagIds() != null && !q.getTagIds().isEmpty()) {
            filter.add(Map.of("nested", Map.of(
                    "path", "tags",
                    "query", Map.of("terms", Map.of("tags.id", q.getTagIds()))
            )));
        }
        return Map.of("bool", Map.of("must", must, "filter", filter));
    }

    private List<Object> buildSort(String sort) {
        String value = clean(sort);
        if ("relevance".equals(value)) {
            return List.of(Map.of("_score", Map.of("order", "desc")), Map.of("createTime", Map.of("order", "desc")));
        }
        if ("appear".equals(value)) {
            return List.of(Map.of("appearCount", Map.of("order", "desc")), Map.of("createTime", Map.of("order", "desc")));
        }
        if ("hot".equals(value)) {
            return List.of(Map.of("qualityScore", Map.of("order", "desc")), Map.of("appearCount", Map.of("order", "desc")));
        }
        return List.of(Map.of("createTime", Map.of("order", "desc")), Map.of("questionId", Map.of("order", "desc")));
    }

    private List<QuestionSearchHit> extractHits(JsonNode root) {
        List<QuestionSearchHit> hitsResult = new ArrayList<>();
        JsonNode hits = root.path("hits").path("hits");
        if (!hits.isArray()) {
            return hitsResult;
        }
        for (JsonNode hit : hits) {
            long id = hit.path("_source").path("questionId").asLong(0L);
            if (id > 0) {
                hitsResult.add(new QuestionSearchHit(
                        id,
                        firstHighlight(hit, "questionText").orElse(null),
                        firstHighlight(hit, "examPoint").orElse(null),
                        firstHighlight(hit, "answerHint").orElse(null)
                ));
            }
        }
        return hitsResult;
    }

    private Optional<String> firstHighlight(JsonNode hit, String field) {
        JsonNode values = hit.path("highlight").path(field);
        if (!values.isArray() || values.isEmpty()) {
            return Optional.empty();
        }
        String value = values.get(0).asText("");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private Map<String, Object> questionIndexMapping() {
        Map<String, Object> props = baseQuestionProperties();
        props.put("tags", Map.of("type", "nested", "properties", Map.of(
                "id", Map.of("type", "long"),
                "name", Map.of("type", "keyword"),
                "tagType", Map.of("type", "integer")
        )));
        return Map.of(
                "settings", Map.of("number_of_shards", 1, "number_of_replicas", 0, "refresh_interval", "1s"),
                "mappings", Map.of("dynamic", "strict", "properties", props)
        );
    }

    private boolean ensureStructuredFieldMapping() {
        return elasticsearch.updateMapping(elasticsearch.questionIndex(), structuredFieldProperties());
    }

    private Map<String, Object> baseQuestionProperties() {
        Map<String, Object> keyword = Map.of("type", "keyword");
        Map<String, Object> text = Map.of("type", "text", "fields", Map.of("keyword", keyword));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", keyword);
        props.put("questionId", Map.of("type", "long"));
        props.put("canonicalId", Map.of("type", "long"));
        props.put("questionText", text);
        props.put("answerHint", Map.of("type", "text"));
        props.put("examPoint", text);
        props.put("referenceAnswer", Map.of("type", "text"));
        props.put("sourceSnippet", Map.of("type", "text"));
        props.put("qualityReason", Map.of("type", "text"));
        props.put("company", text);
        props.put("position", keyword);
        props.put("interviewRound", keyword);
        props.put("difficulty", keyword);
        props.put("sourcePostId", Map.of("type", "long"));
        props.put("sourceAuthorUid", Map.of("type", "long"));
        props.put("status", Map.of("type", "integer"));
        props.put("appearCount", Map.of("type", "integer"));
        props.put("qualityScore", Map.of("type", "integer"));
        props.put("tagNames", keyword);
        props.put("createTime", Map.of("type", "date", "format", "epoch_millis"));
        props.put("updateTime", Map.of("type", "date", "format", "epoch_millis"));
        return props;
    }

    private Map<String, Object> structuredFieldProperties() {
        Map<String, Object> keyword = Map.of("type", "keyword");
        Map<String, Object> text = Map.of("type", "text", "fields", Map.of("keyword", keyword));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("examPoint", text);
        props.put("referenceAnswer", Map.of("type", "text"));
        props.put("sourceSnippet", Map.of("type", "text"));
        props.put("qualityReason", Map.of("type", "text"));
        return props;
    }

    private void addMatchFilter(List<Object> filter, String field, String value) {
        if (!clean(value).isBlank()) {
            filter.add(Map.of("match_phrase", Map.of(field, clean(value))));
        }
    }

    private void addTermFilter(List<Object> filter, String field, String value) {
        if (!clean(value).isBlank()) {
            filter.add(Map.of("term", Map.of(field, clean(value))));
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record QuestionSearchHit(Long questionId,
                                    String highlightQuestionText,
                                    String highlightExamPoint,
                                    String highlightAnswerHint) {
    }
}

package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostContentRevisionGuardTest {

    @Test
    void effectiveRevisionMustBeCandidateThenActivatedOrRejected() throws Exception {
        String app = read("src/main/java/com/offerlab/community/post/application/PostApplicationService.java");
        String history = read("src/main/java/com/offerlab/community/post/application/PostVersionHistoryService.java");
        String mapper = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostVersionHistoryMapper.java");
        String po = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/po/PostVersionHistoryPO.java");

        assertTrue(history.contains("isEligiblePublicTextRevision"),
                "only normalized public title/body changes may create a quality revision");
        assertTrue(history.contains("normalizePublicText"),
                "whitespace-only changes must not create a revision boundary");
        assertTrue(history.contains("boolean qualityRevisionCandidate"),
                "candidate history writes must be explicit");
        assertTrue(history.contains("po.setQualitySignalRevisionState(\"CANDIDATE\")"),
                "eligible updates must persist a pending candidate");
        assertTrue(history.contains("po.setQualitySignalRevision(po.getResultVersion())"),
                "candidate must reuse the authoritative resulting post version");
        assertTrue(history.contains("UUID.randomUUID().toString()"),
                "the externally visible revision reference must be opaque");
        assertTrue(history.contains("activateQualityRevision"),
                "direct updates and approvals must activate the candidate explicitly");
        assertTrue(history.contains("rejectQualityRevision"),
                "rejected review candidates must never become effective");
        assertTrue(history.contains("qualitySignalSchemaColumnCount() == 6"),
                "candidate writes must fail closed until every required field is present");
        assertTrue(history.contains("effective content revision boundary is unavailable"),
                "candidate write and activation failures must be observable failures");

        int update = app.indexOf("if (!postRepo.update(post))");
        int activate = app.indexOf("versionHistoryService.activateDirectPublicRevision");
        assertTrue(update >= 0 && activate > update,
                "a direct public update must activate only after its post mutation succeeds");
        assertTrue(app.contains("versionHistoryService.resolvePendingPublicRevision(post.getId(), post.getVersion(), approved)"),
                "both review exits must settle the candidate state");

        assertTrue(mapper.contains("quality_signal_revision_state = 'CANDIDATE'"),
                "activation and rejection must compare-and-set only pending candidates");
        assertTrue(mapper.contains("quality_signal_revision_state = 'EFFECTIVE'"),
                "only an explicit effective state is readable as a boundary");
        assertTrue(mapper.contains("quality_signal_revision_state = 'REJECTED'"),
                "rejections must be persisted distinctly");
        assertTrue(mapper.contains("quality_signal_revision_state = 'SUPERSEDED'"),
                "replaced pending candidates must be excluded explicitly");
        assertTrue(mapper.contains("latest_effective_content_revision_token"),
                "canonical post state must point to the latest effective candidate");
        assertTrue(po.contains("qualitySignalRevisionToken"),
                "history PO must map the opaque candidate token");
    }

    @Test
    void readContractMustBeOpaqueAndNeverConfuseUnavailableWithNoRevision() throws Exception {
        String facade = read("src/main/java/com/offerlab/community/post/api/quality/PostContentRevisionQueryFacade.java");
        String query = read("src/main/java/com/offerlab/community/post/api/quality/PostContentRevisionQuery.java");
        String result = read("src/main/java/com/offerlab/community/post/api/quality/PostContentRevisionQueryResult.java");
        String snapshot = read("src/main/java/com/offerlab/community/post/api/quality/PostContentRevisionSnapshot.java");
        String service = read("src/main/java/com/offerlab/community/post/application/PostContentRevisionQueryService.java");
        String pageQuery = read("src/main/java/com/offerlab/community/post/api/quality/PostPublicRevisionBoundaryPageQuery.java");
        String pageResult = read("src/main/java/com/offerlab/community/post/api/quality/PostPublicRevisionBoundaryPageResult.java");
        String projection = read("src/main/java/com/offerlab/community/post/api/quality/PostPublicRevisionBoundaryProjection.java");

        assertTrue(facade.contains("PostContentRevisionQueryResult query(PostContentRevisionQuery query)"));
        assertTrue(query.contains("AUTHOR_OWNED") && query.contains("AUTHORIZED_CHANNEL"),
                "the API must support owner and pre-authorized channel scopes");
        for (String field : new String[]{
                "windowStart",
                "hasEffectiveRevision",
                "revisionToken",
                "effectivePublishedPostVersion",
                "FOUND",
                "NO_EFFECTIVE_REVISION",
                "NOT_ELIGIBLE",
                "UNAVAILABLE"
        }) {
            assertTrue(snapshot.contains(field), "snapshot must expose " + field);
        }
        assertTrue(result.contains("boolean available"), "callers must see source readiness separately");
        assertTrue(service.contains("return unavailable(query)"),
                "schema/query failures must return UNAVAILABLE instead of an empty success");
        assertTrue(service.contains("qualitySignalSchemaColumnCount() != REQUIRED_QUALITY_SIGNAL_COLUMNS"),
                "readiness must be checked before querying new fields");
        assertTrue(service.contains("quality_signal_revision_state") == false,
                "service must consume typed mapper projections rather than construct SQL");
        assertFalse(snapshot.contains("String title"));
        assertFalse(snapshot.contains("String content"));
        assertFalse(snapshot.contains("hash"));
        assertFalse(snapshot.contains("reader"));
        assertTrue(facade.contains("queryPublicRevisionBoundaryPage"),
                "the same facade must expose the bounded channel projection");
        assertTrue(service.contains("Boolean.TRUE.equals(row.getAnonymous())"),
                "anonymous career content must never enter the public quality projection");
        assertTrue(read("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostVersionHistoryMapper.java")
                        .contains("JSON_EXTRACT(e.ext_json, '$.anonymous')"),
                "the page query must exclude anonymous career content before pagination");
        assertTrue(pageQuery.contains("MAX_PAGE_SIZE = 100"),
                "channel projection pages must remain bounded");
        assertTrue(pageQuery.contains("snapshotUpperBoundPostId"),
                "continuations must retain the first-page high-water mark");
        assertTrue(pageResult.contains("boolean available"),
                "unavailable projection reads must remain distinct from no-revision rows");
        for (String field : new String[]{
                "postId",
                "domain",
                "authorId",
                "publicEligible",
                "windowStart",
                "revisionToken",
                "EFFECTIVE_REVISION",
                "NO_EFFECTIVE_REVISION"
        }) {
            assertTrue(projection.contains(field), "page projection must expose " + field);
        }
        assertFalse(projection.contains("reader"));
        assertFalse(projection.contains("viewCount"));
        assertFalse(projection.contains("feedback"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

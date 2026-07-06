package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class V4KnowledgeAssetGuardTest {

    private static final String DTO_DIR = "src/main/java/com/offerlab/community/post/api/dto/";

    @Test
    void phaseFiveKnowledgeAssetContractsAndGuardsStayPublicOnly() {
        List<String> failures = new ArrayList<>();

        Map<String, List<String>> contracts = Map.of(
                "PublicKnowledgeAssetDTO", List.of(
                        "assetId", "assetType", "title", "summary", "assetStatus", "visibilityState",
                        "source", "previewSource", "sourceNote", "targetHref", "updatedAt"),
                "KnowledgeRelationDTO", List.of(
                        "relationId", "sourceAssetId", "targetAssetId", "relationType", "reasonText",
                        "source", "reviewStatus", "riskLevel", "createdAt"),
                "KnowledgePathDTO", List.of(
                        "pathId", "title", "summary", "entryAssetId", "steps", "sourceRefs",
                        "pathStatus", "displayState", "updatedAt"),
                "KnowledgeGapDTO", List.of(
                        "gapId", "title", "reasonText", "source", "sourceRefs", "minSampleMet",
                        "reviewStatus", "targetStage"),
                "KnowledgeAssetSnapshotDTO", List.of(
                        "snapshotId", "assetId", "assetType", "title", "summary", "sections",
                        "relations", "sourceNote", "archivedAt")
        );

        StringBuilder contractSurface = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : contracts.entrySet()) {
            String source = read(DTO_DIR + entry.getKey() + ".java", failures);
            contractSurface.append(source).append('\n');
            has(source, "public\\s+class\\s+" + entry.getKey() + "\\b", failures,
                    entry.getKey() + " must define the public phase-5 contract.");
            for (String field : entry.getValue()) {
                has(source, "private\\s+[^;]+\\s+" + field + ";", failures,
                        entry.getKey() + " must include field " + field + ".");
            }
        }

        for (String enumName : List.of(
                "KnowledgeAssetStatus",
                "KnowledgeVisibilityState",
                "KnowledgePreviewSource",
                "KnowledgePathStatus",
                "KnowledgePathDisplayState",
                "KnowledgeAssetSource",
                "KnowledgeRelationSource")) {
            String source = read(DTO_DIR + enumName + ".java", failures);
            contractSurface.append(source).append('\n');
            has(source, "@JsonValue", failures, enumName + " must serialize explicit API values.");
        }

        String assetStatus = read(DTO_DIR + "KnowledgeAssetStatus.java", failures);
        String pathStatus = read(DTO_DIR + "KnowledgePathStatus.java", failures);
        String assetSource = read(DTO_DIR + "KnowledgeAssetSource.java", failures);
        String relationSource = read(DTO_DIR + "KnowledgeRelationSource.java", failures);
        String service = read("src/main/java/com/offerlab/community/post/application/KnowledgeRelationService.java", failures);
        String controller = read("src/main/java/com/offerlab/community/post/controller/KnowledgeController.java", failures);

        has(assetStatus, "ACTIVE\\(\"active\"\\)[\\s\\S]*ARCHIVED\\(\"archived\"\\)", failures,
                "assetStatus must only be the persistent lifecycle active/archived.");
        missing(assetStatus, "DEGRADED|OFFLINE|DRAFT|REVIEW|VISIBLE|EXCLUDED", failures,
                "assetStatus must not contain response, moderation, draft, or offline states.");
        has(pathStatus, "ACTIVE\\(\"active\"\\)[\\s\\S]*ARCHIVED\\(\"archived\"\\)", failures,
                "pathStatus must only be the persistent lifecycle active/archived.");
        missing(pathStatus, "DEGRADED|OFFLINE|NORMAL|PARTIAL", failures,
                "pathStatus must not contain response display or offline states.");
        missing(assetSource, "FALLBACK|DEMO|LOCAL|OFFLINE|DEGRADED", failures,
                "formal asset source must reject fallback/demo/local/offline/degraded.");
        missing(relationSource, "FALLBACK|DEMO|LOCAL|OFFLINE|DEGRADED", failures,
                "formal relation source must reject fallback/demo/local/offline/degraded.");

        has(controller, "@GetMapping\\(\"/assets\"\\)", failures,
                "KnowledgeController must expose /api/v1/knowledge/assets.");
        has(service, "knowledgeAssets\\(", failures,
                "KnowledgeRelationService must aggregate phase-5 knowledge assets.");
        has(service, "previewSource[\\s\\S]*remote", failures,
                "formal backend assets must use remote previewSource.");
        has(service, "assetStatus[\\s\\S]*active[\\s\\S]*archived", failures,
                "backend must model active/archived assetStatus.");
        has(service, "displayState[\\s\\S]*normal[\\s\\S]*degraded", failures,
                "backend must keep displayState as response state only.");
        has(service, "(OFFLINE|STATUS_OFFLINE|offline)[\\s\\S]*(excluded|ordinary|path|filter|skip)", failures,
                "OFFLINE objects must not enter ordinary knowledge paths.");
        has(service, "(ARCHIVED|archived)[\\s\\S]*(visibilityState|archived|revisit|snapshot)", failures,
                "ARCHIVED objects may be revisited but must be marked archived.");
        has(service, "(fallback|demo|local)[\\s\\S]*(asset|relation|path|gap)[\\s\\S]*(reject|filter|skip|return false)", failures,
                "fallback/demo/local-only objects must not enter formal assets, relations, paths, or gaps.");
        has(service, "(draft|review|private|visibility|post_status|topicStatus)[\\s\\S]*(filter|skip|return false|selectPublic|batchGetPosts)", failures,
                "draft, reviewing, private, and invisible content must be filtered before formal assets.");

        for (String forbidden : List.of(
                "uid", "userId", "ipAddress", "clientIp", "deviceId", "fingerprint",
                "singleUser", "singleSearch", "privateFavorite", "privateCollection",
                "privateLearningRecord", "learningRecord", "draftBody", "previewToken")) {
            missing(contractSurface.toString(), "\\b" + forbidden + "\\b", failures,
                    "formal knowledge asset contracts must not expose private/single-user field: " + forbidden + ".");
        }

        assertTrue(failures.isEmpty(), () -> "V4 knowledge asset Java guard failed:\n- " + String.join("\n- ", failures));
    }

    private static String read(String path, List<String> failures) {
        Path sourcePath = Path.of(path);
        if (!Files.exists(sourcePath)) {
            failures.add(path + " must exist.");
            return "";
        }
        try {
            return Files.readString(sourcePath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            failures.add(path + " must be readable: " + e.getMessage());
            return "";
        }
    }

    private static void has(String source, String regex, List<String> failures, String message) {
        if (!Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(source).find()) {
            failures.add(message);
        }
    }

    private static void missing(String source, String regex, List<String> failures, String message) {
        if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(source).find()) {
            failures.add(message);
        }
    }
}

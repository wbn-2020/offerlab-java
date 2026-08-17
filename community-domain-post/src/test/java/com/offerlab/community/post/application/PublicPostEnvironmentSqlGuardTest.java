package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicPostEnvironmentSqlGuardTest {

    private static final Path REPOSITORY_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void publicRevisionBoundarySqlAndServiceMustRequireCommunity() throws Exception {
        String mapper = read("community-domain-post/src/main/java/com/offerlab/community/post/"
                + "infrastructure/persistence/mapper/PostVersionHistoryMapper.java");
        String service = read("community-domain-post/src/main/java/com/offerlab/community/post/"
                + "application/PostContentRevisionQueryService.java");

        assertSelectRequires(mapper, "selectMaxPublicContentRevisionBoundaryPostId",
                "p.content_environment = 'COMMUNITY'");
        assertSelectRequires(mapper, "selectPublicContentRevisionBoundaryRows",
                "p.content_environment = 'COMMUNITY'");
        assertSelectRequires(mapper, "existsPublicContentRevisionBoundaryAfter",
                "p.content_environment = 'COMMUNITY'");
        assertSelectRequires(mapper, "listPublicUpdates",
                "p.content_environment = 'COMMUNITY'");
        assertSelectRequires(mapper, "selectContentRevisionQueryRows",
                "p.content_environment AS contentEnvironment");
        assertTrue(service.contains("Post.isCommunityContent(row.getContentEnvironment())"),
                "service-level revision boundary validation must fail closed to COMMUNITY");
    }

    @Test
    void publicRelationHealthSqlMustTreatNonCommunityTargetsAsInvalid() throws Exception {
        String mapper = read("community-domain-analytics/src/main/java/com/offerlab/community/analytics/"
                + "infrastructure/persistence/mapper/ProjectionHealthMapper.java");

        assertSelectRequires(mapper, "selectInvalidPublicRelationTargetHealth",
                "COALESCE(target_post.content_environment, '') <> 'COMMUNITY'");
        assertSelectRequires(mapper, "listInvalidPublicRelationTargetIssues",
                "COALESCE(target_post.content_environment, '') <> 'COMMUNITY'");
    }

    @Test
    void incentivePublicEligibilitySqlMustRequireCommunity() throws Exception {
        String mapper = read("community-domain-incentive/src/main/java/com/offerlab/community/incentive/"
                + "infrastructure/IncentiveMapper.java");

        assertSelectRequires(mapper, "selectPublicThankTargetAuthor",
                "p.content_environment = 'COMMUNITY'", 2);
        assertSelectRequires(mapper, "selectPublicPostAuthor",
                "p.content_environment = 'COMMUNITY'");
        assertSelectRequires(mapper, "selectPostDomainCode",
                "p.content_environment = 'COMMUNITY'");
        assertSelectRequires(mapper, "countQualifiedPublicPostsByAuthor",
                "p.content_environment = 'COMMUNITY'");
        assertSelectRequires(mapper, "countQualifiedPublicPost",
                "p.content_environment = 'COMMUNITY'");
        assertSelectRequires(mapper, "isFirstQualifiedPublicPost",
                "p.content_environment = 'COMMUNITY'");
    }

    @Test
    void postDerivedUserMaterialSqlMustFailClosedToCommunity() throws Exception {
        String mapper = read("community-domain-question/src/main/java/com/offerlab/community/question/"
                + "infrastructure/persistence/mapper/InterviewMaterialPackMapper.java");

        assertSelectRequires(mapper, "selectByUserFiltered",
                "p.content_environment = 'COMMUNITY'");
        assertSelectRequires(mapper, "countByUserFiltered",
                "p.content_environment = 'COMMUNITY'");
    }

    private static void assertSelectRequires(String source, String methodName, String requiredFragment) {
        assertSelectRequires(source, methodName, requiredFragment, 1);
    }

    private static void assertSelectRequires(String source, String methodName, String requiredFragment,
                                             int expectedOccurrences) {
        String sql = selectSql(source, methodName);
        int occurrences = countOccurrences(sql, requiredFragment);
        assertTrue(occurrences >= expectedOccurrences,
                methodName + " must contain " + expectedOccurrences + " occurrence(s) of " + requiredFragment);
    }

    private static String selectSql(String source, String methodName) {
        int methodStart = source.indexOf(" " + methodName + "(");
        assertTrue(methodStart >= 0, methodName + " must exist");
        int selectStart = source.lastIndexOf("@Select", methodStart);
        assertTrue(selectStart >= 0, methodName + " must have an @Select query");
        return source.substring(selectStart, methodStart);
    }

    private static int countOccurrences(String source, String fragment) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(fragment, offset)) >= 0) {
            count++;
            offset += fragment.length();
        }
        return count;
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(REPOSITORY_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}

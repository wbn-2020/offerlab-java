package com.offerlab.community.post.collaboration.application;

import com.offerlab.community.post.collaboration.infrastructure.persistence.NeedDiscoveryMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeedDiscoveryLoginSqlGuardTest {

    @Test
    void anonymousAndAuthenticatedBranchesMustLeaveValidSelectSeparators() throws Exception {
        String mapper = Files.readString(Path.of(
                "src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/"
                        + "NeedDiscoveryMapper.java"), StandardCharsets.UTF_8);

        assertTrue(mapper.contains("0 AS viewerDomainMatch,\n"
                        + "                       0 AS viewerFormatMatch,"),
                "anonymous branch must terminate its computed columns with a comma");
        assertTrue(mapper.contains(") THEN 1 ELSE 0 END AS viewerFormatMatch,\n"
                        + "                     </otherwise>"),
                "authenticated branch must separate viewerFormatMatch from createTime");
        assertTrue(mapper.contains("</choose>\n"
                        + "                   n.create_time AS createTime"),
                "both branches must be followed by the common createTime column");

        String anonymous = renderSelect(null);
        String authenticated = renderSelect(42L);
        assertRenderedSelectHasSeparator(anonymous, null);
        assertRenderedSelectHasSeparator(authenticated, 42L);
        assertEquals(selectAliases(anonymous), selectAliases(authenticated),
                "anonymous and authenticated branches must expose the same SELECT aliases");
    }

    private static void assertRenderedSelectHasSeparator(String normalized, Long uid) {
        assertTrue(!normalized.contains("viewerFormatMatch, ,")
                        && !normalized.contains("viewerFormatMatch n.create_time"),
                () -> "rendered SELECT has a malformed viewerFormatMatch separator for uid=" + uid
                        + ": " + normalized);
        assertTrue(normalized.contains("AS viewerFormatMatch, n.create_time AS createTime"),
                () -> "rendered SELECT is missing the viewerFormatMatch separator for uid=" + uid
                        + ": " + normalized);
    }

    private static String renderSelect(Long uid) {
        Select annotation = java.util.Arrays.stream(NeedDiscoveryMapper.class.getDeclaredMethods())
                .filter(method -> "listNeeds".equals(method.getName()))
                .findFirst()
                .orElseThrow()
                .getAnnotation(Select.class);
        Configuration configuration = new Configuration();
        SqlSource sqlSource = configuration.getLanguageDriver(null).createSqlSource(
                configuration, String.join("\n", annotation.value()), Map.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("uid", uid);
        parameters.put("domain", null);
        parameters.put("status", null);
        parameters.put("contentFormat", null);
        parameters.put("sourceType", null);
        parameters.put("keyword", null);
        parameters.put("sort", "LATEST");
        parameters.put("cursorTime", null);
        parameters.put("cursorId", null);
        parameters.put("cursorStalled", 0);
        parameters.put("limit", 11);

        BoundSql boundSql = sqlSource.getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }

    private static String selectAliases(String normalized) {
        int from = normalized.indexOf(" FROM t_collab_content_need n ");
        assertTrue(from > 0, () -> "rendered SELECT has no main FROM clause: " + normalized);
        return java.util.regex.Pattern.compile("\\bAS\\s+([A-Za-z][A-Za-z0-9_]*)")
                .matcher(normalized.substring(0, from))
                .results()
                .map(match -> match.group(1))
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }
}

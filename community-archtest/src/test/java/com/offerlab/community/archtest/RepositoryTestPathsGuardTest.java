package com.offerlab.community.archtest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryTestPathsGuardTest {

    @Test
    void architectureTestsMustNotDependOnParentRelativeWorkingDirectories() throws Exception {
        Path testSources = RepositoryTestPaths.resolve("community-archtest/src/test/java");
        List<Path> javaFiles;
        try (var paths = Files.walk(testSources)) {
            javaFiles = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }

        assertFalse(javaFiles.isEmpty(), "architecture test sources must be discoverable");
        String parentRelativeUnix = "\"" + ".." + "/";
        String parentRelativeWindows = "\"" + ".." + "\\";
        String ignoredProductionConfig = "application-" + "prod.yml";
        String ignoredLocalConfig = "application-" + "local.yml";
        String sharedLocalConfig = "community-bootstrap/src/main/resources/" + ignoredLocalConfig;
        String gitignore = Files.readString(RepositoryTestPaths.resolve(".gitignore"), StandardCharsets.UTF_8);
        boolean localConfigIsShared = gitignore.lines()
                .map(String::trim)
                .anyMatch(("!" + sharedLocalConfig)::equals);
        if (localConfigIsShared) {
            assertTrue(Files.isRegularFile(RepositoryTestPaths.resolve(sharedLocalConfig)),
                    "the shared local profile must exist when it is explicitly unignored");
        }
        for (Path javaFile : javaFiles) {
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            assertFalse(source.contains(parentRelativeUnix),
                    () -> javaFile + " must resolve repository files through RepositoryTestPaths");
            assertFalse(source.contains(parentRelativeWindows),
                    () -> javaFile + " must not depend on a Windows parent-relative working directory");
            assertFalse(source.contains(ignoredProductionConfig),
                    () -> javaFile + " must not depend on an ignored production config");
            if (source.contains(ignoredLocalConfig)) {
                assertTrue(localConfigIsShared,
                        () -> javaFile + " must not depend on an ignored local config");
                assertFalse(source.replace(sharedLocalConfig, "").contains(ignoredLocalConfig),
                        () -> javaFile + " may only reference the precisely shared local profile");
            }
        }
        assertTrue(javaFiles.contains(testSources.resolve(
                "com/offerlab/community/archtest/ProductionSecurityGuardTest.java")));
    }
}

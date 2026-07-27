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
        for (Path javaFile : javaFiles) {
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            assertFalse(source.contains(parentRelativeUnix),
                    () -> javaFile + " must resolve repository files through RepositoryTestPaths");
            assertFalse(source.contains(parentRelativeWindows),
                    () -> javaFile + " must not depend on a Windows parent-relative working directory");
            assertFalse(source.contains(ignoredProductionConfig),
                    () -> javaFile + " must not depend on an ignored production config");
            assertFalse(source.contains(ignoredLocalConfig),
                    () -> javaFile + " must not depend on an ignored local config");
        }
        assertTrue(javaFiles.contains(testSources.resolve(
                "com/offerlab/community/archtest/ProductionSecurityGuardTest.java")));
    }
}

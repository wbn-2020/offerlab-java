package com.offerlab.community.archtest;

import java.nio.file.Files;
import java.nio.file.Path;

final class RepositoryTestPaths {
    private static final Path ROOT = findRepositoryRoot();

    private RepositoryTestPaths() {
    }

    static Path resolve(String relativePath) {
        return ROOT.resolve(relativePath).normalize();
    }

    private static Path findRepositoryRoot() {
        Path fromWorkingDirectory = findFrom(Path.of("").toAbsolutePath().normalize());
        if (fromWorkingDirectory != null) {
            return fromWorkingDirectory;
        }

        String multiModuleDirectory = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleDirectory != null && !multiModuleDirectory.isBlank()) {
            Path fromMaven = findFrom(Path.of(multiModuleDirectory).toAbsolutePath().normalize());
            if (fromMaven != null) {
                return fromMaven;
            }
        }

        throw new IllegalStateException(
                "Unable to locate OfferLab repository root from " + Path.of("").toAbsolutePath());
    }

    private static Path findFrom(Path start) {
        for (Path current = start; current != null; current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("community-bootstrap"))
                    && Files.isDirectory(current.resolve("community-archtest"))) {
                return current;
            }
        }
        return null;
    }
}

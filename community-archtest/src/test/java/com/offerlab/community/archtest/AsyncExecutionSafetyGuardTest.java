package com.offerlab.community.archtest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncExecutionSafetyGuardTest {

    @Test
    void globalAsyncExecutionMustUseAnExplicitBoundedExecutorBaseline() throws Exception {
        String application = read("community-bootstrap/src/main/java/com/offerlab/community/CommunityApplication.java");
        String asyncConfig = read("community-bootstrap/src/main/java/com/offerlab/community/config/GlobalAsyncExecutionConfig.java");

        assertTrue(application.contains("@EnableAsync"),
                "community application must keep async processing enabled");
        assertTrue(asyncConfig.contains("implements AsyncConfigurer"),
                "global async baseline must explicitly provide the default async executor");
        assertTrue(asyncConfig.contains("ThreadPoolTaskExecutor"),
                "global async baseline must use a Spring-managed thread pool");
        assertTrue(asyncConfig.contains("THREAD_NAME_PREFIX") && asyncConfig.contains("setThreadNamePrefix(threadNamePrefix)"),
                "global async baseline must expose a stable thread name prefix");
        assertTrue(asyncConfig.contains("QUEUE_CAPACITY") && asyncConfig.contains("setQueueCapacity(queueCapacity)"),
                "global async baseline must use a bounded queue");
        assertTrue(asyncConfig.contains("setRejectedExecutionHandler(new LoggingCallerRunsPolicy())"),
                "global async baseline must define an explicit rejection policy");
        assertTrue(asyncConfig.contains("CallerRunsPolicy"),
                "global async rejection policy must degrade by caller-runs instead of dropping work silently");
        assertTrue(asyncConfig.contains("getAsyncExecutor()"),
                "global async baseline must override Spring's implicit default executor resolution");
        assertFalse(asyncConfig.contains("SimpleAsyncTaskExecutor"),
                "global async baseline must not rely on the unbounded simple async executor");
    }

    private static String read(String path) throws Exception {
        return Files.readString(RepositoryTestPaths.resolve(path), StandardCharsets.UTF_8);
    }
}

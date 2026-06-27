package com.offerlab.community.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalAsyncExecutionConfigTest {

    @Test
    void defaultAsyncMethodsUseTheBoundedCommunityExecutor() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestAsyncContext.class)) {
            ThreadPoolTaskExecutor executor = context.getBean(GlobalAsyncExecutionConfig.DEFAULT_EXECUTOR_BEAN,
                    ThreadPoolTaskExecutor.class);
            AsyncConfigurer asyncConfigurer = context.getBean(AsyncConfigurer.class);
            AsyncProbeService probeService = context.getBean(AsyncProbeService.class);

            CompletableFuture<String> threadNameFuture = probeService.captureThreadName();
            String threadName = threadNameFuture.get(5, TimeUnit.SECONDS);

            assertSame(executor, asyncConfigurer.getAsyncExecutor());
            assertTrue(threadName.startsWith(GlobalAsyncExecutionConfig.THREAD_NAME_PREFIX));
            assertEquals(GlobalAsyncExecutionConfig.CORE_POOL_SIZE, executor.getThreadPoolExecutor().getCorePoolSize());
            assertEquals(GlobalAsyncExecutionConfig.MAX_POOL_SIZE, executor.getThreadPoolExecutor().getMaximumPoolSize());
            assertEquals(GlobalAsyncExecutionConfig.QUEUE_CAPACITY,
                    executor.getThreadPoolExecutor().getQueue().size()
                            + executor.getThreadPoolExecutor().getQueue().remainingCapacity());
            assertInstanceOf(GlobalAsyncExecutionConfig.LoggingCallerRunsPolicy.class,
                    executor.getThreadPoolExecutor().getRejectedExecutionHandler());
        }
    }

    @Test
    void asyncConfigurerExposesAnExceptionHandler() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestAsyncContext.class)) {
            AsyncConfigurer asyncConfigurer = context.getBean(AsyncConfigurer.class);
            AsyncUncaughtExceptionHandler handler = asyncConfigurer.getAsyncUncaughtExceptionHandler();

            assertNotNull(handler);
        }
    }

    @Configuration
    @EnableAsync
    @Import(GlobalAsyncExecutionConfig.class)
    static class TestAsyncContext {

        @Bean
        AsyncProbeService asyncProbeService() {
            return new AsyncProbeService();
        }
    }

    static class AsyncProbeService {

        @Async
        CompletableFuture<String> captureThreadName() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }
}

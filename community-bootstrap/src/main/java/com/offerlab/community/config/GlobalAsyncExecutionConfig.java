package com.offerlab.community.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class GlobalAsyncExecutionConfig implements AsyncConfigurer {
    public static final String DEFAULT_EXECUTOR_BEAN = "communityAsyncExecutor";
    public static final int CORE_POOL_SIZE = 4;
    public static final int MAX_POOL_SIZE = 8;
    public static final int QUEUE_CAPACITY = 200;
    public static final int KEEP_ALIVE_SECONDS = 60;
    public static final String THREAD_NAME_PREFIX = "offerlab-async-";

    private static final Logger log = LoggerFactory.getLogger(GlobalAsyncExecutionConfig.class);

    @Bean(name = DEFAULT_EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor communityAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new LoggingCallerRunsPolicy());
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return communityAsyncExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new LoggingAsyncUncaughtExceptionHandler();
    }

    static final class LoggingCallerRunsPolicy implements RejectedExecutionHandler {
        private final ThreadPoolExecutor.CallerRunsPolicy delegate = new ThreadPoolExecutor.CallerRunsPolicy();

        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
            log.warn("community async executor saturated; falling back to caller thread: poolSize={}, activeCount={}, queueSize={}",
                    executor.getPoolSize(), executor.getActiveCount(), executor.getQueue().size());
            delegate.rejectedExecution(runnable, executor);
        }
    }

    static final class LoggingAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("uncaught async error in method={}", method.getName(), ex);
        }
    }
}

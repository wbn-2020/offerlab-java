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
    public static final String AI_REVIEW_EXECUTOR_BEAN = "aiReviewAsyncExecutor";
    public static final String NOTIFICATION_EXECUTOR_BEAN = "notificationAsyncExecutor";
    public static final String FEED_FANOUT_EXECUTOR_BEAN = "feedFanoutAsyncExecutor";
    public static final int CORE_POOL_SIZE = 4;
    public static final int MAX_POOL_SIZE = 8;
    public static final int QUEUE_CAPACITY = 200;
    public static final int KEEP_ALIVE_SECONDS = 60;
    public static final String THREAD_NAME_PREFIX = "offerlab-async-";

    private static final Logger log = LoggerFactory.getLogger(GlobalAsyncExecutionConfig.class);

    @Bean(name = DEFAULT_EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor communityAsyncExecutor() {
        return buildExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY, THREAD_NAME_PREFIX);
    }

    @Bean(name = AI_REVIEW_EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor aiReviewAsyncExecutor() {
        return buildExecutor(1, 2, 20, "offerlab-ai-review-");
    }

    @Bean(name = NOTIFICATION_EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor notificationAsyncExecutor() {
        return buildExecutor(4, 8, 500, "offerlab-notify-");
    }

    @Bean(name = FEED_FANOUT_EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor feedFanoutAsyncExecutor() {
        return buildExecutor(2, 4, 50, "offerlab-feed-fanout-");
    }

    private ThreadPoolTaskExecutor buildExecutor(int corePoolSize, int maxPoolSize,
                                                 int queueCapacity, String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix(threadNamePrefix);
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
        private final String poolName;

        LoggingCallerRunsPolicy() {
            this("community");
        }

        private LoggingCallerRunsPolicy(String poolName) {
            this.poolName = poolName;
        }

        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
            log.warn("community async executor saturated; pool={} falling back to caller thread: poolSize={}, activeCount={}, queueSize={}",
                    poolName, executor.getPoolSize(), executor.getActiveCount(), executor.getQueue().size());
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

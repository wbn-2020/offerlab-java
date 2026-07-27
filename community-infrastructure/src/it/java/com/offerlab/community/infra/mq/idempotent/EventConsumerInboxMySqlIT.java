package com.offerlab.community.infra.mq.idempotent;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class EventConsumerInboxMySqlIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.33")
            .withDatabaseName("offerlab_it")
            .withUsername("offerlab")
            .withPassword("offerlab");

    private static HikariDataSource dataSource;
    private static IdempotentEventConsumer consumer;

    @BeforeAll
    static void setUp() throws Exception {
        dataSource = dataSource();
        createSchema(dataSource);
        EventConsumerInboxMapper mapper = new SqlSessionTemplate(
                sqlSessionFactory(dataSource)).getMapper(EventConsumerInboxMapper.class);
        consumer = transactionalConsumer(mapper, dataSource);
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void duplicateAndHandlerFailurePreserveTransactionalInboxSemantics() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> consumer.consume(
                        "post.published:42",
                        "POST_PUBLISHED",
                        "feed-fanout",
                        () -> {
                            throw new IllegalStateException("simulated handler failure");
                        }));
        assertEquals(0, inboxCount());

        AtomicInteger executions = new AtomicInteger();
        assertTrue(consumer.consume(
                "post.published:42",
                "POST_PUBLISHED",
                "feed-fanout",
                executions::incrementAndGet));
        assertFalse(consumer.consume(
                "post.published:42",
                "POST_PUBLISHED",
                "feed-fanout",
                executions::incrementAndGet));

        assertEquals(1, executions.get());
        assertEquals(1, inboxCount());
    }

    private static IdempotentEventConsumer transactionalConsumer(
            EventConsumerInboxMapper mapper,
            DataSource dataSource) {
        IdempotentEventConsumer target = new IdempotentEventConsumer(
                mapper,
                new IncrementingIdGenerator());
        TransactionInterceptor interceptor = new TransactionInterceptor(
                new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvice(interceptor);
        return (IdempotentEventConsumer) proxyFactory.getProxy();
    }

    private static int inboxCount() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM t_event_consumer_inbox")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment(
                "it",
                new SpringManagedTransactionFactory(),
                dataSource));
        configuration.addMapper(EventConsumerInboxMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE t_event_consumer_inbox (
                        id BIGINT NOT NULL,
                        consumer_name VARCHAR(64) NOT NULL,
                        idempotency_key VARCHAR(160) NOT NULL,
                        event_type VARCHAR(64) NULL,
                        create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        PRIMARY KEY (consumer_name, idempotency_key),
                        KEY idx_event_consumer_inbox_created (create_time, id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }

    private static final class IncrementingIdGenerator extends SnowflakeIdGenerator {
        private long value = 100L;

        @Override
        public synchronized long nextId() {
            return ++value;
        }
    }
}

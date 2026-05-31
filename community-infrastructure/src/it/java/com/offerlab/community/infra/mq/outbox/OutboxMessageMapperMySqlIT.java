package com.offerlab.community.infra.mq.outbox;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
class OutboxMessageMapperMySqlIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.33")
            .withDatabaseName("offerlab_it")
            .withUsername("offerlab")
            .withPassword("offerlab");

    private static HikariDataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUp() throws Exception {
        dataSource = dataSource();
        createSchema(dataSource);
        sqlSessionFactory = sqlSessionFactory(dataSource);
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void claimLeaseAndRetryTransitionsAreAtomicAgainstRealMySql() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            OutboxMessageMapper mapper = session.getMapper(OutboxMessageMapper.class);
            insertOutbox(session, 10L, OutboxMessageMapper.STATUS_PENDING, null, null, null, 0);
            insertOutbox(session, 20L, OutboxMessageMapper.STATUS_PENDING, LocalDateTime.now().plusHours(1), null, null, 0);
            insertOutbox(session, 30L, OutboxMessageMapper.STATUS_SENDING, null, "expired-owner", LocalDateTime.now().minusSeconds(1), 1);

            String owner = "it-owner";
            LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(1);
            assertEquals(2, mapper.claimPending(owner, lockUntil, 10));

            List<OutboxMessage> claimed = mapper.findClaimed(owner, 10);
            assertEquals(List.of(10L, 30L), claimed.stream().map(OutboxMessage::getId).toList());
            assertEquals(1, mapper.markSent(10L, owner));
            assertEquals(1, mapper.updateRetry(30L, owner, OutboxMessageMapper.STATUS_PENDING, 2, LocalDateTime.now().plusMinutes(5)));

            OutboxMessage future = mapper.findById(20L);
            assertNotNull(future);
            assertEquals(OutboxMessageMapper.STATUS_PENDING, future.getMsgStatus());
            assertEquals(0, mapper.findClaimed("expired-owner", 10).size());
        }
    }

    private static void insertOutbox(SqlSession session, Long id, Integer status, LocalDateTime nextRetryTime,
                                     String lockOwner, LocalDateTime lockUntil, Integer retryCount) {
        try (PreparedStatement ps = session.getConnection().prepareStatement("""
                INSERT INTO t_outbox_message (
                    id, aggregate_type, aggregate_id, topic, payload, msg_status,
                    retry_count, next_retry_time, lock_owner, lock_until
                ) VALUES (?, 'post', ?, 'post.published', '{}', ?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, id);
            ps.setLong(2, id);
            ps.setInt(3, status);
            ps.setInt(4, retryCount);
            ps.setObject(5, nextRetryTime);
            ps.setString(6, lockOwner);
            ps.setObject(7, lockUntil);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
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
        configuration.setEnvironment(new Environment("it", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(OutboxMessageMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE t_outbox_message (
                        id BIGINT NOT NULL PRIMARY KEY,
                        aggregate_type VARCHAR(64) NOT NULL,
                        aggregate_id BIGINT NOT NULL,
                        topic VARCHAR(64) NOT NULL,
                        payload JSON NOT NULL,
                        msg_status TINYINT NOT NULL DEFAULT 0,
                        retry_count INT NOT NULL DEFAULT 0,
                        lock_owner VARCHAR(128) NULL,
                        lock_until DATETIME(3) NULL,
                        next_retry_time DATETIME(3) NULL,
                        create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                        KEY idx_status_time (msg_status, next_retry_time),
                        KEY idx_lock_owner (lock_owner, lock_until)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }
}

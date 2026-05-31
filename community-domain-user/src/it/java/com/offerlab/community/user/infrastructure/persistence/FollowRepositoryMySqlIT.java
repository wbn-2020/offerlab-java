package com.offerlab.community.user.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.user.api.dto.FollowCursorDTO;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserCounterMapper;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserFollowMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserFollowPO;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.logging.stdout.StdOutImpl;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Testcontainers(disabledWithoutDocker = true)
class FollowRepositoryMySqlIT {

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
    void followerPageUsesRelationIdCursorAgainstRealMySql() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            FollowRepositoryImpl repository = new FollowRepositoryImpl(
                    session.getMapper(UserFollowMapper.class),
                    session.getMapper(UserCounterMapper.class),
                    new SnowflakeIdGenerator());

            insertFollow(session, 500L, 1_000L, 10L);
            insertFollow(session, 300L, 1_000L, 20L);
            insertFollow(session, 900L, 1_000L, 30L);
            insertFollow(session, 700L, 1_000L, 40L);

            List<FollowCursorDTO> firstPage = repository.followerPage(1_000L, 0, 2);
            assertEquals(List.of(700L, 900L), firstPage.stream().map(FollowCursorDTO::getUid).toList());
            assertEquals(40L, firstPage.get(0).getRelationId());
            assertEquals(30L, firstPage.get(1).getRelationId());

            List<FollowCursorDTO> secondPage = repository.followerPage(1_000L, firstPage.get(1).getRelationId(), 2);
            assertEquals(List.of(300L, 500L), secondPage.stream().map(FollowCursorDTO::getUid).toList());
            assertEquals(20L, secondPage.get(0).getRelationId());
            assertEquals(10L, secondPage.get(1).getRelationId());
            assertFalse(secondPage.stream().map(FollowCursorDTO::getUid).toList().contains(700L));
        }
    }

    private static void insertFollow(SqlSession session, Long fromUid, Long toUid, Long relationId) {
        UserFollowPO po = follow(fromUid, toUid, relationId);
        try (PreparedStatement ps = session.getConnection().prepareStatement(
                "INSERT INTO t_user_follow(id, from_uid, to_uid, is_deleted) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, po.getId());
            ps.setLong(2, po.getFromUid());
            ps.setLong(3, po.getToUid());
            ps.setInt(4, po.getIsDeleted());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static UserFollowPO follow(Long fromUid, Long toUid, Long relationId) {
        UserFollowPO po = new UserFollowPO();
        po.setId(relationId);
        po.setFromUid(fromUid);
        po.setToUid(toUid);
        po.setIsDeleted(0);
        return po;
    }

    private static HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("it", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(StdOutImpl.class);
        configuration.addMapper(UserFollowMapper.class);
        configuration.addMapper(UserCounterMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE t_user_follow (
                        id BIGINT NOT NULL PRIMARY KEY,
                        from_uid BIGINT NOT NULL,
                        to_uid BIGINT NOT NULL,
                        create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        is_deleted TINYINT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_from_to (from_uid, to_uid),
                        KEY idx_to_uid (to_uid, create_time),
                        KEY idx_from_uid (from_uid, create_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute("""
                    CREATE TABLE t_user_counter (
                        user_id BIGINT NOT NULL PRIMARY KEY,
                        follower_count BIGINT NOT NULL DEFAULT 0,
                        following_count BIGINT NOT NULL DEFAULT 0,
                        post_count BIGINT NOT NULL DEFAULT 0,
                        like_received BIGINT NOT NULL DEFAULT 0,
                        update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                        version INT NOT NULL DEFAULT 0
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }
}

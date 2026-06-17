package com.aicomic.common.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 数据源配置
 * <p>
 * 解决 SQLite 兼容性问题：
 * <ul>
 *   <li>WAL 日志模式 — 支持并发读写，消除 "database is locked" 错误</li>
 *   <li>busy_timeout — 写入冲突时等待而非立即失败</li>
 *   <li>foreign_keys=ON — 启用外键约束</li>
 *   <li>synchronous=NORMAL — 在安全前提下大幅提升写入性能</li>
 *   <li>cache_size — 增大页缓存加速查询</li>
 *   <li>temp_store=MEMORY — 临时表放内存</li>
 *   <li>mmap_size — 内存映射 I/O</li>
 * </ul>
 * <p>
 * 连接池调优：
 * <ul>
 *   <li>最大连接 5 — SQLite 只有一个写入者，过多连接无益</li>
 *   <li>最小空闲 1 — 避免冷连接开销</li>
 *   <li>连接超时 10s — SQLite 是本地文件，快速失败</li>
 * </ul>
 */
@Configuration
public class SQLiteConfig {

    private static final Logger log = LoggerFactory.getLogger(SQLiteConfig.class);

    /**
     * 自定义 DataSource，覆盖 Spring Boot 自动配置
     * 使用 HikariCP 但针对 SQLite 单文件数据库特性调优
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        HikariDataSource ds = properties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        // === SQLite 连接池最佳实践 ===
        // SQLite 是单写入者架构，WAL 模式下多读者 + 一写者
        // 过多连接反而导致锁竞争
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        ds.setIdleTimeout(300_000);      // 5 分钟空闲超时
        ds.setMaxLifetime(600_000);      // 10 分钟最大生命周期
        ds.setConnectionTimeout(10_000);  // 本地文件，快速失败
        ds.setLeakDetectionThreshold(60_000);

        // 每个连接创建时启用外键（SQLite 默认不启用）
        ds.setConnectionInitSql("PRAGMA foreign_keys=ON");

        // 连接池名称便于调试
        ds.setPoolName("SQLitePool");

        log.info("SQLite HikariCP pool configured: maxSize={}, minIdle={}, WAL mode enabled",
                ds.getMaximumPoolSize(), ds.getMinimumIdle());

        return ds;
    }

    /**
     * 启动时执行持久化 PRAGMA 设置和性能优化
     * <p>
     * 注意：journal_mode=WAL 是持久化设置（数据库级），
     * 只需执行一次，后续所有连接自动使用 WAL 模式。
     * foreign_keys=ON 通过 connectionInitSql 在每个连接上设置。
     */
    @Bean
    public CommandLineRunner sqlitePragmaInitializer(DataSource dataSource) {
        return args -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {

                log.info("Initializing SQLite performance PRAGMAs...");

                // WAL 模式 — 持久化设置，支持并发读写
                stmt.execute("PRAGMA journal_mode=WAL");
                log.info("  journal_mode=WAL (persistent)");

                // 忙等待超时 5 秒 — 写入冲突时不立即失败
                stmt.execute("PRAGMA busy_timeout=5000");
                log.info("  busy_timeout=5000ms");

                // 同步模式：NORMAL — 在安全前提下大幅提升写性能
                // FULL 是默认（最安全但最慢），NORMAL 在 WAL 模式下足够安全
                stmt.execute("PRAGMA synchronous=NORMAL");
                log.info("  synchronous=NORMAL");

                // 页缓存大小：-8000 表示 8MB（负值表示 KB）
                stmt.execute("PRAGMA cache_size=-8000");
                log.info("  cache_size=8MB");

                // 临时表存储在内存中
                stmt.execute("PRAGMA temp_store=MEMORY");
                log.info("  temp_store=MEMORY");

                // 内存映射 I/O：256MB（仅在 64 位系统上有效）
                stmt.execute("PRAGMA mmap_size=268435456");
                log.info("  mmap_size=256MB");

                // WAL 自动检查点：每 1000 页触发一次
                stmt.execute("PRAGMA wal_autocheckpoint=1000");
                log.info("  wal_autocheckpoint=1000");

                // 更新查询优化器统计信息
                stmt.execute("PRAGMA optimize");
                log.info("SQLite PRAGMAs initialized successfully");

            } catch (SQLException e) {
                log.warn("Failed to initialize some SQLite PRAGMAs: {}", e.getMessage());
                // 不阻止启动 — PRAGMAs 是优化项，不是必需项
            }
        };
    }
}

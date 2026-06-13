package com.aicomic.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * 数据库导出导入服务
 * 支持 SQL 格式导出和导入
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportImportService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 导出数据库为 SQL 文件
     *
     * @param outputFile 输出文件路径
     */
    @Transactional(readOnly = true)
    public void exportDatabase(Path outputFile) throws IOException {
        StringBuilder sql = new StringBuilder();
        sql.append("-- AI Comic Weaver Database Export\n");
        sql.append("-- Generated at: ").append(java.time.LocalDateTime.now()).append("\n\n");

        // 获取所有表名
        List<String> tables = getTableNames();

        for (String table : tables) {
            log.debug("导出表：{}", table);
            exportTable(sql, table);
        }

        Files.writeString(outputFile, sql.toString());
        log.info("数据库导出完成：{}", outputFile.getFileName());
    }

    /**
     * 从 SQL 文件导入数据库
     *
     * @param inputFile SQL 文件路径
     */
    @Transactional
    public void importDatabase(Path inputFile) throws IOException {
        String sql = Files.readString(inputFile);

        // 分割 SQL 语句（按分号分隔）
        String[] statements = sql.split(";");

        int executed = 0;
        for (String statement : statements) {
            String trimmed = statement.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }

            try {
                jdbcTemplate.execute(trimmed);
                executed++;
            } catch (Exception e) {
                log.warn("执行 SQL 失败：{}, 错误：{}", trimmed.substring(0, Math.min(100, trimmed.length())), e.getMessage());
            }
        }

        log.info("数据库导入完成：执行 {} 条 SQL 语句", executed);
    }

    private List<String> getTableNames() {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    private void exportTable(StringBuilder sql, String tableName) {
        try {
            // 获取表结构
            sql.append("-- Table: ").append(tableName).append("\n");

            // 使用 MySQL 风格的 INSERT 语句
            String selectSql = "SELECT * FROM " + tableName;
            List<ExportRow> rows = jdbcTemplate.query(selectSql, this::mapRow);

            if (rows.isEmpty()) {
                sql.append("-- No data\n\n");
                return;
            }

            // 生成 INSERT 语句
            for (ExportRow row : rows) {
                sql.append("INSERT INTO ").append(tableName).append(" (");
                sql.append(String.join(", ", row.columns));
                sql.append(") VALUES (");

                List<String> values = row.values.stream()
                        .map(v -> v == null ? "NULL" : "'" + v.toString().replace("'", "''") + "'")
                        .toList();

                sql.append(String.join(", ", values));
                sql.append(");\n");
            }

            sql.append("\n");
        } catch (Exception e) {
            log.error("导出表失败：{}", tableName, e);
        }
    }

    private ExportRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        List<String> columns = new java.util.ArrayList<>();
        List<Object> values = new java.util.ArrayList<>();

        for (int i = 1; i <= columnCount; i++) {
            columns.add(metaData.getColumnName(i));
            values.add(rs.getObject(i));
        }

        return new ExportRow(columns, values);
    }

    private record ExportRow(List<String> columns, List<Object> values) {
    }
}

package com.aicomic.common.config;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupportImpl;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.dialect.pagination.LimitOffsetLimitHandler;
import org.hibernate.dialect.unique.UniqueDelegate;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.JdbcType;

import java.sql.Types;

/**
 * SQLite Hibernate 6.x 方言
 * <p>
 * 针对 SQLite 3.x 的核心限制：
 * <ul>
 *   <li>不支持 ALTER TABLE DROP COLUMN (SQLite 3.35+ 部分支持)</li>
 *   <li>不支持外键约束 DDL（但可通过 PRAGMA 运行时强制）</li>
 *   <li>自增列必须是 INTEGER PRIMARY KEY</li>
 *   <li>类型系统宽松（存储类而非严格类型）</li>
 * </ul>
 * <p>
 * 兼容性增强：
 * <ul>
 *   <li>正确的 JDBC 类型映射</li>
 *   <li>datetime 函数适配</li>
 *   <li>Boolean 类型映射为 INTEGER（0/1）</li>
 * </ul>
 */
public class SQLiteDialect extends Dialect {

    public SQLiteDialect(DialectResolutionInfo info) {
        super(info);
    }

    public SQLiteDialect() {
        // Hibernate 6.x 可能通过反射调用无参构造
        super();
    }

    // ============================================================
    // 类型系统
    // ============================================================

    @Override
    protected String columnType(int sqlTypeCode) {
        return switch (sqlTypeCode) {
            case Types.BOOLEAN, Types.BIT -> "integer";        // SQLite 无布尔类型
            case Types.TINYINT, Types.SMALLINT -> "integer";
            case Types.BIGINT -> "integer";                     // SQLite INTEGER 支持 64 位
            case Types.FLOAT, Types.REAL, Types.DOUBLE -> "real";
            case Types.NUMERIC, Types.DECIMAL -> "real";
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR,
                 Types.NVARCHAR, Types.NCHAR, Types.LONGNVARCHAR,
                 Types.CLOB -> "text";
            case Types.BLOB, Types.VARBINARY, Types.LONGVARBINARY,
                 Types.BINARY -> "blob";
            case Types.DATE -> "text";                         // 存为 ISO-8601 文本
            case Types.TIME -> "text";
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "text";
            default -> super.columnType(sqlTypeCode);
        };
    }

    @Override
    public int getDefaultTimestampPrecision() {
        return 0;  // SQLite TEXT 时间戳无精度概念
    }

    // ============================================================
    // 身份列（自增主键）
    // ============================================================

    @Override
    public IdentityColumnSupport getIdentityColumnSupport() {
        return new SQLiteIdentityColumnSupport();
    }

    // ============================================================
    // 分页
    // ============================================================

    @Override
    public LimitHandler getLimitHandler() {
        return LimitOffsetLimitHandler.INSTANCE;
    }

    // ============================================================
    // DDL 限制
    // ============================================================

    @Override
    public boolean hasAlterTable() {
        // SQLite 3.35+ 支持 ALTER TABLE DROP COLUMN，但保守处理
        return false;
    }

    @Override
    public boolean dropConstraints() {
        return false;
    }

    @Override
    public boolean qualifyIndexName() {
        return false;
    }

    @Override
    public String getAddColumnString() {
        return "add column";
    }

    @Override
    public String getDropForeignKeyString() {
        return "";
    }

    @Override
    public String getAddForeignKeyConstraintString(
            String constraintName, String[] foreignKey,
            String referencedTable, String[] primaryKey,
            boolean referencesPrimaryKey) {
        return "";
    }

    @Override
    public String getAddPrimaryKeyConstraintString(String constraintName) {
        return "";
    }

    // ============================================================
    // 函数支持
    // ============================================================

    @Override
    public String currentTime() {
        return "time('now','localtime')";
    }

    @Override
    public String currentTimestamp() {
        return "datetime('now','localtime')";
    }

    @Override
    public String currentDate() {
        return "date('now','localtime')";
    }

    // ============================================================
    // 其他
    // ============================================================

    @Override
    public boolean supportsTupleCounts() {
        return true;
    }

    @Override
    public boolean supportsUnionAll() {
        return true;
    }

    @Override
    public boolean supportsIfExistsAfterTableName() {
        return true;
    }

    /**
     * SQLite 的 CREATE TABLE ... IF NOT EXISTS 语法
     */
    @Override
    public String getTableTypeString() {
        return "";
    }

    @Override
    public boolean supportsIfExistsBeforeTableName() {
        return true;
    }

    // ============================================================
    // 自增主键支持
    // ============================================================

    private static class SQLiteIdentityColumnSupport extends IdentityColumnSupportImpl {

        @Override
        public boolean supportsIdentityColumns() {
            return true;
        }

        @Override
        public boolean hasDataTypeInIdentityColumn() {
            return false;  // SQLite 自增列只能是 INTEGER
        }

        @Override
        public String getIdentitySelectString(String table, String column, int type) {
            return "select last_insert_rowid()";
        }

        @Override
        public String getIdentityColumnString(int type) {
            // SQLite: INTEGER PRIMARY KEY 自动成为 ROWID 别名并自增
            return "integer primary key autoincrement";
        }

        @Override
        public String getIdentityInsertString() {
            return "null";
        }
    }
}

---
description: "执行达梦(DM)数据库查询——通过 sqlline 连接指定 schema，自动处理类路径、驱动和输出格式。用法: /dm-query <schema> <SQL语句>"
---

# 达梦数据库查询

通过 sqlline 命令行工具连接达梦数据库执行 SQL 查询。

## 参数

- `$1` — schema 名称（HSZYW 或 MLXT）
- `$2` — SQL 查询语句

## 连接信息

| Schema | JDBC URL | 用户名 | 密码 |
|--------|----------|--------|------|
| HSZYW | `jdbc:dm://192.168.100.53:5236?schema=HSZYW` | HSZYW | `HS%sirc80` |
| MLXT | `jdbc:dm://192.168.100.53:5236?schema=MLXT` | mlxt | `mlxt.fgi.sirc` |

## 执行

根据用户提供的 schema 和 SQL，构造并执行以下命令：

```bash
TOOL_DIR="D:/IDEA_WORKSPACE/microservice/.claude/tools/db-query"
java -cp "$TOOL_DIR/sqlline.jar;$TOOL_DIR/DmJdbcDriver18.jar" sqlline.SqlLine \
  -u "jdbc:dm://192.168.100.53:5236?schema=<SCHEMA>" \
  -n <USER> -p '<PASSWORD>' \
  --showNestedErrs=false --showWarnings=false --verbose=false \
  --outputformat=tsv --showHeader=true \
  -e "<SQL>" 2>/dev/null
```

## 注意事项

- 工具目录位于 `D:/IDEA_WORKSPACE/microservice/.claude/tools/db-query/`
- 如果用户未指定 schema，默认使用 HSZYW
- 输出格式为 TSV（制表符分隔），便于后续处理
- 对于大结果集，建议在 SQL 中添加 `LIMIT` 子句
- 如果需要 integratedpd 项目的 MLXT schema，工作目录切换到 `D:/IDEA_WORKSPACE/integratedpd`

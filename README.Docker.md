# AI Comic Weaver Docker 部署指南

## 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- 至少 4GB 可用内存
- 至少 10GB 可用磁盘空间

## 快速启动

### 1. 构建并启动

```bash
docker-compose up -d --build
```

### 2. 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看后端日志
docker-compose logs -f backend

# 查看前端日志
docker-compose logs -f frontend
```

### 3. 访问应用

- 前端：http://localhost
- 后端 API：http://localhost:8080
- API 文档：http://localhost:8080/swagger-ui.html

## 数据持久化

数据存储在 Docker 卷中：

- `aicomic-data` - 数据库和上传文件
- `aicomic-logs` - 日志文件

查看卷位置：

```bash
docker volume inspect aicomic-data
docker volume inspect aicomic-logs
```

## 停止服务

```bash
docker-compose down
```

## 重置数据

```bash
docker-compose down -v
```

## 生产环境部署

### 修改配置

编辑 `docker-compose.yml`：

```yaml
services:
  backend:
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SERVER_PORT=8080
      # 添加您的生产环境配置
      - APP_STORAGE_PATH=/data
```

### 使用外部存储

```yaml
volumes:
  backend:
    image: aicomic-backend
    volumes:
      - /path/to/your/data:/app/data
      - /path/to/your/logs:/app/logs
```

## 健康检查

```bash
# 检查容器状态
docker-compose ps

# 检查后端健康状态
curl http://localhost:8080/actuator/health
```

## 常见问题

### 端口冲突

如果 80 或 8080 端口被占用，修改 `docker-compose.yml`：

```yaml
ports:
  - "8081:8080"  # 后端使用 8081
  - "8082:80"    # 前端使用 8082
```

### 内存不足

为 JVM 设置最大堆大小：

```yaml
environment:
  - JAVA_OPTS=-Xmx2g
```

## 备份数据

```bash
docker run --rm \
  -v aicomic-data:/source \
  -v $(pwd):/backup \
  alpine tar czf /backup/aicomic-data-backup.tar.gz -C /source .
```

## 恢复数据

```bash
docker run --rm \
  -v aicomic-data:/target \
  -v $(pwd):/backup \
  alpine tar xzf /backup/aicomic-data-backup.tar.gz -C /target
```

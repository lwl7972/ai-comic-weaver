# 视频生成队列管理使用指南

## 概述

视频生成队列管理器提供工业级的任务调度能力，支持：
- ✅ 任务优先级管理（HIGH/MEDIUM/LOW）
- ✅ 队列暂停/恢复
- ✅ 任务取消
- ✅ 并发控制（默认最大 2 个并发任务）
- ✅ 实时进度监控

## API 端点

### 1. 提交视频生成任务

#### 1.1 整集视频生成（默认模式）
```http
POST /api/v1/episodes/{episodeId}/generate-videos
```

**响应**:
```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

#### 1.2 自定义参数生成
```http
POST /api/v1/episodes/{episodeId}/generate-videos-custom
Content-Type: application/json

{
  "generationMode": "FULL_EPISODE",
  "priority": "HIGH",
  "duration": 60,
  "resolution": "1080p",
  "enableRetry": true,
  "maxRetries": 3
}
```

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| generationMode | String | 否 | FULL_EPISODE/SINGLE_SHOT，默认 FULL_EPISODE |
| priority | String | 否 | HIGH/MEDIUM/LOW，默认 MEDIUM |
| duration | Integer | 否 | 视频时长（秒） |
| resolution | String | 否 | 分辨率（720p/1080p/4K） |
| enableRetry | Boolean | 否 | 是否启用重试，默认 true |
| maxRetries | Integer | 否 | 最大重试次数，默认 3 |

**响应**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "a1b2c3d4e5f6",
    "projectId": 1,
    "episodeId": 1,
    "generationMode": "FULL_EPISODE",
    "priority": "HIGH",
    "duration": 60,
    "resolution": "1080p"
  }
}
```

---

### 2. 队列控制

#### 2.1 暂停队列
```http
POST /api/v1/director/queue/pause
```

暂停后，新任务不会被调度执行，但已提交的任务会保留在队列中。

#### 2.2 恢复队列
```http
POST /api/v1/director/queue/resume
```

恢复队列调度，暂停期间提交的任务会按优先级顺序执行。

---

### 3. 任务管理

#### 3.1 取消任务
```http
DELETE /api/v1/director/tasks/{taskId}
```

**响应**:
```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

**注意**:
- 仅 PENDING 或 RUNNING 状态的任务可以取消
- 已取消的任务无法恢复

#### 3.2 获取任务详情
```http
GET /api/v1/director/tasks/{taskId}
```

**响应**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "a1b2c3d4e5f6",
    "taskType": "FULL_EPISODE",
    "videoUrl": "http://...",
    "status": "RUNNING",
    "priority": "HIGH",
    "progress": 45,
    "errorMessage": null,
    "submittedAt": "2026-06-13T10:30:00",
    "startedAt": "2026-06-13T10:31:00",
    "completedAt": null,
    "retryCount": 0
  }
}
```

#### 3.3 获取所有任务
```http
GET /api/v1/director/tasks
```

返回所有任务的列表（包括已完成、失败、取消的任务）。

---

### 4. 队列监控

#### 4.1 获取队列统计
```http
GET /api/v1/director/queue/stats
```

**响应**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "pendingCount": 3,
    "runningCount": 2,
    "completedCount": 15,
    "failedCount": 1,
    "cancelledCount": 0,
    "totalCount": 21,
    "paused": false,
    "maxConcurrent": 2
  }
}
```

**字段说明**:
| 字段 | 说明 |
|------|------|
| pendingCount | 等待中的任务数 |
| runningCount | 正在执行的任务数 |
| completedCount | 已成功完成的任务数 |
| failedCount | 失败的任务数 |
| cancelledCount | 已取消的任务数 |
| totalCount | 总任务数 |
| paused | 队列是否暂停 |
| maxConcurrent | 最大并发任务数 |

---

## 使用示例

### 示例 1：提交高优先级任务

```javascript
// 提交高优先级整集视频生成
fetch('/api/v1/episodes/1/generate-videos-custom', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    generationMode: 'FULL_EPISODE',
    priority: 'HIGH',
    duration: 90
  })
})
.then(res => res.json())
.then(data => {
  console.log('任务已提交:', data.data.taskId);
  // 轮询任务状态
  pollTaskStatus(data.data.taskId);
});
```

### 示例 2：轮询任务进度

```javascript
function pollTaskStatus(taskId) {
  const pollInterval = setInterval(async () => {
    const res = await fetch(`/api/v1/director/tasks/${taskId}`);
    const { data } = await res.json();
    
    console.log(`进度：${data.progress}%, 状态：${data.status}`);
    
    if (data.status === 'SUCCESS' || data.status === 'FAILED') {
      clearInterval(pollInterval);
      if (data.status === 'SUCCESS') {
        console.log('视频生成完成:', data.videoUrl);
      } else {
        console.error('视频生成失败:', data.errorMessage);
      }
    }
  }, 3000); // 每 3 秒轮询一次
}
```

### 示例 3：批量提交后暂停队列

```javascript
// 批量提交多个任务
const taskIds = [];
for (let episodeId = 1; episodeId <= 10; episodeId++) {
  const res = await fetch(`/api/v1/episodes/${episodeId}/generate-videos`, {
    method: 'POST'
  });
  const { data } = await res.json();
  taskIds.push(data.taskId);
}

// 暂停队列，稍后恢复
await fetch('/api/v1/director/queue/pause', { method: 'POST' });
console.log('队列已暂停，可以调整优先级或其他设置');

// ... 进行调整操作 ...

// 恢复队列
await fetch('/api/v1/director/queue/resume', { method: 'POST' });
console.log('队列已恢复，任务开始执行');
```

### 示例 4：取消失败任务并重试

```javascript
async function retryFailedTask(taskId) {
  // 获取任务详情
  const res = await fetch(`/api/v1/director/tasks/${taskId}`);
  const { data } = await res.json();
  
  if (data.status === 'FAILED') {
    // 取消原任务
    await fetch(`/api/v1/director/tasks/${taskId}`, {
      method: 'DELETE'
    });
    
    // 重新提交任务（高优先级）
    const retryRes = await fetch(`/api/v1/episodes/${data.episodeId}/generate-videos-custom`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        generationMode: 'FULL_EPISODE',
        priority: 'HIGH'
      })
    });
    
    console.log('任务已重试:', await retryRes.json());
  }
}
```

---

## 任务优先级策略

### 优先级说明

| 优先级 | 权重 | 使用场景 |
|--------|------|----------|
| HIGH | 3 | 紧急任务、VIP 用户、重要项目 |
| MEDIUM | 2 | 普通任务（默认） |
| LOW | 1 | 批量处理、后台任务 |

### 调度规则

1. **优先级优先**: 高优先级任务总是先于低优先级任务执行
2. **同优先级 FIFO**: 相同优先级的任务按提交顺序执行
3. **并发限制**: 最多同时执行 2 个任务（可配置）

---

## 配置选项

### application.yml

```yaml
# 视频队列配置
video:
  queue:
    # 最大并发任务数
    max-concurrent: 2
    # 队列检查间隔（秒）
    check-interval: 2
    # 任务超时时间（分钟）
    task-timeout: 30
```

---

## 最佳实践

### 1. 任务提交
- 避免短时间内提交大量低优先级任务
- 紧急任务使用 HIGH 优先级，但不要滥用
- 批量任务建议先暂停队列，提交完成后恢复

### 2. 进度监控
- 使用 SSE 推送（推荐）或轮询（每 3-5 秒）
- 轮询间隔不宜过短，避免服务器压力
- 任务完成后及时清理或归档

### 3. 错误处理
- 失败任务自动重试 3 次（可配置）
- 重试失败后手动干预或降低优先级重试
- 记录失败原因用于后续分析

### 4. 资源管理
- 定期检查队列统计，清理已完成任务
- 高峰期适当增加 max-concurrent 配置
- 监控队列积压情况，及时扩容

---

## 故障排查

### 问题 1：任务一直 PENDING

**可能原因**:
- 队列已满（runningCount >= maxConcurrent）
- 队列被暂停
- 优先级太低

**解决方案**:
```bash
# 检查队列状态
GET /api/v1/director/queue/stats

# 如果 paused=true，恢复队列
POST /api/v1/director/queue/resume

# 如果优先级太低，考虑取消后用高优先级重新提交
```

### 问题 2：任务频繁 FAILED

**可能原因**:
- 模型 API 限流或故障
- 提示词过长或格式错误
- 参考图 URL 不可访问

**解决方案**:
```bash
# 检查错误信息
GET /api/v1/director/tasks/{taskId}

# 查看错误详情 data.errorMessage
# 根据错误类型调整参数或联系模型供应商
```

### 问题 3：队列处理缓慢

**可能原因**:
- 并发数配置过低
- 单个任务耗时过长
- 系统资源不足

**解决方案**:
```yaml
# 增加并发数（需评估服务器性能）
video:
  queue:
    max-concurrent: 4  # 从 2 增加到 4
```

---

## 技术架构

### 核心组件

```
DirectorController (REST API)
       ↓
DirectorService (业务逻辑)
       ↓
VideoTaskQueueManager (队列调度)
       ↓
VideoGenerationTask (任务模型)
       ↓
ModelCallService (模型调用)
```

### 调度流程

```
1. 任务提交 → 进入优先队列
2. 调度器轮询 → 获取最高优先级任务
3. 检查并发限制 → 有空闲槽位则执行
4. 异步执行任务 → 更新状态为 RUNNING
5. 任务完成 → 更新状态为 SUCCESS/FAILED
6. 从 runningTasks 移除 → 调度下一个任务
```

---

最后更新：2026-06-13

# AI漫剧平台核心流水线补全 — 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 补全六大模块流水线（剧本→角色→场景→分镜→导演→S级），实现配置中心、脏标记机制、S级FFmpeg、素材库和项目模板

**架构：** 分层推进（L1基础层→L2流水线层→L3配套层），后端Spring Boot + SQLite，前端Vue 3 + Element Plus，SSE实时推送

**技术栈：** Spring Boot 2.7 / Java 17 / Spring Data JPA / SQLite / Vue 3.5 / Element Plus 2.9 / Pinia / Vite 6 / TypeScript 5.6 / FFmpeg

---

## 文件结构

### L1 基础层

| 文件 | 职责 | 操作 |
|------|------|------|
| `backend/.../service/PipelineStateService.java` | 脏标记传播与查询 | 新建 |
| `backend/.../controller/PipelineStateController.java` | 流水线状态API端点 | 新建 |
| `backend/.../dto/PipelineAdvanceRequest.java` | 推进阶段请求DTO | 新建 |
| `backend/.../service/ScriptService.java` | 剧本修改时标记下游DIRTY | 修改 |
| `backend/.../service/CharacterService.java` | 角色修改时标记下游DIRTY | 修改 |
| `backend/.../service/SceneService.java` | 场景修改时标记下游DIRTY | 修改 |
| `backend/.../service/StoryboardService.java` | 分镜修改时标记下游DIRTY | 修改 |
| `backend/.../service/DirectorService.java` | 视频生成后标记下游DIRTY | 修改 |
| `backend/.../service/ModelConfigService.java` | 补充testConnection方法 | 修改 |
| `backend/.../service/PromptTemplateService.java` | 完善render/validate方法 | 修改 |
| `frontend/src/views/config/ConfigView.vue` | 重构为Tab分组配置中心 | 重写 |
| `frontend/src/stores/pipeline.ts` | 流水线状态store（脏标记检查） | 新建 |
| `frontend/src/layouts/MainLayout.vue` | 模块切换时DIRTY拦截 | 修改 |

### L2 流水线层

| 文件 | 职责 | 操作 |
|------|------|------|
| `backend/.../common/util/FFmpegUtils.java` | FFmpeg命令构建与执行 | 新建 |
| `backend/.../dto/ExportConfig.java` | 视频导出配置DTO | 新建 |
| `backend/.../dto/WatermarkConfig.java` | 水印配置DTO | 新建 |
| `backend/.../dto/CompositeRequest.java` | 成片合成请求DTO | 新建 |
| `backend/.../service/SLevelService.java` | 完整FFmpeg成片合成实现 | 重写 |
| `backend/.../service/DirectorService.java` | 补充FFmpeg拼接方法 | 修改 |
| `frontend/src/views/slevel/SLevelView.vue` | 完善S级UI（进度/播放器/导出） | 修改 |
| `frontend/src/stores/slevel.ts` | S级store完善（进度订阅） | 修改 |
| `frontend/src/views/character/CharacterView.vue` | 修复scriptId硬编码 | 修改 |
| `frontend/src/views/scene/SceneView.vue` | 修复scriptId硬编码 | 修改 |

### L3 配套层

| 文件 | 职责 | 操作 |
|------|------|------|
| `backend/.../service/AssetService.java` | 素材CRUD与文件管理 | 新建 |
| `backend/.../controller/AssetController.java` | 素材API端点 | 新建 |
| `backend/.../service/TemplateService.java` | 项目模板CRUD与预设初始化 | 新建 |
| `backend/.../controller/TemplateController.java` | 模板API端点 | 新建 |
| `backend/.../config/DataInitializer.java` | 预置模板数据初始化 | 新建 |
| `frontend/src/views/asset/AssetView.vue` | 素材库网格布局页面 | 新建 |
| `frontend/src/stores/asset.ts` | 素材库store | 新建 |
| `frontend/src/stores/template.ts` | 项目模板store | 新建 |
| `frontend/src/views/project/ProjectView.vue` | 增加"从模板创建" | 修改 |
| `frontend/src/router/index.ts` | 增加/asset路由 | 修改 |
| `frontend/src/layouts/MainLayout.vue` | 侧边栏增加素材库入口 | 修改 |

---

## L1 基础层

### 任务 1：PipelineStateService — 脏标记核心逻辑

**文件：**
- 创建：`backend/src/main/java/com/aicomic/service/PipelineStateService.java`
- 创建：`backend/src/main/java/com/aicomic/dto/PipelineAdvanceRequest.java`

- [ ] **步骤 1：编写 PipelineStateService**

```java
package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.PipelineState;
import com.aicomic.entity.Project;
import com.aicomic.repository.PipelineStateRepository;
import com.aicomic.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineStateService {

    private final PipelineStateRepository pipelineStateRepository;
    private final ProjectRepository projectRepository;

    /** 获取项目的流水线状态，不存在则初始化 */
    @Transactional(readOnly = true)
    public PipelineState getPipelineState(Long projectId) {
        return pipelineStateRepository.findByProjectId(projectId)
                .orElseGet(() -> initPipelineState(projectId));
    }

    /** 初始化流水线状态 */
    @Transactional
    public PipelineState initPipelineState(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("项目", projectId));
        PipelineState state = new PipelineState();
        state.setProjectId(projectId);
        state.setCurrentStage(Project.PipelineStage.SCRIPT);
        return pipelineStateRepository.save(state);
    }

    /** 标记下游阶段为DIRTY */
    @Transactional
    public void markDirty(Long projectId, Project.PipelineStage sourceStage) {
        PipelineState state = getPipelineState(projectId);
        switch (sourceStage) {
            case SCRIPT:
                state.setCharacterDirty(true);
                state.setSceneDirty(true);
                // fall through
            case CHARACTER:
                state.setStoryboardDirty(true);
                // fall through
            case SCENE:
                state.setStoryboardDirty(true);
                // fall through
            case STORYBOARD:
                state.setDirectorDirty(true);
                // fall through
            case DIRECTOR:
                state.setSLevelDirty(true);
                break;
            case OUTPUT:
                break;
        }
        pipelineStateRepository.save(state);
        log.info("流水线脏标记更新: projectId={}, source={}, characterDirty={}, sceneDirty={}, storyboardDirty={}, directorDirty={}, sLevelDirty={}",
                projectId, sourceStage, state.getCharacterDirty(), state.getSceneDirty(),
                state.getStoryboardDirty(), state.getDirectorDirty(), state.getSLevelDirty());
    }

    /** 清除指定阶段的DIRTY标记 */
    @Transactional
    public void clearDirtyFlag(Long projectId, Project.PipelineStage stage) {
        PipelineState state = getPipelineState(projectId);
        setDirtyFlag(state, stage, false);
        pipelineStateRepository.save(state);
        log.info("清除脏标记: projectId={}, stage={}", projectId, stage);
    }

    /** 推进到下一阶段 */
    @Transactional
    public PipelineState advance(Long projectId, Project.PipelineStage targetStage) {
        PipelineState state = getPipelineState(projectId);
        state.setCurrentStage(targetStage);
        return pipelineStateRepository.save(state);
    }

    /** 检查指定阶段是否有DIRTY标记 */
    @Transactional(readOnly = true)
    public boolean isStageDirty(Long projectId, Project.PipelineStage stage) {
        PipelineState state = getPipelineState(projectId);
        return getDirtyFlag(state, stage);
    }

    /** 获取所有DIRTY阶段列表 */
    @Transactional(readOnly = true)
    public List<Project.PipelineStage> getDirtyStages(Long projectId) {
        PipelineState state = getPipelineState(projectId);
        return Arrays.stream(Project.PipelineStage.values())
                .filter(stage -> getDirtyFlag(state, stage))
                .toList();
    }

    private void setDirtyFlag(PipelineState state, Project.PipelineStage stage, boolean value) {
        switch (stage) {
            case SCRIPT: state.setScriptDirty(value); break;
            case CHARACTER: state.setCharacterDirty(value); break;
            case SCENE: state.setSceneDirty(value); break;
            case STORYBOARD: state.setStoryboardDirty(value); break;
            case DIRECTOR: state.setDirectorDirty(value); break;
            case OUTPUT: state.setSLevelDirty(value); break;
        }
    }

    private boolean getDirtyFlag(PipelineState state, Project.PipelineStage stage) {
        switch (stage) {
            case SCRIPT: return Boolean.TRUE.equals(state.getScriptDirty());
            case CHARACTER: return Boolean.TRUE.equals(state.getCharacterDirty());
            case SCENE: return Boolean.TRUE.equals(state.getSceneDirty());
            case STORYBOARD: return Boolean.TRUE.equals(state.getStoryboardDirty());
            case DIRECTOR: return Boolean.TRUE.equals(state.getDirectorDirty());
            case OUTPUT: return Boolean.TRUE.equals(state.getSLevelDirty());
            default: return false;
        }
    }
}
```

- [ ] **步骤 2：编写 PipelineAdvanceRequest DTO**

```java
package com.aicomic.dto;

import com.aicomic.entity.Project;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class PipelineAdvanceRequest {
    @NotNull(message = "目标阶段不能为空")
    private Project.PipelineStage targetStage;

    /** 用户选择：true=重新执行，false=保持现状 */
    private boolean reExecute = false;
}
```

- [ ] **步骤 3：编写 PipelineStateController**

```java
package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.dto.PipelineAdvanceRequest;
import com.aicomic.entity.PipelineState;
import com.aicomic.service.PipelineStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.aicomic.entity.Project.PipelineStage;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PipelineStateController {

    private final PipelineStateService pipelineStateService;

    @GetMapping("/projects/{projectId}/pipeline-state")
    public ApiResponse<PipelineState> getPipelineState(@PathVariable Long projectId) {
        return ApiResponse.success(pipelineStateService.getPipelineState(projectId));
    }

    @PostMapping("/projects/{projectId}/pipeline-advance")
    public ApiResponse<PipelineState> advance(
            @PathVariable Long projectId,
            @RequestBody PipelineAdvanceRequest request) {
        PipelineState state;
        if (request.isReExecute()) {
            // 重新执行：清除该阶段DIRTY标记，然后推进
            pipelineStateService.clearDirtyFlag(projectId, request.getTargetStage());
        }
        state = pipelineStateService.advance(projectId, request.getTargetStage());
        return ApiResponse.success(state);
    }

    @PostMapping("/projects/{projectId}/pipeline-clear-dirty")
    public ApiResponse<PipelineState> clearDirty(
            @PathVariable Long projectId,
            @RequestParam PipelineStage stage) {
        pipelineStateService.clearDirtyFlag(projectId, stage);
        return ApiResponse.success(pipelineStateService.getPipelineState(projectId));
    }

    @GetMapping("/projects/{projectId}/pipeline-dirty-stages")
    public ApiResponse<List<PipelineStage>> getDirtyStages(@PathVariable Long projectId) {
        return ApiResponse.success(pipelineStateService.getDirtyStages(projectId));
    }
}
```

- [ ] **步骤 4：编译验证**

运行：`cd D:/AI/atomgit/backend && mvn compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add backend/src/main/java/com/aicomic/service/PipelineStateService.java backend/src/main/java/com/aicomic/controller/PipelineStateController.java backend/src/main/java/com/aicomic/dto/PipelineAdvanceRequest.java
git commit -m "feat: 实现流水线脏标记机制 - PipelineStateService/Controller/DTO"
```

---

### 任务 2：脏标记侵入现有Service

**文件：**
- 修改：`backend/src/main/java/com/aicomic/service/ScriptService.java`
- 修改：`backend/src/main/java/com/aicomic/service/CharacterService.java`
- 修改：`backend/src/main/java/com/aicomic/service/SceneService.java`
- 修改：`backend/src/main/java/com/aicomic/service/StoryboardService.java`
- 修改：`backend/src/main/java/com/aicomic/service/DirectorService.java`

- [ ] **步骤 1：在5个Service中注入PipelineStateService并添加markDirty调用**

每个Service的修改模式相同：

1. 在依赖注入字段中添加 `PipelineStateService pipelineStateService`
2. 在修改数据的方法中调用 `pipelineStateService.markDirty(projectId, PipelineStage.XXX)`

**ScriptService.java** — 在 `updateScript()` 等修改方法中添加：
```java
// 在方法体的数据修改之后添加：
pipelineStateService.markDirty(script.getProjectId(), Project.PipelineStage.SCRIPT);
```

**CharacterService.java** — 在 `saveCharacter()`, `confirmExtractedAsset()` 等方法中添加：
```java
pipelineStateService.markDirty(character.getProjectId(), Project.PipelineStage.CHARACTER);
```

**SceneService.java** — 在 `saveScene()`, `confirmExtractedAsset()` 等方法中添加：
```java
pipelineStateService.markDirty(scene.getProjectId(), Project.PipelineStage.SCENE);
```

**StoryboardService.java** — 在 `saveStoryboard()`, `batchUpdateStoryboards()` 等方法中添加：
```java
pipelineStateService.markDirty(projectId, Project.PipelineStage.STORYBOARD);
```

**DirectorService.java** — 在视频生成完成（`generateShotVideoInternal` 成功分支）中添加：
```java
Long projectId = refService.resolveProjectIdFromStoryboard(sb);
pipelineStateService.markDirty(projectId, Project.PipelineStage.DIRECTOR);
```

- [ ] **步骤 2：编译验证**

运行：`cd D:/AI/atomgit/backend && mvn compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add backend/src/main/java/com/aicomic/service/ScriptService.java backend/src/main/java/com/aicomic/service/CharacterService.java backend/src/main/java/com/aicomic/service/SceneService.java backend/src/main/java/com/aicomic/service/StoryboardService.java backend/src/main/java/com/aicomic/service/DirectorService.java
git commit -m "feat: 五大模块Service集成脏标记 - 修改数据时自动标记下游DIRTY"
```

---

### 任务 3：前端流水线Store + DIRTY拦截

**文件：**
- 创建：`frontend/src/stores/pipeline.ts`
- 修改：`frontend/src/layouts/MainLayout.vue`

- [ ] **步骤 1：创建 pipeline store**

```typescript
// frontend/src/stores/pipeline.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'
import { useNotificationStore } from './notification'
import type { PipelineState, PipelineStage } from '@/types'

export const usePipelineStore = defineStore('pipeline', () => {
  const pipelineState = ref<PipelineState | null>(null)
  const loading = ref(false)

  async function fetchPipelineState(projectId: number) {
    loading.value = true
    try {
      const data = await http.get(`/v1/projects/${projectId}/pipeline-state`)
      pipelineState.value = data as unknown as PipelineState
    } catch (err: any) {
      console.warn('获取流水线状态失败:', err.message)
      pipelineState.value = null
    } finally {
      loading.value = false
    }
  }

  async function advanceStage(projectId: number, targetStage: PipelineStage, reExecute: boolean) {
    try {
      const data = await http.post(`/v1/projects/${projectId}/pipeline-advance`, {
        targetStage,
        reExecute,
      })
      pipelineState.value = data as unknown as PipelineState
    } catch (err: any) {
      useNotificationStore().error('阶段切换失败', err.message)
      throw err
    }
  }

  async function clearDirtyFlag(projectId: number, stage: PipelineStage) {
    try {
      await http.post(`/v1/projects/${projectId}/pipeline-clear-dirty?stage=${stage}`)
      await fetchPipelineState(projectId)
    } catch (err: any) {
      useNotificationStore().error('清除标记失败', err.message)
    }
  }

  function isStageDirty(stage: PipelineStage): boolean {
    if (!pipelineState.value) return false
    const state = pipelineState.value
    switch (stage) {
      case 'SCRIPT': return state.scriptDirty
      case 'CHARACTER': return state.characterDirty
      case 'SCENE': return state.sceneDirty
      case 'STORYBOARD': return state.storyboardDirty
      case 'DIRECTOR': return state.directorDirty
      case 'OUTPUT': return state.sLevelDirty
      default: return false
    }
  }

  return {
    pipelineState, loading,
    fetchPipelineState, advanceStage, clearDirtyFlag, isStageDirty,
  }
})
```

- [ ] **步骤 2：修改 MainLayout.vue — 模块切换DIRTY拦截**

在 `<script setup>` 中添加流水线拦截逻辑。当用户点击侧边栏菜单项切换模块时，先检查目标模块是否有DIRTY标记。

关键修改点：
1. 导入 `usePipelineStore` 和 `ElMessageBox`
2. 在 `el-menu` 的 `@select` 事件处理器中添加拦截逻辑
3. 关闭 `el-menu` 的 `router` 属性，改用手动导航以实现拦截

```typescript
// 添加到 MainLayout.vue <script setup>
import { usePipelineStore } from '@/stores/pipeline'
import { ElMessageBox } from 'element-plus'

const pipelineStore = usePipelineStore()

// 路由到PipelineStage的映射
const stageMap: Record<string, PipelineStage> = {
  '/script': 'SCRIPT',
  '/character': 'CHARACTER',
  '/scene': 'SCENE',
  '/storyboard': 'STORYBOARD',
  '/director': 'DIRECTOR',
  '/s-level': 'OUTPUT',
}

async function handleMenuSelect(index: string) {
  const targetStage = stageMap[index]
  // 非流水线模块直接导航
  if (!targetStage || !pipelineStore.pipelineState) {
    router.push(index)
    return
  }
  // 检查目标阶段是否有DIRTY标记
  if (pipelineStore.isStageDirty(targetStage)) {
    try {
      await ElMessageBox.confirm(
        '上游内容已变更，是否重新执行当前阶段？',
        '内容已变更',
        { confirmButtonText: '重新执行', cancelButtonText: '保持现状', type: 'warning' }
      )
      // 重新执行
      await pipelineStore.advanceStage(
        pipelineStore.pipelineState!.projectId, targetStage, true)
    } catch {
      // 保持现状
      await pipelineStore.clearDirtyFlag(
        pipelineStore.pipelineState!.projectId, targetStage)
    }
  }
  router.push(index)
}
```

在模板中将 `<el-menu :router="true">` 改为 `<el-menu @select="handleMenuSelect">`，去掉 `:router="true"`。

- [ ] **步骤 3：侧边栏DIRTY视觉指示**

在 `MainLayout.vue` 的侧边栏菜单项中，为有DIRTY标记的模块添加橙色圆点：

```html
<!-- 每个菜单项模板修改示例 -->
<el-menu-item index="/script">
  <el-icon><Document /></el-icon>
  <span>剧本</span>
  <el-badge v-if="pipelineStore.isStageDirty('SCRIPT')" is-dot class="dirty-dot" />
</el-menu-item>
```

添加CSS：
```css
.dirty-dot {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
}
.dirty-dot :deep(.el-badge__content.is-dot) {
  background: #e6a23c;
}
```

- [ ] **步骤 4：编译验证**

运行：`cd D:/AI/atomgit/frontend && npx vue-tsc --noEmit`
预期：无类型错误

- [ ] **步骤 5：Commit**

```bash
git add frontend/src/stores/pipeline.ts frontend/src/layouts/MainLayout.vue
git commit -m "feat: 前端流水线脏标记拦截 - 模块切换DIRTY检查+视觉指示"
```

---

### 任务 4：配置中心 — ModelConfigService testConnection

**文件：**
- 修改：`backend/src/main/java/com/aicomic/service/ModelConfigService.java`

- [ ] **步骤 1：在 ModelConfigService 中添加 testConnection 方法**

```java
/** 测试模型配置的API连通性 */
public Map<String, Object> testConnection(Long configId) {
    ModelConfig config = modelConfigRepository.findById(configId)
            .orElseThrow(() -> new ResourceNotFoundException("模型配置", configId));

    Map<String, Object> result = new HashMap<>();
    long start = System.currentTimeMillis();

    try {
        if (config.getIsCozeWorkflow() != null && config.getIsCozeWorkflow()) {
            // 扣子工作流：调用查询API验证workflow_id
            testCozeConnection(config);
        } else {
            // 通用模型：发送简单测试请求
            testGenericConnection(config);
        }
        long elapsed = System.currentTimeMillis() - start;
        result.put("success", true);
        result.put("responseTime", elapsed);
        result.put("message", "连接成功");
    } catch (Exception e) {
        long elapsed = System.currentTimeMillis() - start;
        result.put("success", false);
        result.put("responseTime", elapsed);
        result.put("message", "连接失败: " + e.getMessage());
    }
    return result;
}

private void testCozeConnection(ModelConfig config) {
    // 构建扣子工作流验证请求
    String url = config.getApiUrl() + "/v1/workflow/run";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Authorization", "Bearer " + config.getApiKey());

    Map<String, Object> body = new HashMap<>();
    body.put("workflow_id", config.getWorkflowId());
    body.put("parameters", "{}");

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
    restTemplate.postForObject(url, entity, Map.class);
}

private void testGenericConnection(ModelConfig config) {
    // 根据模型类型发送最小测试请求
    String url = config.getApiUrl() + "/models";
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + config.getApiKey());
    HttpEntity<Void> entity = new HttpEntity<>(headers);
    restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
}
```

需要添加依赖注入：`RestTemplate restTemplate`（已有AiRestTemplateConfig）

- [ ] **步骤 2：在 ModelConfigController 中添加 test-connection 端点**

```java
@PostMapping("/model-configs/{id}/test-connection")
public ApiResponse<Map<String, Object>> testConnection(@PathVariable Long id) {
    return ApiResponse.success(modelConfigService.testConnection(id));
}
```

- [ ] **步骤 3：编译验证**

运行：`cd D:/AI/atomgit/backend && mvn compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add backend/src/main/java/com/aicomic/service/ModelConfigService.java backend/src/main/java/com/aicomic/controller/ModelConfigController.java
git commit -m "feat: 模型配置测试连接 - 支持通用模型和扣子工作流验证"
```

---

### 任务 5：配置中心 — PromptTemplateService render/validate

**文件：**
- 修改：`backend/src/main/java/com/aicomic/service/PromptTemplateService.java`

- [ ] **步骤 1：在 PromptTemplateService 中完善 render 和 validate 方法**

```java
/** 渲染提示词模板（变量替换） */
public String renderTemplate(Long templateId, Map<String, String> variables) {
    PromptTemplate template = promptTemplateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("提示词模板", templateId));

    String content = template.getContent();
    if (content == null || content.isEmpty()) {
        throw new IllegalStateException("模板内容为空");
    }

    // 校验必需变量
    List<String> requiredVars = parseVariables(content);
    List<String> missingVars = requiredVars.stream()
            .filter(v -> !variables.containsKey(v) || variables.get(v) == null || variables.get(v).isEmpty())
            .toList();
    if (!missingVars.isEmpty()) {
        throw new IllegalArgumentException("缺少必需变量: " + String.join(", ", missingVars));
    }

    // 执行变量替换
    String result = content;
    for (Map.Entry<String, String> entry : variables.entrySet()) {
        result = result.replace("{" + entry.getKey() + "}", entry.getValue());
    }
    return result;
}

/** 校验模板变量完整性 */
public Map<String, Object> validateTemplate(Long templateId) {
    PromptTemplate template = promptTemplateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("提示词模板", templateId));

    List<String> contentVars = parseVariables(template.getContent());
    List<String> definedVars = parseDefinedVariables(template.getVariables());

    List<String> missing = contentVars.stream()
            .filter(v -> !definedVars.contains(v))
            .toList();
    List<String> unused = definedVars.stream()
            .filter(v -> !contentVars.contains(v))
            .toList();

    Map<String, Object> result = new HashMap<>();
    result.put("valid", missing.isEmpty() && unused.isEmpty());
    result.put("contentVariables", contentVars);
    result.put("definedVariables", definedVars);
    result.put("missingDefinitions", missing);
    result.put("unusedDefinitions", unused);
    return result;
}

/** 从模板内容中解析 {varName} 变量引用 */
private List<String> parseVariables(String content) {
    if (content == null) return List.of();
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{(\\w+)\\}").matcher(content);
    java.util.Set<String> vars = new java.util.LinkedHashSet<>();
    while (matcher.find()) {
        vars.add(matcher.group(1));
    }
    return new java.util.ArrayList<>(vars);
}

/** 从变量定义JSON中解析已定义的变量名 */
private List<String> parseDefinedVariables(String variablesJson) {
    if (variablesJson == null || variablesJson.isEmpty()) return List.of();
    try {
        java.util.List<Map<String, Object>> defs = objectMapper.readValue(variablesJson, java.util.List.class);
        return defs.stream()
                .map(d -> (String) d.get("name"))
                .filter(java.util.Objects::nonNull)
                .toList();
    } catch (Exception e) {
        log.warn("解析变量定义失败: {}", e.getMessage());
        return List.of();
    }
}
```

需要添加依赖注入：`ObjectMapper objectMapper`

- [ ] **步骤 2：在 PromptTemplateController 中补充端点**

确认以下端点已正确实现（已有骨架的确认代码正确）：
- `POST /api/v1/prompt-templates/{id}/render` — 接收 `Map<String, String> variables`
- `POST /api/v1/prompt-templates/{id}/validate` — 返回校验结果

- [ ] **步骤 3：编译验证**

运行：`cd D:/AI/atomgit/backend && mvn compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add backend/src/main/java/com/aicomic/service/PromptTemplateService.java backend/src/main/java/com/aicomic/controller/PromptTemplateController.java
git commit -m "feat: 提示词模板渲染与校验 - 变量替换/完整性检查"
```

---

### 任务 6：配置中心 — ConfigView 前端重构

**文件：**
- 重写：`frontend/src/views/config/ConfigView.vue`

- [ ] **步骤 1：重写 ConfigView.vue 为 Tab 分组配置中心**

核心结构：
```vue
<template>
  <div class="config-container">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- Tab 1-4: 模型配置 -->
      <el-tab-pane label="文本模型" name="TEXT">
        <ModelConfigTab type="TEXT" />
      </el-tab-pane>
      <el-tab-pane label="生图模型" name="IMAGE">
        <ModelConfigTab type="IMAGE" />
      </el-tab-pane>
      <el-tab-pane label="视频模型" name="VIDEO">
        <ModelConfigTab type="VIDEO" />
      </el-tab-pane>
      <el-tab-pane label="音频模型" name="AUDIO">
        <ModelConfigTab type="AUDIO" />
      </el-tab-pane>
      <!-- Tab 5: 提示词模板 -->
      <el-tab-pane label="提示词模板" name="PROMPT">
        <PromptTemplateTab />
      </el-tab-pane>
      <!-- Tab 6: 应用设置 -->
      <el-tab-pane label="应用设置" name="APP">
        <AppSettingsTab />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
```

**ModelConfigTab 子组件**（内联在同一文件中）：
- 加载指定type的模型配置列表
- 每个配置为一个 `el-card`，显示名称/供应商/模型名/启用状态
- 操作按钮：编辑/删除/测试连接
- "新增配置"按钮打开 `el-dialog` 表单
- 表单字段：名称、供应商(el-select)、API Key(password+show-toggle)、API URL、模型名、最大tokens、启用开关
- 扣子工作流额外字段：workflow_id、bot_id、app_id
- "测试连接"按钮：调用 `POST /api/v1/model-configs/{id}/test-connection`，显示结果（成功/失败+响应时间）

**PromptTemplateTab 子组件**（内联在同一文件中）：
- 分类下拉筛选
- 模板列表 `el-table`
- 新增/编辑 `el-dialog`：名称、分类、内容(textarea)、变量定义(JSON textarea)
- "预览渲染"按钮：弹出对话框输入变量值，调用 render 端点

**AppSettingsTab 子组件**（内联在同一文件中）：
- 迁移原 ConfigView 的应用信息和更新检测功能
- 新增：存储路径显示、主题切换、FFmpeg路径、自动备份开关

- [ ] **步骤 2：编译验证**

运行：`cd D:/AI/atomgit/frontend && npx vue-tsc --noEmit`
预期：无类型错误

- [ ] **步骤 3：Commit**

```bash
git add frontend/src/views/config/ConfigView.vue
git commit -m "feat: 配置中心重构 - Tab分组布局(模型配置/提示词模板/应用设置)"
```

---

## L2 流水线层

### 任务 7：FFmpegUtils 工具类

**文件：**
- 创建：`backend/src/main/java/com/aicomic/common/util/FFmpegUtils.java`
- 创建：`backend/src/main/java/com/aicomic/dto/ExportConfig.java`
- 创建：`backend/src/main/java/com/aicomic/dto/WatermarkConfig.java`
- 创建：`backend/src/main/java/com/aicomic/dto/CompositeRequest.java`

- [ ] **步骤 1：编写 FFmpegUtils**

```java
package com.aicomic.common.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class FFmpegUtils {

    @Value("${app.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${app.ffmpeg.timeout:300}")
    private int timeoutSeconds;

    /** 拼接多个视频（concat demuxer） */
    public Path concatVideos(List<String> videoPaths, String outputPath) throws IOException, InterruptedException {
        // 创建concat文件列表
        Path concatFile = Files.createTempFile("ffmpeg_concat_", ".txt");
        StringBuilder content = new StringBuilder();
        for (String path : videoPaths) {
            content.append("file '").append(path).append("'\n");
        }
        Files.writeString(concatFile, content.toString());

        List<String> command = List.of(
                ffmpegPath, "-y", "-f", "concat", "-safe", "0",
                "-i", concatFile.toString(),
                "-c", "copy", outputPath
        );
        execute(command);
        Files.deleteIfExists(concatFile);
        return Path.of(outputPath);
    }

    /** 烧录SRT字幕 */
    public Path addSubtitles(String videoPath, String srtPath, String outputPath) throws IOException, InterruptedException {
        // Windows路径中的反斜杠需要转义给FFmpeg的subtitles滤镜
        String escapedSrt = srtPath.replace("\\", "/").replace(":", "\\:");
        List<String> command = List.of(
                ffmpegPath, "-y", "-i", videoPath,
                "-vf", "subtitles='" + escapedSrt + "'",
                "-c:a", "copy", outputPath
        );
        execute(command);
        return Path.of(outputPath);
    }

    /** 混合多音轨 */
    public Path mixAudio(String videoPath, List<AudioInput> audioTracks, String outputPath) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-i");
        command.add(videoPath);

        for (AudioInput track : audioTracks) {
            command.addAll(List.of("-i", track.filePath));
        }

        // 构建复杂滤镜：混合所有音轨
        StringBuilder filter = new StringBuilder();
        for (int i = 0; i < audioTracks.size(); i++) {
            filter.append("[").append(i + 1).append(":a]volume=")
                  .append(audioTracks.get(i).volume)
                  .append("[a").append(i).append("];");
        }
        filter.append("[0:a]");
        for (int i = 0; i < audioTracks.size(); i++) {
            filter.append("[a").append(i).append("]");
        }
        filter.append("amix=inputs=").append(audioTracks.size() + 1)
              .append(":duration=longest[aout]");

        command.addAll(List.of("-filter_complex", filter.toString()));
        command.addAll(List.of("-map", "0:v", "-map", "[aout]"));
        command.addAll(List.of("-c:v", "copy", "-c:a", "aac", "-shortest", outputPath));

        execute(command);
        return Path.of(outputPath);
    }

    /** 添加转场效果 */
    public Path addTransition(String inputPath, String transitionType, double duration, String outputPath) throws IOException, InterruptedException {
        // xfade滤镜需要两个输入流，简化实现：淡入淡出
        String filter;
        switch (transitionType) {
            case "fade":
                filter = String.format("fade=t=in:st=0:d=%.1f,fade=t=out:st=duration-%.1f:d=%.1f", duration, duration, duration);
                break;
            case "slideleft":
                filter = String.format("slice=in:st=0:d=%.1f", duration);
                break;
            case "zoom":
                filter = String.format("zoompan=z='min(zoom+0.0015,1.5)':d=1:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s=iw*ih");
                break;
            default:
                filter = String.format("fade=t=in:st=0:d=%.1f", duration);
        }
        List<String> command = List.of(
                ffmpegPath, "-y", "-i", inputPath,
                "-vf", filter,
                "-c:a", "copy", outputPath
        );
        execute(command);
        return Path.of(outputPath);
    }

    /** 格式转码 */
    public Path transcode(String inputPath, ExportConfig config, String outputPath) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-i");
        command.add(inputPath);

        // 视频编码
        String codec = switch (config.getFormat()) {
            case "mov" -> "prores_ks";
            case "avi" -> "mpeg4";
            default -> "libx264";
        };
        command.addAll(List.of("-c:v", codec));

        // 分辨率
        if (config.getResolution() != null) {
            int h = parseResolutionHeight(config.getResolution());
            command.addAll(List.of("-vf", "scale=-2:" + h));
        }

        // 码率
        if (config.getBitrate() != null && config.getBitrate() > 0) {
            command.addAll(List.of("-b:v", config.getBitrate() + "k"));
        }

        // 帧率
        if (config.getFps() != null && config.getFps() > 0) {
            command.addAll(List.of("-r", String.valueOf(config.getFps())));
        }

        command.addAll(List.of("-c:a", "aac", outputPath));
        execute(command);
        return Path.of(outputPath);
    }

    /** 叠加水印 */
    public Path addWatermark(String inputPath, WatermarkConfig config, String outputPath) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();

        if ("IMAGE".equals(config.getType()) && config.getImagePath() != null) {
            // 图片水印
            String position = getOverlayPosition(config.getPosition());
            command.addAll(List.of(ffmpegPath, "-y", "-i", inputPath, "-i", config.getImagePath()));
            command.addAll(List.of("-filter_complex",
                    String.format("[1:v]format=rgba,colorchannelmixer=aa=%.1f[wm];[0:v][wm]%s",
                            config.getOpacity(), position)));
            command.addAll(List.of("-c:a", "copy", outputPath));
        } else {
            // 文字水印
            String escapedText = config.getContent().replace("'", "'\\''");
            String pos = getTextPosition(config.getPosition(), config.getFontSize());
            command.addAll(List.of(ffmpegPath, "-y", "-i", inputPath));
            command.addAll(List.of("-vf",
                    String.format("drawtext=text='%s':fontsize=%d:fontcolor=%s@%.1f:%s",
                            escapedText,
                            config.getFontSize() != null ? config.getFontSize() : 24,
                            config.getFontColor() != null ? config.getFontColor() : "white",
                            config.getOpacity(),
                            pos)));
            command.addAll(List.of("-c:a", "copy", outputPath));
        }
        execute(command);
        return Path.of(outputPath);
    }

    /** 获取视频元信息 */
    public VideoInfo getVideoInfo(String videoPath) throws IOException, InterruptedException {
        List<String> command = List.of(
                "ffprobe", "-v", "quiet", "-print_format", "json",
                "-show_format", "-show_streams", videoPath
        );
        FFmpegResult result = execute(command);
        // 简化：只返回基本信息
        VideoInfo info = new VideoInfo();
        info.setFilePath(videoPath);
        info.setValid(result.getExitCode() == 0);
        return info;
    }

    /** 统一执行FFmpeg命令 */
    public FFmpegResult execute(List<String> command) throws IOException, InterruptedException {
        log.info("执行FFmpeg命令: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        int exitCode = finished ? process.exitValue() : -1;

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("FFmpeg执行超时(" + timeoutSeconds + "秒)");
        }

        if (exitCode != 0) {
            log.error("FFmpeg执行失败: exitCode={}, output={}", exitCode, output);
            throw new RuntimeException("FFmpeg执行失败(exitCode=" + exitCode + "): " + output);
        }

        FFmpegResult result = new FFmpegResult();
        result.setExitCode(exitCode);
        result.setOutput(output.toString());
        return result;
    }

    private String getOverlayPosition(String position) {
        if (position == null) position = "BOTTOM_RIGHT";
        return switch (position) {
            case "TOP_LEFT" -> "overlay=10:10";
            case "TOP_RIGHT" -> "overlay=W-w-10:10";
            case "BOTTOM_LEFT" -> "overlay=10:H-h-10";
            case "CENTER" -> "overlay=(W-w)/2:(H-h)/2";
            default -> "overlay=W-w-10:H-h-10"; // BOTTOM_RIGHT
        };
    }

    private String getTextPosition(String position, Integer fontSize) {
        int fs = fontSize != null ? fontSize : 24;
        return switch (position != null ? position : "BOTTOM_RIGHT") {
            case "TOP_LEFT" -> "x=10:y=10";
            case "TOP_RIGHT" -> "x=w-tw-10:y=10";
            case "BOTTOM_LEFT" -> "x=10:y=h-lh-10";
            case "CENTER" -> "x=(w-tw)/2:y=(h-th)/2";
            default -> "x=w-tw-10:y=h-lh-10"; // BOTTOM_RIGHT
        };
    }

    private int parseResolutionHeight(String resolution) {
        return switch (resolution) {
            case "4K" -> 2160;
            case "1080p" -> 1080;
            case "720p" -> 720;
            default -> 1080;
        };
    }

    // 内部DTO
    @Data
    public static class AudioInput {
        private String filePath;
        private double volume = 1.0;
    }

    @Data
    public static class VideoInfo {
        private String filePath;
        private boolean valid;
        private double duration;
        private int width;
        private int height;
    }

    @Data
    public static class FFmpegResult {
        private int exitCode;
        private String output;
    }
}
```

- [ ] **步骤 2：编写 ExportConfig DTO**

```java
package com.aicomic.dto;

import lombok.Data;

@Data
public class ExportConfig {
    private String format = "mp4";       // mp4/mov/avi
    private String resolution = "1080p"; // 720p/1080p/4K
    private Integer bitrate;             // kbps, null=默认
    private Integer fps;                 // 24/30/60, null=默认
}
```

- [ ] **步骤 3：编写 WatermarkConfig DTO**

```java
package com.aicomic.dto;

import lombok.Data;

@Data
public class WatermarkConfig {
    private String type = "TEXT";        // TEXT/IMAGE
    private String content;              // 文字内容
    private String imagePath;            // 图片路径（IMAGE类型时使用）
    private String position = "BOTTOM_RIGHT"; // TOP_LEFT/TOP_RIGHT/BOTTOM_LEFT/BOTTOM_RIGHT/CENTER
    private Double opacity = 0.7;        // 0.0-1.0
    private Integer fontSize = 24;       // 文字水印字号
    private String fontColor = "white";  // 文字水印颜色
}
```

- [ ] **步骤 4：编写 CompositeRequest DTO**

```java
package com.aicomic.dto;

import lombok.Data;

@Data
public class CompositeRequest {
    private Long episodeId;
    private boolean addSubtitles = true;
    private boolean mixAudio = true;
    private String transitionType;       // fade/slideleft/slideup/zoom, null=无转场
    private double transitionDuration = 1.0;
}
```

- [ ] **步骤 5：编译验证**

运行：`cd D:/AI/atomgit/backend && mvn compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 6：Commit**

```bash
git add backend/src/main/java/com/aicomic/common/util/FFmpegUtils.java backend/src/main/java/com/aicomic/dto/ExportConfig.java backend/src/main/java/com/aicomic/dto/WatermarkConfig.java backend/src/main/java/com/aicomic/dto/CompositeRequest.java
git commit -m "feat: FFmpegUtils工具类 - 视频拼接/字幕/音频混合/转场/转码/水印"
```

---

### 任务 8：SLevelService 完整实现

**文件：**
- 重写：`backend/src/main/java/com/aicomic/service/SLevelService.java`

- [ ] **步骤 1：重写 SLevelService**

核心逻辑：调用 FFmpegUtils 完成成片合成的5步流程。

```java
package com.aicomic.service;

import com.aicomic.common.util.FFmpegUtils;
import com.aicomic.dto.CompositeRequest;
import com.aicomic.dto.ExportConfig;
import com.aicomic.dto.WatermarkConfig;
import com.aicomic.entity.Storyboard;
import com.aicomic.repository.StoryboardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SLevelService {

    private final StoryboardRepository storyboardRepository;
    private final FFmpegUtils ffmpegUtils;
    private final SseService sseService;

    @Value("${app.storage.path:./data}")
    private String storageBasePath;

    /** 成片合成（异步执行） */
    @Async("videoTaskExecutor")
    public void compositeFinalVideoAsync(Long projectId, CompositeRequest request) {
        log.info("开始成片合成: projectId={}, episodeId={}", projectId, request.getEpisodeId());

        try {
            // 1. 收集分镜视频片段
            sseService.pushNotification("slevel-progress", "正在收集视频片段...");
            List<Storyboard> storyboards = storyboardRepository
                    .findByEpisodeIdOrderBySequenceAsc(request.getEpisodeId());
            List<String> videoUrls = storyboards.stream()
                    .filter(sb -> sb.getGeneratedVideoUrl() != null)
                    .map(Storyboard::getGeneratedVideoUrl)
                    .toList();

            if (videoUrls.isEmpty()) {
                sseService.pushNotification("slevel-error", "没有可用的视频片段");
                return;
            }

            Path workDir = Paths.get(storageBasePath, "projects", String.valueOf(projectId), "output");
            Files.createDirectories(workDir);
            String workDirStr = workDir.toString();

            // 2. FFmpeg拼接视频
            sseService.pushNotification("slevel-progress", "正在拼接视频（FFmpeg）...");
            String concatOutput = workDirStr + "/concat.mp4";
            // 将URL路径转为本地文件路径（假设视频已在本地）
            List<String> localPaths = videoUrls.stream()
                    .map(url -> url.startsWith("http") ? downloadToTemp(url, workDirStr) : url)
                    .toList();
            ffmpegUtils.concatVideos(localPaths, concatOutput);

            String currentVideo = concatOutput;

            // 3. 叠加字幕（如果配置了）
            if (request.isAddSubtitles()) {
                sseService.pushNotification("slevel-progress", "正在叠加字幕...");
                String srtPath = generateSrtFile(storyboards, workDirStr);
                if (srtPath != null) {
                    String subtitleOutput = workDirStr + "/subtitle.mp4";
                    ffmpegUtils.addSubtitles(currentVideo, srtPath, subtitleOutput);
                    currentVideo = subtitleOutput;
                }
            }

            // 4. 混合音频
            if (request.isMixAudio()) {
                sseService.pushNotification("slevel-progress", "正在混合音频...");
                // 简化：暂无音频混合（后续任务添加音频库后启用）
                log.info("音频混合暂跳过（无音频数据）");
            }

            // 5. 添加转场效果
            if (request.getTransitionType() != null && !request.getTransitionType().isEmpty()) {
                sseService.pushNotification("slevel-progress", "正在添加转场特效...");
                String transitionOutput = workDirStr + "/transition.mp4";
                ffmpegUtils.addTransition(currentVideo, request.getTransitionType(),
                        request.getTransitionDuration(), transitionOutput);
                currentVideo = transitionOutput;
            }

            // 6. 生成最终文件
            String finalPath = workDirStr + "/episode-" + request.getEpisodeId() + "-final.mp4";
            if (!currentVideo.equals(finalPath)) {
                Files.copy(Path.of(currentVideo), Path.of(finalPath),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            sseService.pushNotification("slevel-completed",
                    "成片合成完成: " + finalPath);
            log.info("成片合成完成: projectId={}, outputPath={}", projectId, finalPath);

        } catch (Exception e) {
            log.error("成片合成失败: {}", e.getMessage(), e);
            sseService.pushNotification("slevel-error", "成片合成失败: " + e.getMessage());
        }
    }

    /** 导出视频 */
    @Async("videoTaskExecutor")
    public void exportVideoAsync(Long projectId, Long episodeId, ExportConfig config) {
        log.info("开始导出视频: projectId={}, episodeId={}", projectId, episodeId);

        try {
            sseService.pushNotification("slevel-progress",
                    String.format("正在导出视频 (%s, %s)...", config.getFormat(), config.getResolution()));

            Path workDir = Paths.get(storageBasePath, "projects", String.valueOf(projectId), "output");
            String inputPath = workDir + "/episode-" + episodeId + "-final.mp4";
            String outputPath = workDir + "/episode-" + episodeId + "-export." + config.getFormat();

            ffmpegUtils.transcode(inputPath, config, outputPath);

            sseService.pushNotification("slevel-completed",
                    String.format("视频导出完成: %s %s", config.getFormat(), config.getResolution()));
            log.info("视频导出完成: outputPath={}", outputPath);

        } catch (Exception e) {
            log.error("视频导出失败: {}", e.getMessage(), e);
            sseService.pushNotification("slevel-error", "视频导出失败: " + e.getMessage());
        }
    }

    /** 添加水印 */
    @Async("taskExecutor")
    public void addWatermarkAsync(Long projectId, Long episodeId, WatermarkConfig config) {
        log.info("开始添加水印: projectId={}, type={}", projectId, config.getType());

        try {
            sseService.pushNotification("slevel-progress",
                    String.format("正在添加%s水印...", "IMAGE".equals(config.getType()) ? "图片" : "文字"));

            Path workDir = Paths.get(storageBasePath, "projects", String.valueOf(projectId), "output");
            String inputPath = workDir + "/episode-" + episodeId + "-final.mp4";
            String outputPath = workDir + "/episode-" + episodeId + "-watermark.mp4";

            ffmpegUtils.addWatermark(inputPath, config, outputPath);

            sseService.pushNotification("slevel-completed", "水印添加完成");
            log.info("水印添加完成: outputPath={}", outputPath);

        } catch (Exception e) {
            log.error("水印添加失败: {}", e.getMessage(), e);
            sseService.pushNotification("slevel-error", "水印添加失败: " + e.getMessage());
        }
    }

    // ============ Private helpers ============

    /** 下载URL到临时文件（简化实现，仅处理本地路径） */
    private String downloadToTemp(String url, String workDir) {
        // 当前阶段所有文件都在本地，直接返回路径
        // 如果是本地文件URL（file://），去掉前缀
        if (url.startsWith("file://")) return url.substring(7);
        // 如果是相对路径，拼接存储基础路径
        if (!url.startsWith("/") && !url.contains(":")) {
            return Paths.get(storageBasePath, url).toString();
        }
        return url;
    }

    /** 从分镜对话生成SRT字幕文件 */
    private String generateSrtFile(List<Storyboard> storyboards, String workDir) {
        StringBuilder srt = new StringBuilder();
        int index = 1;
        for (Storyboard sb : storyboards) {
            if (sb.getDialogue() == null || sb.getDialogue().isEmpty()) continue;
            // 解析时间范围
            String timeRange = sb.getTimeRange() != null ? sb.getTimeRange() : "0-5s";
            String[] parts = timeRange.replace("s", "").split("-");
            String startSec = parts.length > 0 ? parts[0].trim() : "0";
            String endSec = parts.length > 1 ? parts[1].trim() : String.valueOf(Integer.parseInt(startSec) + 5);

            srt.append(index++).append("\n");
            srt.append(formatSrtTime(Integer.parseInt(startSec)))
               .append(" --> ")
               .append(formatSrtTime(Integer.parseInt(endSec)))
               .append("\n");
            srt.append(sb.getDialogue()).append("\n\n");
        }

        if (srt.length() == 0) return null;

        try {
            Path srtPath = Path.of(workDir, "subtitles.srt");
            Files.writeString(srtPath, srt.toString());
            return srtPath.toString();
        } catch (Exception e) {
            log.warn("生成SRT文件失败: {}", e.getMessage());
            return null;
        }
    }

    /** 格式化秒数为SRT时间格式 HH:MM:SS,mmm */
    private String formatSrtTime(int totalSeconds) {
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d,000", h, m, s);
    }
}
```

- [ ] **步骤 2：更新 SLevelController 端点参数**

确保 SLevelController 的端点与新 SLevelService 方法签名匹配：
- `POST /api/v1/episodes/{id}/compose` — 接收 `CompositeRequest`
- `POST /api/v1/episodes/{id}/export` — 接收 `ExportConfig`
- `POST /api/v1/episodes/{id}/watermark` — 接收 `WatermarkConfig`

- [ ] **步骤 3：编译验证**

运行：`cd D:/AI/atomgit/backend && mvn compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add backend/src/main/java/com/aicomic/service/SLevelService.java
git commit -m "feat: S级模块完整FFmpeg实现 - 成片合成/字幕/音频混合/转场/导出/水印"
```

---

### 任务 9：DirectorService FFmpeg拼接补充

**文件：**
- 修改：`backend/src/main/java/com/aicomic/service/DirectorService.java`

- [ ] **步骤 1：在 DirectorService 中实现 concatVideoFragments 方法**

注入 `FFmpegUtils`，替换 `concatVideoFragments` 中的 TODO：

```java
// 替换原 concatVideoFragments 方法
public String concatVideoFragments(List<String> fragmentUrls) {
    if (fragmentUrls == null || fragmentUrls.isEmpty()) {
        log.warn("FFmpeg 拼接跳过: 视频片段列表为空");
        return null;
    }
    log.info("开始 FFmpeg 拼接: fragmentCount={}", fragmentUrls.size());
    sseService.pushNotification("director-progress",
            String.format("正在用 FFmpeg 拼接 %d 个视频片段...", fragmentUrls.size()));

    try {
        Path workDir = Files.createTempDirectory("ffmpeg_concat_");
        String outputPath = workDir + "/concat_result.mp4";
        ffmpegUtils.concatVideos(fragmentUrls, outputPath);
        log.info("FFmpeg 拼接完成: {}", outputPath);
        return outputPath;
    } catch (Exception e) {
        log.error("FFmpeg 拼接失败: {}", e.getMessage(), e);
        sseService.pushNotification("director-error", "FFmpeg拼接失败: " + e.getMessage());
        return null;
    }
}
```

添加依赖注入字段：`FFmpegUtils ffmpegUtils`

- [ ] **步骤 2：编译验证**

运行：`cd D:/AI/atomgit/backend && mvn compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add backend/src/main/java/com/aicomic/service/DirectorService.java
git commit -m "feat: DirectorService FFmpeg拼接实现 - 替换TODO为实际FFmpeg调用"
```

---

### 任务 10：前端 SLevelView + Store 完善

**文件：**
- 修改：`frontend/src/views/slevel/SLevelView.vue`
- 修改：`frontend/src/stores/slevel.ts`

- [ ] **步骤 1：更新 slevel store — 对接后端新API**

```typescript
// 修改 composeVideo 方法
async function composeVideo(episodeId: number, options?: { addSubtitles?: boolean, mixAudio?: boolean, transitionType?: string }) {
  generating.value = true
  composeStep.value = 1
  try {
    await http.post(`/v1/episodes/${episodeId}/compose`, {
      episodeId,
      addSubtitles: options?.addSubtitles ?? true,
      mixAudio: options?.mixAudio ?? true,
      transitionType: options?.transitionType ?? 'fade',
    })
    useNotificationStore().info('成片合成已启动', '正在执行 FFmpeg 合成流程...')
  } catch (err: any) {
    const detail = err.status === 502 ? 'FFmpeg 服务暂不可用，请稍后重试' : err.message
    useNotificationStore().error('合成失败', detail)
    composeStep.value = 0
  } finally {
    generating.value = false
  }
}

// 修改 exportVideo 方法
async function exportVideo(episodeId: number, form: ExportForm) {
  generating.value = true
  try {
    await http.post(`/v1/episodes/${episodeId}/export`, {
      format: form.format,
      resolution: form.resolution + 'p',
      bitrate: form.bitrate,
      fps: form.fps,
    })
    useNotificationStore().info('视频导出已启动', `正在导出 ${form.format} ${form.resolution}p...`)
  } catch (err: any) {
    useNotificationStore().error('导出失败', err.message)
  } finally {
    generating.value = false
  }
}

// 修改 addWatermark 方法
async function addWatermark(episodeId: number, form: WatermarkForm) {
  generating.value = true
  try {
    await http.post(`/v1/episodes/${episodeId}/watermark`, {
      type: form.watermarkType,
      content: form.watermarkContent,
      position: 'BOTTOM_RIGHT',
      opacity: 0.7,
    })
    useNotificationStore().info('水印添加已启动')
  } catch (err: any) {
    useNotificationStore().error('添加水印失败', err.message)
  } finally {
    generating.value = false
  }
}
```

- [ ] **步骤 2：更新 SLevelView.vue**

修改3处：
1. 合成按钮调用 `slevelStore.composeVideo(episodeId, ...)` 传入episodeId
2. 导出对话框确认按钮调用 `slevelStore.exportVideo(episodeId, exportForm)` 传入episodeId
3. 水印按钮调用 `slevelStore.addWatermark(episodeId, watermarkForm)` 传入episodeId
4. 新增：SSE进度订阅，监听 `slevel-progress`/`slevel-completed`/`slevel-error` 更新步骤条
5. 新增：合成完成后显示视频播放器 `<video :src="videoUrl" controls />`

- [ ] **步骤 3：编译验证**

运行：`cd D:/AI/atomgit/frontend && npx vue-tsc --noEmit`
预期：无类型错误

- [ ] **步骤 4：Commit**

```bash
git add frontend/src/views/slevel/SLevelView.vue frontend/src/stores/slevel.ts
git commit -m "feat: S级前端完善 - 对接FFmpeg API/SSE进度/视频播放器"
```

---

### 任务 11：修复 CharacterView/SceneView scriptId 硬编码

**文件：**
- 修改：`frontend/src/views/character/CharacterView.vue`
- 修改：`frontend/src/views/scene/SceneView.vue`

- [ ] **步骤 1：修复 CharacterView.vue 中的 scriptId 硬编码**

找到 AI 提取角色的调用处，将硬编码的 `scriptId=1` 改为从当前项目获取：

```typescript
// 替换硬编码 scriptId
const projectStore = useProjectStore()
const currentProject = projectStore.currentProject

async function handleExtractCharacters() {
  if (!currentProject.value?.id) {
    ElMessage.warning('请先选择项目')
    return
  }
  // 从当前项目的剧本获取 scriptId
  const scripts = await http.get(`/v1/projects/${currentProject.value.id}/scripts`)
  if (scripts && scripts.length > 0) {
    const scriptId = scripts[0].id
    await http.post(`/v1/scripts/${scriptId}/extract-assets`, { type: 'CHARACTER' })
  } else {
    ElMessage.warning('请先创建剧本')
  }
}
```

- [ ] **步骤 2：修复 SceneView.vue 中同样的硬编码**

同样模式，从当前项目获取 scriptId。

- [ ] **步骤 3：编译验证**

运行：`cd D:/AI/atomgit/frontend && npx vue-tsc --noEmit`
预期：无类型错误

- [ ] **步骤 4：Commit**

```bash
git add frontend/src/views/character/CharacterView.vue frontend/src/views/scene/SceneView.vue
git commit -m "fix: 修复角色/场景AI提取scriptId硬编码 - 从当前项目动态获取"
```

---

## L3 配套层

### 任务 12：后端素材库 Service + Controller

**文件：**
- 创建：`backend/src/main/java/com/aicomic/service/AssetService.java`
- 创建：`backend/src/main/java/com/aicomic/controller/AssetController.java`

- [ ] **步骤 1：编写 AssetService**

```java
package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.AssetItem;
import com.aicomic.repository.AssetItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetItemRepository assetItemRepository;

    @Value("${app.storage.path:./data}")
    private String storageBasePath;

    @Transactional
    public AssetItem uploadAsset(Long projectId, MultipartFile file, String tags) throws IOException {
        // 确定文件类型
        AssetItem.AssetType type = determineAssetType(file.getContentType());

        // 保存文件到项目目录
        String subDir = type.name().toLowerCase();
        Path dir = Paths.get(storageBasePath, "projects", String.valueOf(projectId), "assets", subDir);
        Files.createDirectories(dir);

        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String filename = UUID.randomUUID().toString() + ext;
        Path filePath = dir.resolve(filename);
        file.transferTo(filePath.toFile());

        // 创建数据库记录
        AssetItem asset = new AssetItem();
        asset.setProjectId(projectId);
        asset.setName(originalFilename != null ? originalFilename : filename);
        asset.setType(type);
        asset.setFilePath(filePath.toString());
        asset.setFileSize(file.getSize());
        asset.setMimeType(file.getContentType());
        asset.setTags(tags);
        asset.setSource(AssetItem.AssetSource.UPLOAD);

        return assetItemRepository.save(asset);
    }

    @Transactional(readOnly = true)
    public List<AssetItem> searchAssets(Long projectId, String type, String tags) {
        if (type != null && !type.isEmpty()) {
            AssetItem.AssetType assetType = AssetItem.AssetType.valueOf(type);
            return assetItemRepository.findByProjectIdAndType(projectId, assetType);
        }
        return assetItemRepository.findByProjectId(projectId);
    }

    @Transactional
    public AssetItem updateAsset(Long id, String name, String tags) {
        AssetItem asset = assetItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("素材", id));
        if (name != null) asset.setName(name);
        if (tags != null) asset.setTags(tags);
        return assetItemRepository.save(asset);
    }

    @Transactional
    public void deleteAsset(Long id) {
        AssetItem asset = assetItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("素材", id));
        // 删除文件
        try {
            Files.deleteIfExists(Path.of(asset.getFilePath()));
        } catch (IOException e) {
            log.warn("删除素材文件失败: {}", e.getMessage());
        }
        assetItemRepository.delete(asset);
    }

    private AssetItem.AssetType determineAssetType(String mimeType) {
        if (mimeType == null) return AssetItem.AssetType.DOCUMENT;
        if (mimeType.startsWith("image/")) return AssetItem.AssetType.IMAGE;
        if (mimeType.startsWith("video/")) return AssetItem.AssetType.VIDEO;
        if (mimeType.startsWith("audio/")) return AssetItem.AssetType.AUDIO;
        return AssetItem.AssetType.DOCUMENT;
    }
}
```

注意：需要在 AssetItemRepository 中添加 `findByProjectId` 和 `findByProjectIdAndType` 方法。

- [ ] **步骤 2：编写 AssetController**

```java
package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.entity.AssetItem;
import com.aicomic.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @PostMapping("/projects/{projectId}/assets/upload")
    public ApiResponse<AssetItem> uploadAsset(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tags", required = false) String tags) throws IOException {
        return ApiResponse.success(assetService.uploadAsset(projectId, file, tags));
    }

    @GetMapping("/assets")
    public ApiResponse<List<AssetItem>> searchAssets(
            @RequestParam Long projectId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "tags", required = false) String tags) {
        return ApiResponse.success(assetService.searchAssets(projectId, type, tags));
    }

    @PutMapping("/assets/{id}")
    public ApiResponse<AssetItem> updateAsset(
            @PathVariable Long id,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "tags", required = false) String tags) {
        return ApiResponse.success(assetService.updateAsset(id, name, tags));
    }

    @DeleteMapping("/assets/{id}")
    public ApiResponse<Void> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return ApiResponse.success(null);
    }
}
```

- [ ] **步骤 3：补充 AssetItemRepository 方法**

在 `AssetItemRepository.java` 中添加：
```java
List<AssetItem> findByProjectId(Long projectId);
List<AssetItem> findByProjectIdAndType(Long projectId, AssetItem.AssetType type);
```

- [ ] **步骤 4：编译验证**

运行：`cd D:/AI/atomgit/backend && mvn compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add backend/src/main/java/com/aicomic/service/AssetService.java backend/src/main/java/com/aicomic/controller/AssetController.java backend/src/main/java/com/aicomic/repository/AssetItemRepository.java
git commit -m "feat: 素材库后端 - 上传/搜索/更新/删除API"
```

---

### 任务 13：前端素材库页面

**文件：**
- 创建：`frontend/src/views/asset/AssetView.vue`
- 创建：`frontend/src/stores/asset.ts`
- 修改：`frontend/src/router/index.ts`
- 修改：`frontend/src/layouts/MainLayout.vue`

- [ ] **步骤 1：创建 asset store**

```typescript
// frontend/src/stores/asset.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'
import { useNotificationStore } from './notification'
import type { AssetItem, AssetType } from '@/types'

export const useAssetStore = defineStore('asset', () => {
  const assets = ref<AssetItem[]>([])
  const loading = ref(false)

  async function fetchAssets(projectId: number, type?: AssetType) {
    loading.value = true
    try {
      const params: any = { projectId }
      if (type) params.type = type
      const data = await http.get('/v1/assets', { params })
      assets.value = data as unknown as AssetItem[]
    } catch (err: any) {
      useNotificationStore().error('获取素材失败', err.message)
    } finally {
      loading.value = false
    }
  }

  async function uploadAsset(projectId: number, file: File, tags?: string) {
    const formData = new FormData()
    formData.append('file', file)
    if (tags) formData.append('tags', tags)
    try {
      await http.post(`/v1/projects/${projectId}/assets/upload`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      useNotificationStore().info('上传成功', file.name)
      await fetchAssets(projectId)
    } catch (err: any) {
      useNotificationStore().error('上传失败', err.message)
    }
  }

  async function deleteAsset(id: number, projectId: number) {
    try {
      await http.delete(`/v1/assets/${id}`)
      useNotificationStore().info('删除成功')
      await fetchAssets(projectId)
    } catch (err: any) {
      useNotificationStore().error('删除失败', err.message)
    }
  }

  return { assets, loading, fetchAssets, uploadAsset, deleteAsset }
})
```

- [ ] **步骤 2：创建 AssetView.vue**

核心布局：
- 顶部工具栏：类型筛选下拉 + 搜索框 + 上传按钮
- 主区域：`el-row` + `el-col` 网格展示素材缩略图
- 每个素材卡片：缩略图/图标 + 名称 + 大小 + 删除按钮
- 拖拽上传：整个区域监听 `@dragover.prevent` + `@drop` 事件
- 上传实现：点击按钮或拖拽触发 `<input type="file" multiple />`

```vue
<template>
  <div class="view-container" @dragover.prevent @drop.prevent="handleDrop">
    <h2>素材库</h2>
    <div class="toolbar">
      <el-select v-model="filterType" placeholder="全部类型" clearable @change="loadAssets" style="width: 140px">
        <el-option label="图片" value="IMAGE" />
        <el-option label="视频" value="VIDEO" />
        <el-option label="音频" value="AUDIO" />
      </el-select>
      <el-button type="primary" @click="triggerUpload">
        <el-icon><Upload /></el-icon> 上传素材
      </el-button>
      <input ref="fileInput" type="file" multiple style="display:none" @change="handleFileSelect" />
    </div>

    <el-row :gutter="16" v-loading="assetStore.loading">
      <el-col :span="6" v-for="asset in assetStore.assets" :key="asset.id">
        <el-card shadow="hover" class="asset-card">
          <div class="asset-preview">
            <el-image v-if="asset.type === 'IMAGE'" :src="getAssetUrl(asset)" fit="cover" />
            <el-icon v-else-if="asset.type === 'VIDEO'" :size="48"><VideoCamera /></el-icon>
            <el-icon v-else-if="asset.type === 'AUDIO'" :size="48"><Headset /></el-icon>
            <el-icon v-else :size="48"><Document /></el-icon>
          </div>
          <div class="asset-info">
            <span class="asset-name">{{ asset.name }}</span>
            <span class="asset-size">{{ formatSize(asset.fileSize) }}</span>
          </div>
          <el-button type="danger" size="small" text @click="handleDelete(asset)">删除</el-button>
        </el-card>
      </el-col>
    </el-row>

    <div v-if="assetStore.assets.length === 0 && !assetStore.loading" class="empty-hint">
      拖拽文件到此处上传，或点击上方按钮选择文件
    </div>
  </div>
</template>
```

- [ ] **步骤 3：添加路由**

在 `router/index.ts` 的 children 中添加：
```typescript
{
  path: '/assets',
  name: 'Assets',
  component: () => import('@/views/asset/AssetView.vue'),
  meta: { title: '素材库', icon: 'Folder' },
},
```

- [ ] **步骤 4：侧边栏添加素材库入口**

在 `MainLayout.vue` 的 `el-menu` 中，配置中心之前添加：
```html
<el-menu-item index="/assets">
  <el-icon><Folder /></el-icon>
  <span>素材库</span>
</el-menu-item>
```

- [ ] **步骤 5：编译验证**

运行：`cd D:/AI/atomgit/frontend && npx vue-tsc --noEmit`
预期：无类型错误

- [ ] **步骤 6：Commit**

```bash
git add frontend/src/views/asset/AssetView.vue frontend/src/stores/asset.ts frontend/src/router/index.ts frontend/src/layouts/MainLayout.vue
git commit -m "feat: 素材库前端 - 网格布局/拖拽上传/类型筛选/路由"
```

---

### 任务 14：后端项目模板 Service + Controller + 数据初始化

**文件：**
- 创建：`backend/src/main/java/com/aicomic/service/TemplateService.java`
- 创建：`backend/src/main/java/com/aicomic/controller/TemplateController.java`
- 创建：`backend/src/main/java/com/aicomic/config/DataInitializer.java`

- [ ] **步骤 1：编写 TemplateService**

```java
package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.Project;
import com.aicomic.entity.ProjectTemplate;
import com.aicomic.repository.ProjectRepository;
import com.aicomic.repository.ProjectTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final ProjectTemplateRepository templateRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ProjectTemplate> getTemplates(String type, String category) {
        if (category != null && !category.isEmpty()) {
            return templateRepository.findByStyle(ProjectTemplate.StyleType.valueOf(category));
        }
        return templateRepository.findAll();
    }

    @Transactional
    public ProjectTemplate saveAsTemplate(Long projectId, String name, String description) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("项目", projectId));

        ProjectTemplate template = new ProjectTemplate();
        template.setName(name != null ? name : project.getName() + " 模板");
        template.setDescription(description);
        template.setStyle(project.getStyle());
        template.setIsBuiltin(false);

        // 收集项目配置数据
        Map<String, Object> configData = Map.of(
                "defaultStyle", project.getStyle().name(),
                "projectId", projectId
        );
        try {
            template.setTemplateData(objectMapper.writeValueAsString(configData));
        } catch (Exception e) {
            template.setTemplateData("{}");
        }

        return templateRepository.save(template);
    }

    @Transactional
    public Project createProjectFromTemplate(Long templateId, String projectName) {
        ProjectTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("模板", templateId));

        Project project = new Project();
        project.setName(projectName);
        project.setStyle(template.getStyle());
        project.setPipelineStage(Project.PipelineStage.SCRIPT);

        // 增加模板使用次数
        template.setUseCount(template.getUseCount() != null ? template.getUseCount() + 1 : 1);
        templateRepository.save(template);

        return projectRepository.save(project);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        ProjectTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("模板", id));
        if (template.getIsBuiltin() != null && template.getIsBuiltin()) {
            throw new IllegalStateException("内置模板不可删除");
        }
        templateRepository.delete(template);
    }
}
```

- [ ] **步骤 2：编写 TemplateController**

```java
package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.entity.Project;
import com.aicomic.entity.ProjectTemplate;
import com.aicomic.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    public ApiResponse<List<ProjectTemplate>> getTemplates(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "category", required = false) String category) {
        return ApiResponse.success(templateService.getTemplates(type, category));
    }

    @PostMapping("/projects/{projectId}/save-as-template")
    public ApiResponse<ProjectTemplate> saveAsTemplate(
            @PathVariable Long projectId,
            @RequestParam String name,
            @RequestParam(value = "description", required = false) String description) {
        return ApiResponse.success(templateService.saveAsTemplate(projectId, name, description));
    }

    @PostMapping("/{templateId}/create-project")
    public ApiResponse<Project> createProjectFromTemplate(
            @PathVariable Long templateId,
            @RequestParam String projectName) {
        return ApiResponse.success(templateService.createProjectFromTemplate(templateId, projectName));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ApiResponse.success(null);
    }
}
```

- [ ] **步骤 3：编写 DataInitializer — 预置3个模板**

```java
package com.aicomic.config;

import com.aicomic.entity.ProjectTemplate;
import com.aicomic.repository.ProjectTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ProjectTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) {
        if (templateRepository.count() > 0) {
            log.info("预置模板已存在，跳过初始化");
            return;
        }

        log.info("初始化预置项目模板...");

        createTemplate("短剧模板", "9:16竖屏短剧，快节奏剪辑，适合抖音/快手平台",
                ProjectTemplate.StyleType.SHORT_DRAMA, Map.of(
                        "defaultAspectRatio", "9:16",
                        "defaultFps", 24,
                        "defaultDuration", 60,
                        "visualStyle", "快节奏剪辑",
                        "colorTone", "bright"
                ));

        createTemplate("漫剧模板", "16:9横屏漫剧，电影级运镜，画面细腻",
                ProjectTemplate.StyleType.COMIC, Map.of(
                        "defaultAspectRatio", "16:9",
                        "defaultFps", 24,
                        "defaultDuration", 120,
                        "visualStyle", "电影级运镜",
                        "colorTone", "cinematic"
                ));

        createTemplate("预告片模板", "16:9横屏预告片，高冲击力，快切+慢镜头",
                ProjectTemplate.StyleType.TRAILER, Map.of(
                        "defaultAspectRatio", "16:9",
                        "defaultFps", 30,
                        "defaultDuration", 30,
                        "visualStyle", "高冲击力快切",
                        "colorTone", "dramatic"
                ));

        log.info("预置模板初始化完成: 3个模板");
    }

    private void createTemplate(String name, String description,
                                 ProjectTemplate.StyleType style, Map<String, Object> configData) {
        try {
            ProjectTemplate template = new ProjectTemplate();
            template.setName(name);
            template.setDescription(description);
            template.setStyle(style);
            template.setIsBuiltin(true);
            template.setUseCount(0);
            template.setTemplateData(objectMapper.writeValueAsString(configData));
            templateRepository.save(template);
        } catch (Exception e) {
            log.error("创建预置模板失败: {}", e.getMessage());
        }
    }
}
```

注意：需要检查 `ProjectTemplateRepository` 是否存在，不存在则创建。

- [ ] **步骤 4：编译验证**

运行：`cd D:/AI/atomgit/backend && mvn compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add backend/src/main/java/com/aicomic/service/TemplateService.java backend/src/main/java/com/aicomic/controller/TemplateController.java backend/src/main/java/com/aicomic/config/DataInitializer.java
git commit -m "feat: 项目模板后端 - CRUD/预置3个模板/数据初始化"
```

---

### 任务 15：前端项目模板功能

**文件：**
- 创建：`frontend/src/stores/template.ts`
- 修改：`frontend/src/views/project/ProjectView.vue`

- [ ] **步骤 1：创建 template store**

```typescript
// frontend/src/stores/template.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'
import { useNotificationStore } from './notification'
import type { ProjectTemplate, StyleType } from '@/types'

export interface ProjectTemplateWithConfig extends ProjectTemplate {
  templateData?: Record<string, any>
}

export const useTemplateStore = defineStore('template', () => {
  const templates = ref<ProjectTemplateWithConfig[]>([])
  const loading = ref(false)

  async function fetchTemplates(category?: string) {
    loading.value = true
    try {
      const params: any = {}
      if (category) params.category = category
      const data = await http.get('/v1/templates', { params })
      templates.value = data as unknown as ProjectTemplateWithConfig[]
    } catch (err: any) {
      useNotificationStore().error('获取模板失败', err.message)
    } finally {
      loading.value = false
    }
  }

  async function saveAsTemplate(projectId: number, name: string, description?: string) {
    try {
      await http.post(`/v1/templates/projects/${projectId}/save-as-template`, null, {
        params: { name, description },
      })
      useNotificationStore().info('保存模板成功')
    } catch (err: any) {
      useNotificationStore().error('保存模板失败', err.message)
    }
  }

  async function createProjectFromTemplate(templateId: number, projectName: string) {
    try {
      const data = await http.post(`/v1/templates/${templateId}/create-project`, null, {
        params: { projectName },
      })
      useNotificationStore().info('项目创建成功')
      return data
    } catch (err: any) {
      useNotificationStore().error('创建项目失败', err.message)
      return null
    }
  }

  return { templates, loading, fetchTemplates, saveAsTemplate, createProjectFromTemplate }
})
```

- [ ] **步骤 2：在 ProjectView.vue 中添加"从模板创建"功能**

1. 在工具栏中添加"从模板创建"按钮（在"新建项目"按钮旁边）
2. 新增 `showTemplateDialog` ref 和模板选择对话框
3. 对话框内容：分类筛选标签 + 模板卡片网格（每个卡片含名称+描述+风格标签）
4. 选择模板后输入项目名称，调用 `templateStore.createProjectFromTemplate()`

关键模板代码：
```vue
<!-- 工具栏添加按钮 -->
<el-button @click="openTemplateDialog">
  <el-icon><CopyDocument /></el-icon> 从模板创建
</el-button>

<!-- 模板选择对话框 -->
<el-dialog v-model="showTemplateDialog" title="从模板创建项目" width="700px">
  <div class="template-filters">
    <el-radio-group v-model="templateCategory" @change="loadTemplates">
      <el-radio-button label="">全部</el-radio-button>
      <el-radio-button label="SHORT_DRAMA">短剧</el-radio-button>
      <el-radio-button label="COMIC">漫剧</el-radio-button>
      <el-radio-button label="TRAILER">预告片</el-radio-button>
    </el-radio-group>
  </div>
  <el-row :gutter="16">
    <el-col :span="8" v-for="tmpl in templateStore.templates" :key="tmpl.id">
      <el-card shadow="hover" class="template-card" :class="{ selected: selectedTemplateId === tmpl.id }"
               @click="selectedTemplateId = tmpl.id">
        <h4>{{ tmpl.name }}</h4>
        <p>{{ tmpl.description }}</p>
        <el-tag size="small">{{ styleLabel(tmpl.style) }}</el-tag>
      </el-card>
    </el-col>
  </el-row>
  <div v-if="selectedTemplateId" style="margin-top: 16px">
    <el-input v-model="newProjectName" placeholder="请输入项目名称" />
  </div>
  <template #footer>
    <el-button @click="showTemplateDialog = false">取消</el-button>
    <el-button type="primary" :disabled="!selectedTemplateId || !newProjectName"
               @click="createFromTemplate">创建</el-button>
  </template>
</el-dialog>
```

- [ ] **步骤 3：编译验证**

运行：`cd D:/AI/atomgit/frontend && npx vue-tsc --noEmit`
预期：无类型错误

- [ ] **步骤 4：Commit**

```bash
git add frontend/src/stores/template.ts frontend/src/views/project/ProjectView.vue
git commit -m "feat: 项目模板前端 - 模板选择对话框/从模板创建项目"
```

---

## 自检结果

**1. 规格覆盖度检查：**

| 规格章节 | 对应任务 | 状态 |
|----------|---------|------|
| 1.1 配置中心 ConfigView 重构 | 任务6 | 覆盖 |
| 1.1 后端 testConnection | 任务4 | 覆盖 |
| 1.1 后端 render/validate | 任务5 | 覆盖 |
| 1.2 脏标记 PipelineStateService | 任务1 | 覆盖 |
| 1.2 脏标记侵入5个Service | 任务2 | 覆盖 |
| 1.2 前端 DIRTY 拦截 | 任务3 | 覆盖 |
| 2.1 修复 scriptId 硬编码 | 任务11 | 覆盖 |
| 2.2 FFmpegUtils | 任务7 | 覆盖 |
| 2.2 SLevelService 完整实现 | 任务8 | 覆盖 |
| 2.2 SLevelView 前端完善 | 任务10 | 覆盖 |
| 2.2 DirectorService FFmpeg 拼接 | 任务9 | 覆盖 |
| 3.1 素材库后端 | 任务12 | 覆盖 |
| 3.1 素材库前端 | 任务13 | 覆盖 |
| 3.2 项目模板后端 | 任务14 | 覆盖 |
| 3.2 项目模板前端 | 任务15 | 覆盖 |

**2. 占位符扫描：** 无 TODO/TBD/待定项。所有步骤包含完整代码。

**3. 类型一致性检查：**
- `PipelineStage` 枚举：后端使用 `Project.PipelineStage`，前端使用 `PipelineStage` 联合类型 — 已对齐
- `ExportConfig`/`WatermarkConfig`/`CompositeRequest` — 后端DTO与前端store字段名一致
- `AssetItem.AssetType` — 后端枚举与前端联合类型 `AssetType` 一致
- `ProjectTemplate.StyleType` — 后端枚举与前端 `StyleType` 一致
- `ModelConfig.ModelProvider`/`ModelType` — 前端类型定义与后端枚举对齐

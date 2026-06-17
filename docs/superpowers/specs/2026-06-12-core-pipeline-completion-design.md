# AI漫剧平台核心流水线补全 — 开发设计规格

> 日期：2026-06-12
> 状态：已批准
> 范围：补全六大模块流水线，使剧本→角色→场景→分镜→导演→S级全链路可运行

## 背景

项目已有基础骨架（阶段1完成），6大模块前端视图和后端Service均有实质性业务功能，但存在以下缺口：
- S级模块全部TODO，无FFmpeg调用
- 配置中心仅有更新检测，缺模型配置/提示词模板管理
- 流水线脏标记机制未实现
- 素材库和项目模板未实现
- CharacterView/SceneView的scriptId硬编码等细节问题

## 开发策略：分层推进

按依赖关系分3层，每层完成后可独立验证：

| 层次 | 内容 | 依赖 |
|------|------|------|
| L1 基础层 | 配置中心 + 脏标记机制 | 无 |
| L2 流水线层 | 修复现有TODO + S级FFmpeg | L1配置中心 |
| L3 配套层 | 素材库 + 项目模板 | L1+L2 |

---

## L1 基础层

### 1.1 配置中心（ConfigView重构）

**现状**：ConfigView仅含Electron更新检测和版本展示，底部标注"更多设置项即将上线"。

**目标**：实现模型配置CRUD + 提示词模板管理 + API Key测试连接，让现有AI生成功能可配置化。

#### 前端：ConfigView重构为Tab分组

6个Tab页签：

**Tab 1: 文本模型**
- 表单字段：供应商（下拉选择）、API Key（密码输入框）、Base URL、模型名、是否启用
- 供应商选项：OpenAI、通义千问、文心一言、扣子工作流
- 扣子工作流展开额外字段：workflow_id、input_mapping（JSON编辑器）、output_field、bot_id、app_id

**Tab 2: 生图模型**
- 同文本模型表单结构，供应商选项：DALL-E、Stable Diffusion WebUI、扣子工作流

**Tab 3: 视频模型**
- 同文本模型表单结构，供应商选项：Seedance 2.0、SKYREELS-V4、扣子工作流
- 视频模型特定配置：最大时长、支持的首帧图数量

**Tab 4: 音频模型**
- 同文本模型表单结构，供应商选项：OpenAI TTS、通义语音、火山引擎、扣子工作流

**Tab 5: 提示词模板**
- 分类列表（SCRIPT_GENERATION/STORYBOARD_GENERATION/CHARACTER_GENERATION/ASSET_EXTRACTION/NOVEL_CONVERSION/SCENE_EXTRACTION）
- 每个模板卡片：名称、分类标签、变量列表、内容预览
- 编辑对话框：名称、分类、内容（代码编辑器，支持变量高亮）、变量定义（JSON编辑器）、输出格式
- "预览渲染"按钮 — 调用render端点，填入示例变量值查看渲染结果

**Tab 6: 应用设置**
- 存储路径（只读展示+修改按钮）
- 主题切换（light/dark）
- 自动备份开关+间隔
- FFmpeg路径配置
- 检查更新按钮（原功能迁移）

**通用交互**：
- 每个模型配置为一个卡片，支持新增/编辑/删除/启用禁用切换
- "测试连接"按钮 — 调用后端测试API，显示成功/失败+响应时间
- API Key输入框带"显示/隐藏"切换
- 表单校验：必填字段、URL格式、JSON格式

#### 后端：补充配置中心API

**ModelConfigController**（已有骨架，需补充）：
- `POST /api/v1/model-configs/{id}/test-connection` — 实现测试连接逻辑
  - 根据模型类型调用对应Provider的健康检查
  - 文本模型：发送简单prompt测试
  - 生图模型：生成一张小图测试
  - 扣子工作流：调用工作流查询API验证workflow_id有效性
  - 返回：`{ "success": true, "responseTime": 1234, "message": "连接成功" }`

**PromptTemplateController**（已有骨架，需完善）：
- `POST /api/v1/prompt-templates/{id}/render` — 完善变量替换渲染
  - 接收变量值Map，替换模板中的 `{varName}` 占位符
  - 校验所有必需变量都已提供
- `POST /api/v1/prompt-templates/{id}/validate` — 校验变量完整性
  - 解析模板中所有 `{varName}` 变量引用
  - 与变量定义对比，返回缺失/多余的变量

**ModelConfigService**（已有，需补充）：
- `testConnection(Long configId)` — 测试指定模型配置的连通性

**PromptTemplateService**（已有，需完善）：
- `renderTemplate(Long templateId, Map<String, String> variables)` — 渲染模板
- `validateTemplate(Long templateId)` — 校验模板变量完整性

### 1.2 脏标记机制（pipeline_state）

**现状**：PipelineState实体已存在（currentStage, stageStatus, dirtyFlags），但无业务逻辑。

**目标**：实现ADR-20定义的脏标记传播和用户确认机制。

#### 后端实现

**新建 `PipelineStateService`**：

```java
@Service
public class PipelineStateService {

    // 获取项目的流水线状态
    PipelineState getPipelineState(Long projectId);

    // 标记下游阶段为DIRTY
    void markDirty(Long projectId, PipelineStage sourceStage);

    // 清除指定阶段的DIRTY标记（用户选择"保持现状"）
    void clearDirtyFlag(Long projectId, PipelineStage stage);

    // 推进到下一阶段（带脏标记检查）
    // 返回是否有DIRTY阶段需要处理
    AdvanceResult advance(Long projectId, PipelineStage targetStage);

    // 重新执行指定阶段（用户选择"重新执行"）
    void reExecute(Long projectId, PipelineStage stage);
}
```

**脏标记传播规则**：

| 修改源 | 标记DIRTY的阶段 |
|--------|----------------|
| SCRIPT | CHARACTER, SCENE, SHOT, DIRECTOR, S_LEVEL |
| CHARACTER | SHOT, DIRECTOR, S_LEVEL |
| SCENE | SHOT, DIRECTOR, S_LEVEL |
| SHOT | DIRECTOR, S_LEVEL |
| DIRECTOR | S_LEVEL |

**侵入点**：在以下Service的修改方法中调用 `pipelineStateService.markDirty()`：
- ScriptService.updateScript()
- CharacterService.updateCharacter() / confirmAsset()
- SceneService.updateScene() / confirmAsset()
- StoryboardService.updateStoryboard() / batchUpdateStoryboards()
- DirectorService（视频生成完成后的状态更新）

**新建 `PipelineStateController`**：
- `GET /api/v1/projects/{id}/pipeline-state` — 获取流水线状态
- `POST /api/v1/pipeline-states/{id}/advance` — 推进阶段
- `POST /api/v1/pipeline-states/{id}/re-execute` — 重新执行
- `POST /api/v1/pipeline-states/{id}/clear-dirty` — 清除DIRTY标记

**PipelineState实体补充**：
- 新增 `PipelineStage` 枚举：SCRIPT, CHARACTER, SCENE, SHOT, DIRECTOR, S_LEVEL
- `stageStatus` JSON结构：`{"SCRIPT": "COMPLETED", "CHARACTER": "IN_PROGRESS", ...}`
- `dirtyFlags` JSON结构：`{"SHOT": true, "DIRECTOR": true, "S_LEVEL": true}`

#### 前端实现

**MainLayout.vue 模块切换拦截**：
- 用户点击侧边栏切换模块时，先调用 `GET /api/v1/projects/{id}/pipeline-state`
- 检查目标模块是否有DIRTY标记
- 如果有DIRTY，弹出 `ElMessageBox.confirm` 对话框："上游内容已变更，是否重新执行？"
  - 选择"重新执行" → 调用 `POST /api/v1/pipeline-states/{id}/re-execute`，触发重新生成
  - 选择"保持现状" → 调用 `POST /api/v1/pipeline-states/{id}/clear-dirty`，清除标记并进入模块
- 如果无DIRTY，直接进入模块

**侧边栏视觉指示**：
- 有DIRTY标记的模块显示橙色圆点/徽章
- 已完成模块显示绿色对勾
- 当前模块显示蓝色高亮

---

## L2 流水线层

### 2.1 修复现有模块TODO

| 问题 | 修复方案 |
|------|----------|
| CharacterView/SceneView AI提取时scriptId硬编码为1 | 从路由参数 `route.params.scriptId` 或当前项目最新剧本获取scriptId |
| DirectorService FFmpeg拼接为TODO | 在L2.2中与S级一起实现 |

### 2.2 S级模块完整FFmpeg实现

**现状**：SLevelService三个方法（compositeFinalVideoAsync/exportVideoAsync/addWatermarkAsync）全部TODO，无任何FFmpeg调用。

**目标**：实现完整FFmpeg功能链：拼接+字幕+音频混合+转场+滤镜+转码+水印。

#### 后端：新建FFmpegUtils工具类

```java
@Component
public class FFmpegUtils {

    // FFmpeg二进制路径（从app_config读取或系统PATH）
    private String ffmpegPath;

    // 拼接多个视频（concat demuxer）
    Path concatVideos(List<String> videoUrls, String outputPath);

    // 烧录SRT字幕
    Path addSubtitles(String videoPath, String srtPath, String outputPath);

    // 混合多音轨（BGM+音效+配音，各自音量可调）
    Path mixAudio(String videoPath, List<AudioTrackConfig> audioTracks, String outputPath);

    // 添加转场效果
    // transitionType: fade(淡入淡出), slideleft(左滑), slideright(右滑), slideup(上滑), zoom(缩放)
    Path addTransition(String inputPath, String transitionType, double duration, String outputPath);

    // 格式转码（格式/分辨率/码率/帧率）
    Path transcode(String inputPath, ExportConfig config, String outputPath);

    // 叠加水印（文字或图片，位置/透明度可配）
    Path addWatermark(String inputPath, WatermarkConfig config, String outputPath);

    // 获取视频元信息（时长/分辨率/编码等）
    VideoInfo getVideoInfo(String videoPath);

    // 统一执行方法（含超时控制和错误处理）
    FFmpegResult execute(List<String> command);
}
```

**ExportConfig**：
```java
public class ExportConfig {
    private String format;      // mp4/mov/avi
    private String resolution;  // 720p/1080p/4K
    private Integer bitrate;    // kbps
    private Integer fps;        // 24/30/60
}
```

**WatermarkConfig**：
```java
public class WatermarkConfig {
    private String type;        // TEXT/IMAGE
    private String content;     // 文字内容或图片路径
    private String position;    // TOP_LEFT/TOP_RIGHT/BOTTOM_LEFT/BOTTOM_RIGHT/CENTER
    private Double opacity;     // 0.0-1.0
    private Integer fontSize;   // 文字水印字号
    private String fontColor;   // 文字水印颜色
}
```

**FFmpeg二进制路径管理**：
- 启动时检测系统PATH中是否有ffmpeg命令
- 通过`app_config`表的`ffmpeg_path`配置项可自定义
- Electron打包时将ffmpeg二进制放入应用目录（Windows: ffmpeg.exe）
- 路径检测逻辑：`app_config.ffmpeg_path` → 系统 PATH → 应用目录/ffmpeg

#### 后端：SLevelService完整实现

**compositeFinalVideoAsync(episodeId)**：
1. 查询该集所有分镜，按sequence排序
2. 收集所有已生成视频的分镜的 `generated_video_url`
3. 下载视频到临时目录（如果为URL）
4. FFmpeg concat demuxer 拼接视频 → intermediate_concat.mp4
5. 查询字幕数据，生成SRT文件 → FFmpeg烧录字幕 → intermediate_subtitle.mp4
6. 查询音频数据（BGM+音效+配音）→ FFmpeg音频混合 → intermediate_audio.mp4
7. 添加转场效果（如果配置了转场）→ intermediate_transition.mp4
8. 每个步骤完成后SSE推送进度
9. 移动最终文件到项目output目录
10. 更新数据库，返回最终视频URL

**exportVideoAsync(episodeId, exportConfig)**：
1. 读取已合成的视频
2. FFmpeg转码为目标格式和参数
3. 输出到项目output目录

**addWatermarkAsync(videoUrl, watermarkConfig)**：
1. FFmpeg overlay滤镜叠加水印
2. 输出到临时文件后替换原文件

#### 前端：SLevelView完善

**成片合成区**：
- 步骤条：拼接 → 字幕 → 音频混合 → 转场 → 完成
- 每个步骤显示实时进度（SSE订阅）
- "开始合成"按钮，点击后调用 `POST /api/v1/episodes/{id}/compose`
- 合成完成后显示视频播放器

**视频播放器**：
- 使用HTML5 `<video>` 标签
- 支持播放/暂停、进度条、音量控制
- 显示视频时长和分辨率

**导出对话框**（已有骨架，需对接）：
- 格式选择：MP4/MOV/AVI
- 分辨率选择：720p/1080p/4K
- 码率输入框
- 帧率选择：24/30/60
- "导出"按钮调用 `POST /api/v1/episodes/{id}/export`

**水印管理**（已有骨架，需对接）：
- 类型切换：文字水印/图片水印
- 文字水印：内容、字号、颜色、位置
- 图片水印：上传图片、位置、透明度滑块
- "应用水印"按钮调用 `POST /api/v1/episodes/{id}/watermark`

#### 后端：DirectorService FFmpeg拼接补充

**stitchVideosAsync(episodeId)**（回退方案）：
- 收集该集所有分镜视频URL
- FFmpeg concat拼接为整集视频
- 拼接进度通过SSE推送

---

## L3 配套层

### 3.1 素材库

**现状**：AssetItem实体已有，AssetItemRepository已有，无Controller和前端页面。

#### 后端：新建AssetController + AssetService

**AssetService**：
```java
@Service
public class AssetService {
    // 上传素材文件（multipart）
    AssetItem uploadAsset(Long projectId, MultipartFile file, String tags);

    // 搜索素材（按项目ID、类型、标签筛选）
    Page<AssetItem> searchAssets(Long projectId, String type, String tags, Pageable pageable);

    // 更新素材元数据
    AssetItem updateAsset(Long id, String name, String tags);

    // 删除素材（同时删除文件）
    void deleteAsset(Long id);
}
```

**AssetController**：
- `POST /api/v1/projects/{id}/assets/upload` — multipart文件上传
- `GET /api/v1/assets?projectId={id}&type=IMAGE&tags=xxx` — 搜索筛选
- `PUT /api/v1/assets/{id}` — 更新素材元数据
- `DELETE /api/v1/assets/{id}` — 删除素材

**文件存储**：
- 上传文件保存到 `projects/{projectId}/assets/{type}/` 目录
- 文件命名：`{uuid}.{ext}`
- 支持格式：jpg/png/webp/gif/mp4/mov/mp3/wav/aac
- 单文件大小限制：100MB（可配置）

#### 前端：新建AssetView.vue

**布局**：
- 顶部：搜索框 + 类型筛选下拉（全部/图片/音频/视频）+ 标签筛选
- 工具栏：上传按钮（支持多选）+ 批量删除
- 主区域：网格布局展示素材缩略图
  - 图片素材：缩略图预览
  - 音频素材：波形图标 + 时长
  - 视频素材：视频首帧缩略图 + 时长
- 点击素材：侧边详情面板（大图/播放器 + 元数据编辑 + 标签管理）
- 拖拽上传：整个区域支持拖拽文件上传

**路由**：
- 路径：`/assets`
- 侧边栏添加素材库图标入口

**与分镜编辑器联动**：
- 素材库中的图片可拖拽到分镜编辑器作为参考图
- 后续可扩展（非本轮范围）

### 3.2 项目模板

**现状**：ProjectTemplate实体已有，无Controller和前端页面。

#### 后端：新建TemplateController + TemplateService

**TemplateService**：
```java
@Service
public class TemplateService {
    // 获取模板列表（按类型和分类筛选）
    List<ProjectTemplate> getTemplates(String type, String category);

    // 保存项目为模板
    ProjectTemplate saveAsTemplate(Long projectId, String name, String category);

    // 从模板创建项目
    Project createProjectFromTemplate(Long templateId, String projectName);

    // 更新模板
    ProjectTemplate updateTemplate(Long id, String name, String category, String configData);

    // 删除模板
    void deleteTemplate(Long id);
}
```

**模板configData结构**：
```json
{
  "defaultStyle": "国风动漫",
  "defaultAspectRatio": "9:16",
  "defaultFps": 24,
  "exportFormat": "mp4",
  "exportResolution": "1080p",
  "promptTemplateIds": [1, 3, 5],
  "stylePresets": {
    "visualStyle": "2D",
    "colorTone": "warm",
    "lightAtmosphere": "soft"
  }
}
```

**预置模板**（应用启动时自动初始化）：

1. **短剧模板**
   - 类型：9:16竖屏、每集60秒、24fps
   - 风格：快节奏剪辑、对白密集
   - 分类："短剧"

2. **漫剧模板**
   - 类型：16:9横屏、每集120秒、24fps
   - 风格：电影级运镜、画面细腻
   - 分类："漫剧"

3. **预告片模板**
   - 类型：16:9横屏、30秒、30fps
   - 风格：高冲击力、快切+慢镜头
   - 分类："预告片"

**TemplateController**：
- `GET /api/v1/templates?type=PROJECT` — 模板列表
- `POST /api/v1/projects/{id}/save-as-template` — 保存为模板
- `POST /api/v1/templates/{id}/create-project` — 从模板创建
- `PUT /api/v1/templates/{id}` — 更新模板
- `DELETE /api/v1/templates/{id}` — 删除模板

#### 前端：项目模板功能

**项目列表页（ProjectView.vue）增强**：
- 新增"从模板创建"按钮（在"新建项目"按钮旁边）
- 点击后弹出模板选择对话框
- 对话框内容：卡片网格展示模板，每个卡片含缩略图+名称+描述+分类
- 选择模板后，自动填充项目默认参数，用户可修改项目名称后创建

**模板选择对话框**：
- 标签筛选：全部/短剧/漫剧/预告片
- 卡片点击选择 → "使用此模板"按钮
- 每个卡片右上角有收藏星标（isFavorite）

---

## 不在本轮范围内的功能

以下功能按YAGNI原则暂不实现，留待后续迭代：

- 多API Key轮询
- 版本历史（差异对比、回滚）
- 备份恢复
- 音频处理（AI配音、音频库、音频编辑）
- 深色模式
- 新手引导
- 搜索和筛选（全局搜索）
- 诊断工具
- 撤销/重做
- 草稿自动保存
- 项目导出/导入包
- 分镜导出为PDF/Excel
- 视频分享到抖音/B站
- 视频滤镜预设
- 自定义标签系统
- 操作日志审计
- 数据统计和分析

---

## 文件变更预估

### L1 基础层

| 层 | 文件 | 操作 |
|----|------|------|
| 前端 | ConfigView.vue | 重构（Tab分组布局） |
| 前端 | zh-CN.ts | 补充配置中心i18n文案 |
| 后端 | ModelConfigService.java | 补充testConnection方法 |
| 后端 | PromptTemplateService.java | 完善render/validate方法 |
| 后端 | PipelineStateService.java | 新建 |
| 后端 | PipelineStateController.java | 新建 |
| 后端 | PipelineStage.java | 新建（枚举） |
| 后端 | ScriptService.java | 侵入markDirty调用 |
| 后端 | CharacterService.java | 侵入markDirty调用 |
| 后端 | SceneService.java | 侵入markDirty调用 |
| 后端 | StoryboardService.java | 侵入markDirty调用 |
| 后端 | DirectorService.java | 侵入markDirty调用 |
| 前端 | MainLayout.vue | 模块切换DIRTY拦截 |

### L2 流水线层

| 层 | 文件 | 操作 |
|----|------|------|
| 后端 | FFmpegUtils.java | 新建 |
| 后端 | ExportConfig.java | 新建（DTO） |
| 后端 | WatermarkConfig.java | 新建（DTO） |
| 后端 | AudioTrackConfig.java | 新建（DTO） |
| 后端 | VideoInfo.java | 新建（DTO） |
| 后端 | SLevelService.java | 重写（实现FFmpeg调用） |
| 后端 | DirectorService.java | 补充FFmpeg拼接方法 |
| 前端 | SLevelView.vue | 完善（进度/播放器/导出对接） |
| 前端 | CharacterView.vue | 修复scriptId硬编码 |
| 前端 | SceneView.vue | 修复scriptId硬编码 |

### L3 配套层

| 层 | 文件 | 操作 |
|----|------|------|
| 后端 | AssetService.java | 新建 |
| 后端 | AssetController.java | 新建 |
| 前端 | AssetView.vue | 新建 |
| 后端 | TemplateService.java | 新建 |
| 后端 | TemplateController.java | 新建 |
| 前端 | ProjectView.vue | 增强（从模板创建） |
| 前端 | zh-CN.ts | 补充素材库/模板i18n文案 |
| 后端 | DataInitializer.java | 新建（预置模板数据） |

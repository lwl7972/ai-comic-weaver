# AI 漫剧制作平台 - 系统设计文档

## 📌 品牌资源

> 项目统一使用 `assets/logo.png` 作为品牌标识。

| 资源项 | 文件路径 | 用途 | 规格 |
|--------|---------|------|------|
| 应用图标（Logo） | `assets/logo.png` | Electron 窗口图标、系统任务栏、应用安装包图标 | PNG, ~851KB |
| 启动页 / Splash | `assets/logo.png` | 应用启动时的全屏展示页 | 同上 |
| 关于页面 Logo | `assets/logo.png` | 关于/帮助页面品牌展示 | 同上 |
| 导航栏 Logo | `assets/logo.png` | 顶部导航栏左侧品牌标识 | 缩略显示 |
| favicon | `assets/logo.png` | Web 端（可选）浏览器标签图标 | 16×16 / 32×32 |

**设计元素**：赛博朋克风格机器人侧脸轮廓 + 胶片条环绕 + "AI漫剧" 品牌文字，蓝紫渐变配色，体现 AI + 影视创作定位。

---

## 〇、架构决策记录（ADR）

> 以下为项目关键架构决策，按编号排序。决策一旦做出即冻结，后续修改需追加新 ADR。

| ADR | 标题 | 决策 | 依据 |
|-----|------|------|------|
| ADR-1 | 技术栈选型 | Spring Boot + Vue 3 + Electron | 企业级稳定性、Java 生态丰富、前后端分离 |
| ADR-2 | 桌面端架构 | Electron 内嵌 JVM（spawn 子进程） | 复用 Spring Boot 后端，避免双套 API |
| ADR-3 | 用户模型 | 纯单用户桌面工具 | 简化架构，无认证/多租户，符合桌面端定位 |
| ADR-4 | 资产提取方式 | AI 自动提取 + 用户确认 | 剧本/角色完成后自动触发 LLM 提取，用户逐个确认入库 |
| ADR-5 | 扣子工作流字段结构化 | workflow_id/input_mapping/output_field/bot_id/app_id 独立字段 | 替代 JSON 存储，提升可维护性 |
| ADR-6 | 视频模型绑定 | 模型无关，抽象化接口 | 可插拔模型，默认模板兼容 Seedance 2.0 / SKYREELS-V4 |
| ADR-7 | 生成任务用途区分 | generation_purpose 字段 | 区分 CHARACTER_MAKEUP/SCENE_VIEW/SCENE_QUAD_VIEW/STORYBOARD_IMAGE/STORYBOARD_VIDEO |
| ADR-8 | 任务调度方案 | Spring @Async + ThreadPoolTaskExecutor | 轻量级，满足单用户桌面场景 |
| ADR-9 | 大文件小说处理 | 分章节摘要策略 | 超长文本先逐章生成摘要，再用摘要生成大纲和剧本 |
| ADR-10 | 角色一致性方案 | 参考图 + Prompt | 定妆图作为参考图，6层锚点描述拼入提示词，双重保证 |
| ADR-11 | 场景四视图 | 自动生成 | 场景确认后自动生成正面/背面/左侧/右侧四视图 |
| ADR-12 | 数据库选型 | SQLite | 单用户桌面工具，无需独立数据库服务，零配置 |
| ADR-13 | 视频合成策略 | 混合方案 | 优先一次生成整集视频，不支持时回退逐镜头+FFmpeg拼接 |
| ADR-14 | 异步任务通知 | SSE（Server-Sent Events） | 单向推送，比 WebSocket 轻量，Spring 原生支持 |
| ADR-15 | 扣子工作流调用模式 | 异步为主 | 长任务必须异步，提交后轮询查询结果 |
| ADR-16 | 首帧图来源 | 分镜生成图 | generated_image_url 直接作为视频生成首帧 |
| ADR-17 | 视频后期方案 | 后端 FFmpeg 为主，前端简单预览拼接 | 专业合成靠 FFmpeg，前端仅快速预览 |
| ADR-18 | JVM 启动方式 | spawn 子进程 + 随机端口 | Electron 主进程启动 java -jar，随机端口避免冲突 |
| ADR-19 | 分镜生成流程 | 三步流程：解析→编辑→生成 | 比"直接生成"更灵活，用户可修改结构化数据 |
| ADR-20 | 回退调整机制 | pipeline_state 脏标记 | 修改上游自动标记下游 DIRTY，切换时提示是否重新执行 |

---

## 一、项目概述

**参照魔因漫创（Moyin Creator）架构** 开发的 AI 漫剧制作平台，采用**六大模块流水线设计**（📝 剧本 → 🎭 角色 → 🌄 场景 → 🎬 分镜 → 🎥 导演 → ⭐ S级），覆盖从剧本到成片的完整创作链路，每一步产出自动流入下一步，实现全流程自动化。

### 核心目标
- **魔因漫创一致性**：保持六大模块流水线（与魔因漫创五面板对齐，拆分分镜/导演）、6层身份锚点、专业分镜系统等核心特性
- **快速生产**：支持短剧/漫剧/预告片批量化创作
- **多端支持**：桌面端 + Web 端
- **灵活模型配置**：支持通用大厂模型 + 扣子工作流集成
- **核心差异化**：提示词管理、小说导入一键转剧本、剧本自动提取资产

### 魔因漫创核心对标特性
✅ **六大模块流水线**：剧本 → 角色 → 场景 → 分镜 → 导演 → S级（与魔因漫创五面板对齐）<br>
✅ **6层身份锚点**：保证角色一致性<br>
✅ **角色圣经管理**：角色特征完整管理<br>
✅ **专业电影级分镜**：景别/机位/运镜参数<br>
✅ **Seedance 2.0 / SKYREELS-V4 多引擎支持**：动作+镜头+对白三层融合（模型无关化，ADR-6）<br>
✅ **工业级批量调度**：多API Key轮询、失败重试

## 二、技术栈

| 层级 | 技术选型 | 说明 |
|------|----------|------|
| **参考方案** | 魔因漫创：Electron 30 + React 18 + Node.js | 可选参考方案 |
| **我们的技术栈** | Spring Boot 3.x + Spring Data JPA + SQLite | 后端选择 |
| **前端** | Vue 3 + Element Plus + Vite | 前端选择 |
| **桌面端** | Electron（内嵌 JVM，spawn 子进程+随机端口，ADR-2/ADR-18） | 桌面端打包 |
| **实时通信** | SSE（Server-Sent Events，ADR-14） | 任务进度推送 |
| **视频合成** | FFmpeg（后端导出）+ 前端简单预览拼接（ADR-17） | 视频后期 |
| **文件存储** | 本地文件系统（桌面/Web简单版） / 对象存储（Web高配版可选） | 存储方案 |

### 魔因漫创技术对比
- 魔因漫创：Electron + React + Node.js（纯前端/Node.js 后端）
- 我们的方案：Electron + Vue + Spring Boot（Java 后端，更适合企业级）

两种方案可以共存，用户可根据需求选择。

## 三、总体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        用户界面层                              │
│  ┌──────────────┐        ┌──────────────┐                   │
│  │  Electron桌面 │        │   Web 浏览器  │（后续可选）        │
│  │ (内嵌JVM子进程)│       └──────────────┘                   │
│  └──────────────┘                                           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       前端应用层 (Vue 3)                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ 剧本编辑 │ │ 分镜编排 │ │ 素材库   │ │ 配置中心 │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
│  SSE 客户端 ← 接收生成任务进度推送                               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  后端服务层 (Spring Boot, 随机端口)             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    API 控制器层                         │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │           领域事件总线 (ApplicationEventPublisher)      │  │
│  │   剧本完成→角色提取→场景提取→分镜生成→视频生成→成片合成   │  │
│  │   pipeline_state 脏标记机制（ADR-20）                    │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ 剧本服务 │ │ 角色服务 │ │ 场景服务 │ │ 分镜服务 │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ 导演服务 │ │ S级服务  │ │ 素材服务 │ │ 配置服务 │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │         模型抽象层 (ModelProvider 接口, ADR-6)           │  │
│  │  ┌──────────────┐  ┌──────────────────────────────┐   │  │
│  │  │ 国产LLM适配器 │  │ 扣子工作流适配器(异步,ADR-15)  │   │  │
│  │  └──────────────┘  └──────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │     提示词引擎 (模板+变量替换+校验, 模型无关格式)        │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  任务调度引擎 (@Async+ThreadPoolTaskExecutor, ADR-8)   │  │
│  │  SSE 推送进度 (ADR-14) | 失败重试 | 断点恢复            │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  视频后期引擎 (FFmpeg 拼接+字幕+音频, ADR-17)           │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   SQLite     │    │  文件系统    │    │  外部 API    │
│   数据库     │    │  / MinIO     │    │  (扣子/大厂)  │
└──────────────┘    └──────────────┘    └──────────────┘
```

## 四、核心模块设计（六大模块流水线，参照魔因漫创）

### 4.1 📝 剧本板块
**核心功能**：
- **剧本编辑**：文本编辑器 + AI 续写（按集粒度）
- **小说导入**：支持 TXT、PDF、EPUB 等格式，一键转换为分集剧本
- **分章节摘要策略**（ADR-9）：大文件先对每章生成摘要，再用摘要生成大纲和剧本
- **剧本解析引擎**：分三步（ADR-19）：
  1. 解析为结构化数据（场景列表+对白列表+动作列表）
  2. 用户编辑结构化数据
  3. 填入提示词模板生成分镜
- **角色/场景/情绪/镜头语言自动识别**
- **多集/多幕剧本结构支持**（project → script → episode → storyboard 层级）
- **大纲生成**：根据故事概要自动生成完整剧集大纲

**小说→剧本转换流程**：
```
用户上传小说 → novel(UPLOADED)
  → AI 分章摘要（每章独立调用LLM生成摘要）
  → 用户确认/编辑摘要
  → AI 生成大纲（episodes 列表）
  → 用户确认/修改大纲
  → AI 逐集生成剧本
  → 创建 script + episodes
  → novel(PROCESSED)
```

**数据流向**：剧本产出 → 领域事件触发 → 自动流入角色板块

---

### 4.2 🎭 角色板块
**核心功能**：
- **6层身份锚点系统**（参照魔因漫创）：
  1. 基础身份（姓名、性别、年龄）
  2. 外貌特征（脸型、眼睛、发型）
  3. 服装系统（类型、详细描述）
  4. 性格特征（关键词 + 描述）
  5. AI定妆图（完整绘画提示词，用于生成角色图）
  6. 特征锚点（特殊标记、特征描述）
- **角色圣经管理**：集中管理所有角色的完整特征
- **参考图绑定**：支持上传角色参考图
- **角色一致性保证**（ADR-10）：参考图+Prompt 方案
  - 生成定妆图后，后续所有生成都附带角色参考图（makeup_image_url）
  - 同时将6层锚点描述拼入提示词，双重保证一致性
- **AI自动提取**（ADR-4）：剧本完成后自动触发LLM提取角色，用户逐个确认后入库

**数据流向**：角色产出 → 领域事件触发 → 自动流入场景板块

---

### 4.3 🌄 场景板块
**核心功能**：
- **多视角场景生成**：同一场景的不同视图（四视图策略）
- **四视图自动生成**（ADR-11）：场景确认后自动生成四视图（正面/背面/左侧/右侧），失败的视角可单独重试
- **场景描述 → 视觉提示词自动转换**
- **空间连贯性保障**：场景逻辑正确不穿模
- **静态初始状态提取**：用于文生图的场景设计
- **场景库管理**：常用场景复用
- **场景变体**：同一场景支持不同时间/氛围变体（如"白天卧室"vs"夜晚卧室"），通过 light_atmosphere 和 color_tone 区分
- **AI自动提取**（ADR-4）：角色完成后自动触发LLM提取场景，用户逐个确认后入库

**数据流向**：场景产出 → 领域事件触发 → 自动流入分镜板块

---

### 4.4 🎬 分镜板块（视觉参数+图片生成）
> 与魔因漫创对齐：魔因漫创五大面板为 Script → Role → Scene → **Shot** → **Director**，分镜(Shot)和导演(Director)独立。
> 本平台将原合并的"导演板块"拆分为 4.4 分镜板块 + 4.5 导演板块。

**核心功能**：
- **专业电影级分镜**：
  - 景别设置（远景/全景/中景/近景/特写）
  - 机位设置（机位选择、高度、角度）
  - 运镜方式（15种基础运镜 + 10种特效运镜，见附录A.2）
  - 节奏控制（时长、剪辑节奏）
- **视觉风格一键切换**：2D / 3D / 写实 / 定格动画
- **分镜自动排版导出**：标准格式输出
- **承接上镜 / 场景信息管理**：
  - 承接上镜描述
  - 场景信息绑定
  - 运镜描述
  - 角色对话
  - 背景音效
- **AI 分镜生成（三步流程，ADR-19）**：
  1. **解析**：拿当前集剧本+涉及角色+涉及场景 → LLM 解析为结构化数据（场景列表+对白列表+动作列表）
  2. **编辑**：用户确认/修改结构化数据（调整场景切换点、修改对白、补充动作描述、标注情绪）
  3. **生成**：结构化数据+提示词模板+角色描述+场景描述 → 填入模板生成分镜
  - 支持对单个分镜重新生成
  - 自动解析 `---SHOT_SEPARATOR---` 分隔符
- **分镜图片生成**：
  - 根据分镜参数+角色描述+场景描述 → 生成每个分镜的参考图/生成图
  - 支持批量生成分镜图片
  - 生成结果写入 storyboard.generated_image_url

**数据流向**：分镜产出 → 领域事件触发 → 自动流入导演板块

---

### 4.5 🎥 导演板块（视频生成调度）
> 与魔因漫创对齐：Director 面板专注于视频生成调度，与分镜(Shot)面板职责分离。

**核心功能**：
- **视频生成调度**：
  - 分镜图片 → 视频生成任务调度
  - 多镜头合并叙事视频生成
  - 优先一次生成整集视频（需模型支持多图引用+长视频）
  - 模型不支持时回退逐镜头生成+FFmpeg拼接（ADR-13）
- **已验证兼容视频引擎**（参照魔因漫创 V0.2.8）：
  - Seedance 2.0：多模态引用(@图/@视频/@音频)，三层融合
  - SKYREELS-V4：统一音视频生成模型，可一次性生成带原生同步音频的1080P视频
- **多模态引用**（适配不同模型）：
  - 角色参考图（makeup_image_url）
  - 场景图（front_view_url 等）
  - 首帧图 = 分镜生成图（generated_image_url，ADR-16）
  - **首帧图网格拼接**（参照魔因漫创）：当多个分镜首帧需同时传入时，采用 N×N 网格拼接策略（如3个分镜首帧拼接为1×3网格图），减少多图引用数量，提高模型兼容性
- **智能提示词构建**：自动三层融合（动作 + 镜头语言 + 对白唇形同步）
- **模型参数自动校验**：根据模型配置校验参数限制（如 Seedance ≤9图+≤3视频+≤3音频，prompt≤5000字符）
- **扣子工作流集成**（异步模式，ADR-15）：通过扣子异步接口调用，轮询查询结果
- **模型特定格式适配**：提示词模板支持变量替换，不同模型可配置不同模板格式

**数据流向**：视频产出 → 领域事件触发 → 自动流入S级板块

---

### 4.6 ⭐ S级板块（成片输出与质量保障）
> 原编号4.5，因拆分分镜/导演板块而顺延为4.6。

**核心功能**：
- **成片合成**：
  - 多分镜视频按顺序拼接为完整集视频（FFmpeg，ADR-17）
  - 字幕叠加、音频混合、转场特效
- **质量校验**：
  - 视频连贯性检查（前后分镜衔接、角色一致性校验）
  - 音画同步检查
- **成片预览与导出**：
  - 整集视频预览播放
  - 多种格式导出（MP4/MOV/AVI，分辨率/码率/帧率可配）
  - 水印添加

**数据流向**：成片产出 → 用户预览 → 导出/分享

---

### 4.7 配套支持模块

#### 4.7.1 配置中心
- **模型配置**：文本/生图/视频/音频模型统一管理
- **API Key 轮询**：多个 API Key 负载均衡
- **扣子工作流配置**：工作流参数映射
- **品牌配置**：Logo（`assets/logo.png`）、应用名称（"AI漫剧"）、启动页图片
- **存储配置**：本地路径 / 对象存储

#### 4.7.2 提示词管理
- **提示词分类**：剧本/角色/场景/分镜/视频生成
- **预置提示词库**：完整 7 个核心提示词（见附录）
- **自定义提示词**：用户模板创建、编辑、变量定义
- **版本管理**：提示词历史版本

#### 4.7.3 素材库
- **图片/音频/视频素材管理**
- **标签分类**
- **素材预览与搜索**

#### 4.7.4 任务调度
- **工业级批量调度**（ADR-8：Spring @Async + ThreadPoolTaskExecutor）：
  - 多任务并行
  - 失败自动重试
  - 进度实时展示（ADR-14：SSE 推送）
  - 历史记录
- **生成队列管理**：
  - 任务优先级设置（高/中/低）
  - 队列暂停/继续
  - 任务取消和删除
  - 批量任务操作
  - 断点恢复（断网后继续生成）

#### 4.7.5 回退调整机制（ADR-20）
- **流水线脏标记**（pipeline_state 表）：
  - 用户修改任意阶段 → 下游阶段标记 DIRTY
  - 切换到下游阶段时提示"上游已变更，是否重新执行？"
  - 用户选择"重新执行"→ 重新生成受影响内容，下游继续标记 DIRTY
  - 用户选择"保持现状"→ 清除当前阶段 DIRTY 标记，下游保持 DIRTY
- **脏标记传播规则**：
  - 修改剧本 → CHARACTER:DIRTY, SCENE:DIRTY, SHOT:DIRTY, DIRECTOR:DIRTY, S_LEVEL:DIRTY
  - 修改角色 → SCENE:DIRTY(可选), SHOT:DIRTY, DIRECTOR:DIRTY, S_LEVEL:DIRTY
  - 修改场景 → SHOT:DIRTY, DIRECTOR:DIRTY, S_LEVEL:DIRTY
  - 修改分镜 → DIRECTOR:DIRTY, S_LEVEL:DIRTY
  - 修改视频 → S_LEVEL:DIRTY

---

### 4.8 版本控制和历史
- **剧本版本历史**：
  - 自动保存版本
  - 版本列表展示
  - 版本对比（差异高亮）
  - 一键回滚到任意版本
- **分镜版本历史**：
  - 单个分镜版本管理
  - 回滚和恢复
- **变更记录**：
  - 谁在什么时候修改了什么
  - 变更摘要

---

### 4.9 导出和分享
- **项目导出**：
  - 导出完整项目包（含所有文件和数据库）
  - 导入项目包
- **分镜导出**：
  - 导出分镜表为PDF
  - 导出分镜表为Excel
  - 导出分镜图片为ZIP包
- **视频导出设置**：
  - 导出格式选择（MP4/MOV/AVI等）
  - 分辨率选择（720p/1080p/4K/8K）
  - 码率设置
  - 帧率设置（24/30/60fps）
  - 视频比例（16:9/9:16/1:1等）
  - 水印添加功能（图片水印/文字水印）
- **视频分享**：
  - 直接发布到抖音、B站等平台（可选）
  - 生成分享链接
  - 本地导出

---

### 4.10 预览和播放
- **分镜预览**：
  - 生成分镜缩略图预览
  - 单分镜大图预览
- **视频预览**：
  - 应用内视频播放器
  - 进度条、暂停/播放、音量控制
- **连续播放预览**：
  - 多分镜按顺序连续播放
  - 模拟成片效果

---

### 4.11 批量编辑功能
- **分镜批量编辑**：
  - 统一修改属性（风格/时长等）
  - 分镜拖拽排序（支持多选）
  - 分镜批量复制/移动到其他集数
  - 台词批量编辑
- **角色批量替换**：
  - 一键替换所有分镜中的角色
- **场景批量替换**：
  - 一键替换所有分镜中的场景
- **批量操作确认**：
  - 操作前预览变更
  - 支持撤销

---

### 4.12 素材和资源管理
- **素材批量上传**：
  - 拖拽上传多个文件
  - 上传进度显示
- **分镜参考图批量上传**：
  - 按分镜顺序自动匹配
- **素材管理**：
  - 拖拽排序
  - 批量删除/移动
  - 素材重复检测
  - 素材元数据编辑（标签/描述）

---

### 4.13 项目模板和复用
- **保存为模板**：
  - 保存当前项目为模板
  - 模板命名和描述
  - 模板缩略图
- **从模板创建**：
  - 模板列表展示
  - 预览模板内容
  - 一键创建新项目
- **模板导入/导出**：
  - 分享模板给其他用户
- **风格一键应用**：
  - 风格预设（国风/日漫/写实）
  - 一键应用所有相关参数

---

### 4.14 项目设置
- **项目默认参数**：
  - 默认帧率/比例/风格
  - 默认提示词模板
- **项目级提示词**：
  - 项目特定的提示词配置
- **项目备注**：
  - 项目说明和备注

---

### 4.15 分镜编辑体验优化
- **分镜实时预览**：
  - 快速预览生成分镜
- **分镜对比查看**：
  - 左右对比两个分镜版本
- **分镜快速导航**：
  - 分镜缩略图快速切换
  - 分镜缩放查看
  - 分镜时间轴视图

---

### 4.16 音频同步
- **音频自动对齐**：
  - AI配音自动对齐分镜
  - 波形可视化
  - 音量关键帧调节
  - 音频淡入淡出

---

### 4.17 模板和预设
- **项目模板**：
  - 短剧模板
  - 漫剧模板
  - 预告片模板
  - 自定义模板
- **风格预设**：
  - 国风预设
  - 日漫预设
  - 写实预设
  - 一键应用所有相关参数
- **提示词模板库**：
  - 分类模板（角色/场景/分镜）
  - 一键插入变量
  - 用户自定义模板收藏

---

### 4.18 备份和恢复
- **自动备份**：
  - 定时自动备份（可配置时间间隔）
  - 保留最近N个备份
  - 备份到安装路径的 `backups/` 文件夹
- **手动备份**：
  - 一键备份当前项目
  - 备份命名和描述
- **恢复功能**：
  - 从备份恢复项目
  - 备份列表管理
  - 预览备份内容
- **云同步**（Web端可选）：
  - 可选云端备份
  - 跨设备同步

---

### 4.19 音频处理
- **AI配音**：
  - 角色配音选择（不同声音）
  - 情感调节（开心/悲伤/愤怒等）
  - 语速和音调调节
  - 自动匹配分镜台词
- **音频库**：
  - 内置背景音乐库
  - 内置音效库
  - 支持用户上传音频
- **简单音频编辑**：
  - 裁剪音频
  - 淡入淡出效果
  - 音量调节

---

### 4.20 视频后期
- **视频剪辑**：
  - 多分镜拼接
  - 裁剪视频
  - 调整顺序
- **字幕**：
  - 自动生成字幕（基于分镜台词）
  - 字幕样式自定义（字体、颜色、位置）
  - 字幕编辑
- **转场和滤镜**：
  - 常用转场效果（淡入淡出、滑动等）
  - 滤镜预设（复古、清新、电影感等）

---

### 4.21 用户体验优化
- **新手引导**：
  - 首次使用欢迎向导
  - 功能提示和教程
  - 示例项目
- **深色模式**：
  - 明暗主题切换
  - 跟随系统设置
- **最近项目**：
  - 快速打开最近编辑的项目
  - 项目列表按时间排序
- **收藏和标签**：
  - 项目/角色/场景/分镜收藏
  - 自定义标签分类
  - 快速访问收藏内容
- **搜索和筛选**：
  - 项目全局搜索
  - 素材按标签/类型筛选
  - 历史记录搜索

---

### 4.22 帮助和文档
- **内置帮助文档**：
  - 功能说明文档
  - 操作步骤教程
  - 视频教程链接
- **FAQ常见问题**：
  - 搜索常见问题
  - 问题分类浏览
- **版本更新日志**：
  - 新版本功能介绍
  - 问题修复说明

---

### 4.23 错误处理和日志
- **错误日志**：
  - 详细错误记录
  - 日志级别（DEBUG/INFO/WARN/ERROR）
  - 日志文件管理
- **生成失败处理**：
  - 失败原因详细分析
  - 智能重试建议
  - 错误解决方案提示
- **操作日志审计**：
  - 用户操作历史
  - 生成任务记录
  - 导出报告

---

### 4.24 数据统计和分析
- **项目统计**：
  - 项目总数、完成数
  - 总视频时长
  - 生成内容数量
- **使用情况分析**：
  - 常用功能排行
  - 模型使用统计
  - 生成成功率报告
- **资源使用统计**：
  - 存储空间使用
  - API调用次数
  - 生成费用估算

---

### 4.25 安全和隐私
- **API密钥加密存储**：
  - 密钥加密保存
  - 安全访问控制
- **敏感数据保护**：
  - 项目数据安全
  - 可选本地加密存储
- **数据导出和清理**：
  - 一键导出所有数据
  - 数据永久删除

---

### 4.26 自动保存和草稿
- **定时自动保存**：
  - 可配置保存间隔
  - 后台静默保存
- **异常恢复**：
  - 意外退出后恢复
  - 未保存内容找回
- **草稿功能**：
  - 自动保存草稿
  - 草稿对比和恢复

---

### 4.27 撤销/重做
- **编辑操作撤销/重做**：
  - 多步历史记录
  - 直观的历史栈展示
  - 分支历史管理

---

### 4.28 诊断工具
- **系统健康检查**：
  - 存储空间检查
  - 数据库完整性检查
  - 依赖项检查
- **问题诊断向导**：
  - 常见问题排查
  - 自动修复建议
- **清理工具**：
  - 清理临时文件
  - 清理缓存
  - 清理旧日志

## 五、数据模型

### 5.1 project（项目表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| name | String | 项目名称 |
| description | String | 项目描述 |
| cover_url | String | 封面URL |
| **项目设置** | | |
| default_fps | Integer | 默认帧率 |
| default_aspect_ratio | String | 默认视频比例 |
| default_style | String | 默认风格 |
| project_prompt | Text | 项目级提示词 |
| project_remark | Text | 项目备注 |
| **导出设置** | | |
| export_format | String | 默认导出格式 |
| export_resolution | String | 默认导出分辨率 |
| export_watermark | String | 水印配置(JSON) |
| created_at | Timestamp | 创建时间 |
| updated_at | Timestamp | 更新时间 |

### 5.2 character（角色表，含6层身份锚点）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| project_id | Long | 项目ID |
| name | String | 角色名称 |
| first_episode | Integer | 首次出现集数 |
| type | String | 类型(主角/配角/反派) |
| **第1层：基础身份** | | |
| gender | String | 性别(male/female) |
| age | String | 年龄或年龄段 |
| **第2层：外貌特征** | | |
| face_shape | String | 脸型(瓜子脸/鹅蛋脸/圆脸/方脸/清瘦/棱角分明) |
| eyes | String | 眼睛(大眼睛/丹凤眼/桃花眼/细长眼) |
| hair_style | String | 发型(长发/短发/卷发/白发/黑发) |
| appearance_desc | Text | 完整外貌描述 |
| **第3层：服装系统** | | |
| costume_type | String | 服装类型(古装/战甲/仙袍/现代装/铠甲) |
| costume_desc | Text | 服装详细描述(颜色+材质+款式+配饰) |
| **第4层：性格特征** | | |
| personality_keywords | String | 性格关键词(逗号分隔) |
| personality_desc | Text | 性格详细描述 |
| **第5层：AI定妆图** | | |
| makeup_image_desc | Text | AI定妆图描述(完整绘画提示词) |
| makeup_image_url | String | 定妆图URL |
| reference_image_url | String | 角色参考图URL |
| **第6层：特征锚点** | | |
| features | String | 特殊特征(逗号分隔数组) |
| anchor_points | Text | 特征锚点JSON配置(魔因漫创6层锚点) |
| **其他** | | |
| persona | String | 人设描述 |
| style_prompt | String | 风格提示词 |
| created_at | Timestamp | 创建时间 |

### 5.3 script（剧本表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| project_id | Long | 项目ID |
| title | String | 剧本标题 |
| content | Text | 剧本内容 |
| created_at | Timestamp | 创建时间 |
| updated_at | Timestamp | 更新时间 |

### 5.4 episode（集/幕表，新增）
> 修正：原设计缺少集级管理，分镜直接挂在剧本下，层级不完整。编号由5.3调整为5.4。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| script_id | Long | 剧本ID |
| episode_num | Integer | 集数编号 |
| title | String | 集标题 |
| summary | Text | 集摘要 |
| characters | String | 本集角色列表(JSON) |
| scenes | String | 本集场景列表(JSON) |
| props | String | 本集道具列表(JSON) |
| status | String | 状态(DRAFT/OUTLINED/SCRIPTED) |
| created_at | Timestamp | 创建时间 |
| updated_at | Timestamp | 更新时间 |

### 5.5 scene（场景表，多视角场景生成）
> 修正编号：原编号 5.3 与剧本表冲突，先改为 5.4，现因 episode 表插入再调整为 5.5
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| project_id | Long | 项目ID |
| name | String | 场景名称 |
| first_episode | Integer | 首次出现集数 |
| type | String | 类型(主要场景/次要场景) |
| scene_type | String | 场景类型(室内/室外/特殊空间) |
| era | String | 时代背景(古代/现代/未来/玄幻/仙侠) |
| description | Text | 场景描述 |
| **静态初始状态** | | |
| static_desc | Text | 静态初始状态描述(用于文生图) |
| film_type | String | 影片类型 |
| space_structure | String | 空间结构描述 |
| furniture_layout | String | 陈设布局描述 |
| texture_detail | String | 细节质感描述 |
| light_atmosphere | String | 光影氛围描述 |
| color_tone | String | 色彩基调描述 |
| **多视角图片** | | |
| front_view_url | String | 正面视角图URL |
| back_view_url | String | 背面视角图URL |
| left_view_url | String | 左侧视角图URL |
| right_view_url | String | 右侧视角图URL |
| created_at | Timestamp | 创建时间 |

### 5.6 item（道具表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| project_id | Long | 项目ID |
| name | String | 道具名称 |
| first_episode | Integer | 首次出现集数 |
| type | String | 类型(武器/法器/日常物品/特殊物品) |
| description | Text | 道具描述 |
| appearance | String | 外观描述 |
| function | String | 功能描述 |
| owner | String | 归属角色 |
| created_at | Timestamp | 创建时间 |

### 5.7 storyboard（分镜表，专业电影级分镜）
> 修正：增加 episode_id，移除单个 character_id（改用关联表），支持多角色

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| episode_id | Long | 集ID（关联 episode 表） |
| script_id | Long | 剧本ID |
| sequence | Integer | 序号 |
| **时间控制** | | |
| start_time | Integer | 开始时间(秒) |
| end_time | Integer | 结束时间(秒) |
| duration | Integer | 时长(秒) |
| **专业电影级分镜参数** | | |
| shot_type | String | 景别(远景/全景/中景/近景/特写/大特写) |
| camera_angle | String | 机位角度(平视/俯视/仰视/鸟瞰) |
| camera_height | String | 机位高度(高机位/平机位/低机位) |
| camera_movement | String | 运镜方式(见附录A.2的25种运镜) |
| camera_movement_desc | Text | 运镜详细描述 |
| **内容信息** | | |
| continuity | String | 承接上镜描述 |
| scene_info | String | 场景信息 |
| scene_desc | Text | 画面描述 |
| dialogue | Text | 角色对话 |
| scene_id | Long | 场景ID |
| bg_sound | Text | 背景音效 |
| emotion | String | 情绪/氛围 |
| **视觉风格** | | |
| visual_style | String | 视觉风格(2D/3D/写实/定格动画) |
| aspect_ratio | String | 画面比例(16:9/9:16/4:3等) |
| **生成结果** | | |
| reference_image_url | String | 参考图URL |
| generated_image_url | String | 生成图URL |
| generated_video_url | String | 生成视频URL |
| status | String | 状态(PENDING/GENERATING/SUCCESS/FAILED) |
| created_at | Timestamp | 创建时间 |

### 5.8 storyboard_character（分镜-角色关联表，新增）
> 修正：一个分镜可涉及多个角色，单字段无法表达

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| storyboard_id | Long | 分镜ID |
| character_id | Long | 角色ID |
| role_in_shot | String | 本镜中的角色(LEAD/SUPPORTING/BACKGROUND) |

### 5.9 pipeline_state（流水线状态表，新增）
> ADR-20：支持回退调整的脏标记机制

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| project_id | Long | 项目ID |
| current_stage | String | 当前阶段(SCRIPT/CHARACTER/SCENE/SHOT/DIRECTOR/S_LEVEL) |
| stage_status | Text | 阶段状态(JSON，记录每个阶段的完成状态) |
| dirty_flags | Text | 脏标记(JSON，记录哪些下游阶段需要重新执行) |
| created_at | Timestamp | 创建时间 |
| updated_at | Timestamp | 更新时间 |

### 5.10 model_config（模型配置表，结构化扣子字段）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| type | String | 类型(TEXT/IMAGE/VIDEO/AUDIO) |
| provider | String | 供应商名称 |
| api_key | String | API Key |
| base_url | String | API Base URL |
| model_name | String | 模型名称 |
| is_enabled | Boolean | 是否启用 |
| is_coze_workflow | Boolean | 是否为扣子工作流 |
| **扣子工作流字段（结构化，ADR-5）** | | |
| workflow_id | String | 扣子工作流ID |
| input_mapping | Text | 输入参数映射(JSON) |
| output_field | String | 输出字段名 |
| bot_id | String | 关联智能体ID(可选) |
| app_id | String | 应用ID(可选) |
| extra_config | JSON | 通用扩展配置 |
| created_at | Timestamp | 创建时间 |

### 5.11 asset（素材表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| project_id | Long | 项目ID |
| type | String | 类型(IMAGE/AUDIO/VIDEO) |
| name | String | 素材名称 |
| file_url | String | 文件URL |
| tags | String | 标签(逗号分隔) |
| created_at | Timestamp | 创建时间 |

### 5.12 generation_task（生成任务表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| project_id | Long | 项目 ID |
| target_type | String | 目标类型(STORYBOARD_IMAGE/STORYBOARD_VIDEO) |
| target_id | Long | 目标 ID |
| generation_purpose | String | 生成用途(CHARACTER_MAKEUP/SCENE_VIEW/SCENE_QUAD_VIEW/STORYBOARD_IMAGE/STORYBOARD_VIDEO)（新增，ADR-7） |
| config_id | Long | 模型配置 ID |
| priority | String | 优先级(HIGH/MEDIUM/LOW) |
| status | String | 状态(PENDING/RUNNING/SUCCESS/FAILED) |
| progress | Integer | 进度(0-100) |
| checkpoint_data | Text | 断点恢复数据(JSON) |
| error_msg | String | 错误信息 |
| result_url | String | 结果 URL |
| created_at | Timestamp | 创建时间 |
| started_at | Timestamp | 开始时间 |
| finished_at | Timestamp | 完成时间 |

### 5.13 prompt_template（提示词模板表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| name | String | 模板名称 |
| category | String | 分类(SCRIPT_GENERATION/STORYBOARD_GENERATION/CHARACTER_GENERATION/ASSET_EXTRACTION/...) |
| content | Text | 提示词内容（支持变量如 {seconds}、{style} 等） |
| variables | String | 变量定义（JSON 格式，包含变量名、类型、默认值、说明） |
| output_format | String | 期望输出格式说明 |
| is_preset | Boolean | 是否预置模板 |
| version | Integer | 版本号 |
| created_at | Timestamp | 创建时间 |
| updated_at | Timestamp | 更新时间 |

#### 变量定义 JSON 示例
```json
[
  {"name": "seconds", "type": "number", "default": "60", "description": "总时长（秒）"},
  {"name": "aspectRatio", "type": "string", "default": "16:9", "description": "画面比例"},
  {"name": "style", "type": "string", "default": "国风动漫", "description": "画面风格"}
]
```

### 5.14 novel（小说表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| project_id | Long | 项目 ID |
| name | String | 小说名称 |
| author | String | 作者 |
| file_url | String | 源文件 URL |
| file_format | String | 文件格式(TXT/PDF/EPUB/...) |
| raw_content | Text | 原始内容 |
| status | String | 状态(UPLOADED/SUMMARIZING/OUTLINING/PROCESSING/PROCESSED) |
| episode_count | Integer | 目标集数（新增，ADR-9） |
| convert_config | Text | 转换配置(JSON，风格、角色偏好等)（新增） |
| chapter_summaries | Text | 分章摘要结果(JSON，每章摘要数组)（新增，ADR-9） |
| created_at | Timestamp | 创建时间 |

### 5.15 extracted_asset（提取资产表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| project_id | Long | 项目 ID |
| novel_id | Long | 小说 ID（可选） |
| script_id | Long | 剧本 ID（可选） |
| asset_type | String | 资产类型(CHARACTER/SCENE/ITEM) |
| name | String | 资产名称 |
| description | Text | 资产描述 |
| is_confirmed | Boolean | 是否已确认 |
| created_at | Timestamp | 创建时间 |

### 5.16 app_config（应用配置表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| config_key | String | 配置键 |
| config_value | String | 配置值 |
| config_type | String | 配置类型(STRING/NUMBER/BOOLEAN/PATH) |
| description | String | 配置描述 |
| updated_at | Timestamp | 更新时间 |

**预置配置项**：
- `install_path`: 安装路径
- `projects_path`: 项目存储路径
- `default_output_format`: 默认输出格式
- `auto_backup`: 是否自动备份
- `backup_interval`: 自动备份间隔(分钟)
- `theme`: 主题(light/dark)
- `recent_projects`: 最近项目列表(JSON)

### 5.17 version_history（版本历史表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| project_id | Long | 项目ID |
| object_type | String | 对象类型(SCRIPT/STORYBOARD/CHARACTER/SCENE) |
| object_id | Long | 对象ID |
| version_number | Integer | 版本号 |
| content_snapshot | Text | 内容快照(JSON) |
| change_summary | String | 变更摘要 |
| created_at | Timestamp | 创建时间 |

### 5.18 template（模板表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| name | String | 模板名称 |
| type | String | 模板类型(PROJECT/STYLE/PROMPT) |
| category | String | 分类 |
| thumbnail_url | String | 缩略图URL |
| config_data | Text | 配置数据(JSON) |
| is_preset | Boolean | 是否预置 |
| is_favorite | Boolean | 是否收藏 |
| created_at | Timestamp | 创建时间 |

### 5.19 backup（备份表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| project_id | Long | 项目ID |
| name | String | 备份名称 |
| description | String | 备份描述 |
| file_path | String | 备份文件路径 |
| file_size | Long | 文件大小(字节) |
| is_auto | Boolean | 是否自动备份 |
| created_at | Timestamp | 创建时间 |

### 5.20 audio（音频表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| project_id | Long | 项目ID |
| name | String | 音频名称 |
| type | String | 音频类型(BGM/SFX/VOICE) |
| file_path | String | 文件路径 |
| duration | Integer | 时长(秒) |
| tags | String | 标签(逗号分隔) |
| is_preset | Boolean | 是否预置 |
| created_at | Timestamp | 创建时间 |

### 5.21 subtitle（字幕表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| storyboard_id | Long | 分镜ID |
| start_time | Integer | 开始时间(秒) |
| end_time | Integer | 结束时间(秒) |
| text | String | 字幕内容 |
| style_config | Text | 样式配置(JSON) |
| created_at | Timestamp | 创建时间 |

### 5.22 favorite（收藏表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| object_type | String | 对象类型(PROJECT/CHARACTER/SCENE/STORYBOARD/TEMPLATE) |
| object_id | Long | 对象ID |
| created_at | Timestamp | 创建时间 |

### 5.23 tag（标签表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| name | String | 标签名称 |
| color | String | 标签颜色 |
| created_at | Timestamp | 创建时间 |

### 5.24 tag_relation（标签关联表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| tag_id | Long | 标签ID |
| object_type | String | 对象类型 |
| object_id | Long | 对象ID |
| created_at | Timestamp | 创建时间 |

### 5.25 operation_log（操作日志表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| project_id | Long | 项目ID(可选) |
| operation_type | String | 操作类型 |
| operation_desc | String | 操作描述 |
| detail | Text | 详细信息(JSON) |
| ip_address | String | IP地址 |
| created_at | Timestamp | 创建时间 |

### 5.26 error_log（错误日志表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| level | String | 日志级别(DEBUG/INFO/WARN/ERROR) |
| message | String | 错误消息 |
| stack_trace | Text | 堆栈信息 |
| context | Text | 上下文信息(JSON) |
| created_at | Timestamp | 创建时间 |

### 5.27 draft（草稿表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| project_id | Long | 项目ID |
| object_type | String | 对象类型 |
| object_id | Long | 对象ID |
| content | Text | 草稿内容(JSON) |
| auto_saved | Boolean | 是否自动保存 |
| created_at | Timestamp | 创建时间 |
| updated_at | Timestamp | 更新时间 |

### 5.28 statistic（统计表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| stat_key | String | 统计键 |
| stat_value | Long | 统计值 |
| stat_date | String | 统计日期(YYYY-MM-DD) |
| extra_data | Text | 额外数据(JSON) |
| created_at | Timestamp | 创建时间 |

## 六、外部API集成

### 6.1 模型供应商抽象层
设计统一接口：
```java
public interface ModelProvider {
    String getProviderType();
    boolean isEnabled();
    Object generate(Object request);
}
```

### 6.2 扣子工作流集成

#### 6.2.1 扣子工作流 API 说明
- **同步接口地址**: `https://api.coze.cn/v1/workflow/run`
- **异步接口地址**: `https://api.coze.cn/v1/workflow/run` (async_mode=true)
- **异步查询地址**: `https://api.coze.cn/v1/workflows/runs/{run_id}`
- **请求方法**: POST
- **认证方式**: Bearer Token（在 Header 中传入 `Authorization: Bearer {access_token}`）
- **调用模式**（ADR-15）：异步为主，长任务必须异步

#### 6.2.2 请求参数
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| workflow_id | String | 是 | 工作流 ID |
| parameters | Object/String | 否 | 工作流输入参数（JSON 格式） |
| bot_id | String | 否 | 关联的智能体 ID（如需要） |
| app_id | String | 否 | 应用 ID（如需要） |

#### 6.2.3 响应格式
```json
{
  "code": 0,
  "msg": "Success",
  "data": "{\"output\":\"...\"}",
  "debug_url": "...",
  "cost": "0",
  "token": 98
}
```

#### 6.2.4 系统集成设计
- model_config 表已结构化扣子字段（ADR-5）：`workflow_id`、`input_mapping`、`output_field`、`bot_id`、`app_id`
- 通过 `is_coze_workflow` 字段标记是否为扣子工作流
- 执行流程（异步模式，ADR-15）：
  1. 根据 model_config 获取扣子配置
  2. 构建请求参数（根据 input_mapping 映射平台数据）
  3. 调用扣子工作流异步 API，获取 run_id
  4. 定时轮询查询结果（根据 output_field 字段提取结果）
  5. 通过 SSE 推送进度和结果到前端（ADR-14）
  6. 保存结果并更新任务状态

### 6.3 通用大厂模型
- 文本：OpenAI、Anthropic、通义千问、文心一言
- 生图：DALL-E、Midjourney API、Stable Diffusion WebUI
- 视频：Runway、Pika
- 音频：OpenAI TTS、通义语音、火山引擎

### 6.4 RESTful API 接口规范

#### 6.4.1 接口设计原则
- **RESTful 风格**：资源导向，HTTP 方法语义明确（GET/POST/PUT/DELETE）
- **统一响应格式**：所有接口返回统一 JSON 结构
- **版本管理**：URL 路径含版本号 `/api/v1/`
- **认证**：桌面端通过随机端口隔离，无需 token；Web 端可选 JWT（未来扩展）
- **错误码**：HTTP 状态码 + 业务错误码双层体系

#### 6.4.2 统一响应格式
```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1717812345678
}
```
| code | message | 说明 |
|------|---------|------|
| 0 | success | 成功 |
| 10001 | 参数校验失败 | 请求参数不合法 |
| 10002 | 未找到资源 | ID 不存在 |
| 10003 | 权限不足 | 无操作权限 |
| 20001 | 生成任务已存在 | 同类型任务重复提交 |
| 20002 | 生成任务执行失败 | 外部模型调用异常 |
| 20003 | 任务超时 | 生成超限 |
| 30001 | 流水线阶段冲突 | 上游未完成 |

#### 6.4.3 核心 API 端点清单

**项目管理**
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/projects` | 创建项目 |
| GET | `/api/v1/projects` | 项目列表 |
| GET | `/api/v1/projects/{id}` | 项目详情 |
| PUT | `/api/v1/projects/{id}` | 更新项目 |
| DELETE | `/api/v1/projects/{id}` | 删除项目 |

**剧本模块 (SCRIPT)**
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/projects/{projectId}/scripts` | 创建剧本 |
| GET | `/api/v1/scripts/{id}` | 获取剧本 |
| PUT | `/api/v1/scripts/{id}` | 更新剧本内容 |
| POST | `/api/v1/scripts/{id}/outline` | 生成大纲(A.5) |
| GET | `/api/v1/scripts/{id}/episodes` | 获取剧集列表 |
| POST | `/api/v1/scripts/{id}/episodes` | 创建剧集 |
| PUT | `/api/v1/episodes/{id}` | 更新剧集 |

**小说导入 (NOVEL)**
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/projects/{projectId}/novels/upload` | 上传小说文件 |
| POST | `/api/v1/novels/{id}/summarize` | 触发分章摘要(A.8.2) |
| GET | `/api/v1/novels/{id}/summaries` | 获取各章摘要进度 |
| POST | `/api/v1/novels/{id}/convert` | 小说→剧本转换(A.1) |
| GET | `/api/v1/novels/{id}/status` | 获取转换状态 |

**角色模块 (CHARACTER)**
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/projects/{projectId}/characters` | 手动创建角色 |
| POST | `/api/v1/scripts/{id}/extract-assets` | AI 提取资产(触发 A.6) |
| GET | `/api/v1/extracted-assets?scriptId={id}&type=CHARACTER` | 待确认角色列表 |
| PUT | `/api/v1/extracted-assets/{id}/confirm` | 确认入库角色 |
| GET | `/api/v1/characters/{id}` | 角色详情(6层锚点) |
| POST | `/api/v1/characters/{id}/makeup` | 生成定妆图 |
| PUT | `/api/v1/characters/{id}` | 编辑角色 |

**场景模块 (SCENE)**
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/projects/{projectId}/scenes` | 手动创建场景 |
| GET | `/api/v1/scenes/{id}` | 场景详情 |
| POST | `/api/v1/scenes/{id}/generate-views` | 生成四视图(A.7) |
| POST | `/api/v1/scenes/{id}/regenerate-view/{viewType}` | 重试单个视角(front/back/left/right) |
| PUT | `/api/v1/scenes/{id}` | 编辑场景 |

**分镜模块 (SHOT / STORYBOARD)**
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/episodes/{id}/parse` | 解析剧本→结构化数据(A.8.1) |
| PUT | `/api/v1/episodes/{id}/parsed-data` | 编辑结构化数据 |
| POST | `/api/v1/episodes/{id}/generate-storyboards` | 生成分镜(A.2+A.4) |
| GET | `/api/v1/storyboards?episodeId={id}` | 分镜列表 |
| GET | `/api/v1/storyboards/{id}` | 单个分镜详情 |
| POST | `/api/v1/storyboards/{id}/regenerate-image` | 重新生成分镜图片 |
| PUT | `/api/v1/storyboards/{id}` | 编辑分镜参数 |
| POST | `/api/v1/storyboards/batch-update` | 批量编辑分镜 |
| DELETE | `/api/v1/storyboards/{id}` | 删除分镜 |

**导演模块 (DIRECTOR / VIDEO)**
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/episodes/{id}/generate-videos` | 整集视频生成(优先) |
| POST | `/api/v1/storyboards/{id}/generate-video` | 单镜头视频生成(回退方案) |
| POST | `/api/v1/episodes/{id}/stitch-videos` | FFmpeg拼接多视频(ADR-13回退) |
| GET | `/api/v1/generation-tasks?episodeId={id}&purpose=STORYBOARD_VIDEO` | 视频任务列表 |
| GET | `/api/v1/generation-tasks/{id}` | 任务详情+进度 |

**S级模块 (S_LEVEL / OUTPUT)**
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/episodes/{id}/compose` | 成片合成(FFmpeg) |
| GET | `/api/v1/episodes/{id}/preview` | 成片预览URL |
| POST | `/api/v1/episodes/{id}/export` | 导出成片(MP4/MOV等) |
| POST | `/api/v1/episodes/{id}/watermark` | 添加水印 |

**流水线状态 (PIPELINE)**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/projects/{id}/pipeline-state` | 获取流水线状态 |
| POST | `/api/v1/pipeline-states/{id}/advance` | 推进到下一阶段(带脏标记检查) |
| POST | `/api/v1/pipeline-states/{id}/re-execute` | 重新执行当前阶段 |

**配置中心 (CONFIG)**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/model-configs?type=TEXT` | 按类型获取模型配置 |
| POST | `/api/v1/model-configs` | 新增模型配置 |
| PUT | `/api/v1/model-configs/{id}` | 更新配置 |
| POST | `/api/v1/model-configs/{id}/test-connection` | 测试API连通性 |
| GET | `/api/v1/prompt-templates?category=xxx` | 提示词模板列表 |
| POST | `/api/v1/prompt-templates` | 创建提示词模板 |
| PUT | `/api/v1/prompt-templates/{id}` | 更新模板 |
| GET | `/api/v1/app-config` | 应用全局配置 |
| PUT | `/api/v1/app-config` | 更新全局配置 |

**素材库 (ASSET)**
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/projects/{id}/assets/upload` | 上传素材文件 |
| GET | `/api/v1/assets?projectId={id}&type=IMAGE&tags=xxx` | 素材搜索 |
| DELETE | `/api/v1/assets/{id}` | 删除素材 |

**任务调度 & SSE**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/sse/tasks/{taskId}` | SSE 任务进度订阅(ADR-14) |
| POST | `/api/v1/tasks/cancel` | 取消任务 |
| GET | `/api/v1/tasks/history` | 任务历史记录 |

**版本 & 备份**
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/objects/{type}/{id}/save-version` | 保存版本快照 |
| GET | `/api/v1/objects/{type}/{id}/versions` | 版本历史列表 |
| POST | `/api/v1/version-history/{id}/rollback` | 回滚到指定版本 |
| POST | `/api/v1/projects/{id}/backup` | 手动备份 |
| GET | `/api/v1/projects/{id}/backups` | 备份列表 |
| POST | `/api/v1/backups/{id}/restore` | 从备份恢复 |

#### 6.4.4 文件上传接口
- **上传端点**: `POST /api/v1/files/upload` (multipart/form-data)
- **大小限制**: 单文件最大 100MB（可配置）
- **支持格式**:
  - 图片: jpg/png/webp/gif
  - 视频: mp4/mov/avi
  - 音频: mp3/wav/aac/flac
  - 文档: txt/pdf/epub/docx
- **存储策略**: 自动保存到对应项目目录下的子文件夹（见 8.1.2 目录结构）

## 七、页面布局

### 7.1 整体布局
- 左侧边栏：项目列表、素材库、配置中心
- 顶部导航：项目名称、品牌Logo、用户菜单
- 主内容区：当前路由模块

### 7.2 核心页面
1. **项目列表页**：卡片式展示，新建/删除/编辑
2. **剧本编辑页**：左侧树 + 右侧编辑器
3. **分镜编排页**：顶部卡片列表 + 底部详情
4. **素材库页**：网格布局，上传/搜索/分类
5. **配置中心页**：分组Tab（文本/生图/视频/音频/品牌）

## 八、部署方案

### 8.1 桌面端（Electron）
#### 8.1.1 安装配置
- **安装路径选择**：首次安装时提供路径选择对话框，允许用户自定义安装目录
- **默认路径**：
  - Windows: `C:\Users\[用户名]\Documents\AI漫剧平台`
  - macOS: `~/Documents/AI漫剧平台`
  - Linux: `~/AI漫剧平台`
- **配置文件**：安装路径保存到用户配置文件（`config.json`）

#### 8.1.2 项目文件目录结构
每个项目独立文件夹，按项目模块组织文件：
```
[安装路径]/
├── config/
│   └── config.json          # 全局配置
├── database/
│   └── ai_comic_platform.db # SQLite数据库
├── logs/                    # 日志文件夹
│   ├── app.log
│   ├── error.log
│   └── operation.log
├── cache/                   # 缓存文件夹
├── docs/                    # 帮助文档
├── backups/                 # 备份文件夹
│   └── [项目ID]-[时间戳]/
├── templates/               # 模板文件夹
│   ├── project/
│   ├── style/
│   └── prompt/
├── presets/                 # 预置资源
│   ├── audio/
│   │   ├── bgm/
│   │   └── sfx/
│   └── examples/            # 示例项目
├── projects/
│   └── [项目ID]-[项目名称]/  # 每个项目独立文件夹
│       ├── characters/       # 角色文件夹
│       │   ├── [角色ID]-定妆图.jpg
│       │   └── [角色ID]-参考图.png
│       ├── scenes/          # 场景文件夹
│       │   ├── [场景ID]-正面.jpg
│       │   ├── [场景ID]-背面.jpg
│       │   ├── [场景ID]-左侧.jpg
│       │   └── [场景ID]-右侧.jpg
│       ├── storyboards/     # 分镜文件夹
│       │   ├── [分镜ID]-生成图.jpg
│       │   └── [分镜ID]-生成视频.mp4
│       ├── assets/          # 素材文件夹
│       │   ├── images/
│       │   ├── audio/
│       │   └── video/
│       ├── audio/           # 项目音频文件夹
│       │   ├── voice/       # 配音
│       │   ├── bgm/         # 背景音乐
│       │   └── sfx/         # 音效
│       ├── drafts/          # 草稿文件夹
│       ├── versions/        # 版本历史快照
│       ├── output/          # 最终输出文件夹
│       │   ├── episode-001.mp4
│       │   └── episode-002.mp4
│       └── project.json     # 项目配置
├── exports/                 # 导出文件夹
└── temp/                    # 临时文件
```

#### 8.1.3 路径管理功能
- **项目文件组织**：所有生成的文件自动保存到对应项目目录下的子文件夹
- **文件命名规范**：`[对象类型]-[ID]-[用途].[扩展名]`
- **路径配置面板**：在设置中提供项目存储路径修改功能
- **迁移工具**：支持项目整体移动、导入/导出项目

#### 8.1.4 技术实现
- Electron 主进程启动时 spawn `java -jar app.jar --server.port={random_port}`（ADR-2/ADR-18）
- 前端通过 `http://localhost:{random_port}` 访问后端 API
- 随机端口在 Electron 主进程中生成，通过环境变量传递给 Spring Boot
- Spring Boot 启动完成后写入就绪标记文件，Electron 检测到后加载前端页面
- SQLite 数据库：`[安装路径]/database/ai_comic_platform.db`
- 文件存储：本地文件系统，按上述目录结构组织
- FFmpeg：打包 FFmpeg 二进制到应用目录（ADR-17），用于视频后期合成
- 打包工具：Electron Builder
- **品牌图标配置**：
  - `icon` 字段指向 `assets/logo.png`（自动生成 Windows .ico / macOS .icns / Linux .png）
  - `appId`: `com.aicomic.platform`
  - `productName`: `AI漫剧`
  - 启动页 (`splashscreen`) 使用同款 Logo 居中展示
- 预计安装包大小：~300MB（含 JVM + FFmpeg）

#### 8.1.5 应用自动更新机制
- **更新工具**：electron-updater + Electron Builder publish 配置
- **更新源**：GitHub Releases 或自建文件服务器（可配置 `autoUpdateUrl`）
- **更新流程**：
  1. 应用启动后静默检查更新（调用 update server 的 `latest.yml` / `latest-mac.yml`）
  2. 有新版本时下载差量更新包（仅下载变更部分，减小体积）
  3. 下载完成后提示用户「立即重启更新」或「下次启动时更新」
  4. 用户选择后应用退出 → 替换文件 → 重新启动
- **更新策略配置**（`app_config` 表）：
  - `auto_update_enabled`: 是否启用自动更新(Boolean, 默认 true)
  - `update_channel`: 更新通道(stable/beta/nightly)
  - `check_interval`: 检查间隔(小时, 默认 24)
- **手动检查**：设置页面提供「检查更新」按钮，显示更新日志
- **离线支持**：检测不到网络时跳过检查，不影响正常使用
- **安全**：更新包 SHA256 校验 + 签名验证，防止篡改

---

### 8.2 Web端（可选）
- **不使用 Docker**
- 后端：直接运行 JAR 包（`java -jar app.jar`）
- 前端：构建后用 Nginx 或 Spring Boot 静态资源托管
- 数据库：SQLite
- 文件存储：本地文件系统（简单）或对象存储（高配可选）

---

## 附录：完整预置提示词内容

### A.1 小说转分集剧本提示词

```
【任务目标】
根据输入的小说内容，将其拆分为{episodeCount}集短剧剧本。

【输入内容】
小说标题：{novelTitle}
小说内容：
{inputContent}

【重要要求】
1. 所有角色必须使用具体的名字，不能是泛称如"人类小孩"、"老人"、"神秘人"等
2. 角色命名示例：李涛、王小明、张道长、如来佛祖、玉帝、观音菩萨、太白金星、路人甲、店小二、黑衣人等
3. 场景必须使用具体的地点名称，如：花果山水帘洞、长安城醉仙楼、东海龙宫、天庭凌霄殿等
4. 道具必须使用具体的物品名称，如：如意金箍棒、乾坤圈、混天绫、紫金葫芦等
5. 每集剧情必须有明显差异，避免重复相似的情节
6. 剧情要有起伏变化，不能每集都是同样的套路

【单集摘要固定结构（必须严格遵守）】
1. 只写剧情梗概，不写台词、不分镜、不写心理活动
2. 每集摘要必须自然连贯地包含4要素（人物、场景、冲突、结尾悬念钩子），用流畅的叙述呈现，不要分点列出
3. 每集字数：200字以上。
4. 全程人设统一、剧情连贯、前后伏笔呼应

示例格式：
男主在酒楼被反派当众挑衅，对方故意刁难羞辱。男主隐忍不发，暗中观察发现对方背后另有势力。男主假意妥协稳住局面，暗中收集证据，反派嚣张离去，留下下次要彻底打压男主的伏笔。

请为全部{episodeCount}集的每一集生成：
1. id：集数编号
2. title：标题（4-10字，吸引人，概括核心内容，避免过于简单如"初遇"、"决战"等两字标题）
3. summary：分集摘要（70-200字），用连贯的叙述自然包含人物、场景、冲突、悬念四要素，只写梗概不写台词
4. characters：本集出现的角色列表（具体名字）
5. scenes：本集出现的场景列表（具体地点名）
6. props：本集出现的重要道具列表（具体物品名）

请按以下JSON格式返回全部{episodeCount}集：
{
  "episodes": [
    {
      "id": 1,
      "title": "第一集标题",
      "summary": "男主在酒楼被反派当众挑衅，对方故意刁难羞辱。男主隐忍不发，暗中观察发现对方背后另有势力。男主假意妥协稳住局面，暗中收集证据，反派嚣张离去，留下下次要彻底打压男主的伏笔。",
      "characters": ["李涛", "张道长"],
      "scenes": ["长安城", "青云观"],
      "props": ["桃木剑", "符咒"]
    }
  ]
}

【严格要求】
1. 必须一次性生成全部{episodeCount}集，不能分批
2. 每集标题4-10字，要吸引人，概括本集核心内容，避免过于简单的两字标题，且各集标题要有区分度
3. 每集摘要要用连贯的叙述自然包含4要素（人物、场景、冲突、悬念），不要分点列出，字数控制在70-200字
4. 只写剧情梗概，绝对不能写台词、不分镜、不写心理活动描写
5. 剧情中必须使用具体的角色名（如李涛、张道长），不能使用"人类小孩"、"老人"等泛称
6. 场景描述必须使用具体的地点名（如长安城、青云观），不能使用"某个地方"、"城里"等泛称
7. 道具必须使用具体的物品名（如桃木剑、符咒），不能使用"武器"、"法宝"等泛称
8. 各集之间剧情要有明显差异，避免雷同，每集都要有独特的冲突和转折
9. 确保整体剧情连贯，角色行为、情节发展符合逻辑，前后伏笔呼应
10. 只返回JSON格式数据，不要包含其他说明文字
```

---

### A.2 漫剧分镜文案师提示词
> ⚠️ 注意：此提示词源自魔因漫创的 Seedance 2.0 格式，包含模型特定的 `@图` 引用和 `---SHOT_SEPARATOR---` 分隔符。
> 根据ADR-6（模型无关），此模板作为 Seedance/扣子工作流的默认模板，其他模型（如 SKYREELS-V4）可配置不同的模板格式。
> 提示词引擎通过 `prompt_template` 表支持多模板切换，变量用 `{varName}` 标记。
> 已验证兼容引擎：Seedance 2.0、SKYREELS-V4（参照魔因漫创 V0.2.8）。

```
【系统提示词】
你是Seedance 2.0漫剧分镜文案师，按以下格式生成分镜提示词。

【分镜生成要求】
1. 每个分镜的描述必须详细充实，不低于200字，每个分镜必须包含至少3句台词对话
2. 画面描述包含：场景细节、角色动作、表情变化、眼神方向、环境氛围、光影效果
3. 运镜描述要具体：镜头运动方向、速度、目的，让读者能清晰想象画面
4. 台词要符合角色性格，推动剧情发展，每镜至少3句话，不能只有一句台词
5. 音效描述要具体：环境音效、情绪渲染

【核心规则】
1. 总时长严格锁定{seconds}秒，画面比例{aspectRatio}，风格{style}
2. 角色形象全程统一，无变形、无穿模
3. 单镜头只用一种运镜方式，每个镜头必须分配具体秒数（如"3秒"、"4秒"）
4. 所有镜头秒数之和必须严格等于{seconds}秒，不能多也不能少
5. 台词要求（非常重要）：
   - 每个镜头必须有台词，不能留空
   - 台词类型要丰富：单人独白、双人对话、多人对话、内心独白、旁白解说
   - 对话要自然流畅，符合角色性格
   - 台词格式必须使用标准格式：[角色名, 情绪]: "台词内容"
   - 正确示例：[孙悟空, 坚定]: "师父，让我去前面看看情况！"
   - 正确示例：[女主角, 温柔]: "你来了~"
   - 正确示例：[男主角, 愤怒]: "这不可能！"
   - 台词用双引号包裹，音画同步，必须包含说话动作和情绪描述
6. 台词/旁白匹配对应视线与对话对象，对话用正反打，禁止越轴。搭配多元视角与运镜，合理留白。
7. 负面约束：禁止生成背景音乐，只保留人物对话声音和环境音效
8. 分镜之间用---SHOT_SEPARATOR---分隔

【运镜方式知识库（AI必须根据场景自动选择并用自然语言描述）】

【基础运镜（15个）】
- 固定镜头：镜头静止不动，适合展现场景全貌、烘托气氛。使用场景：古风庭院、都市夜景、大殿等需要展现场景氛围的画面
- 缓慢推镜：镜头缓慢向前推进，逐渐靠近主体。使用场景：情绪递进、细节特写、主角顿悟时刻
- 缓慢拉镜：镜头缓慢向后拉远，从近景过渡到全景。使用场景：场景升华、结尾留白、展现主体与环境关系
- 水平摇镜：镜头左右平稳摇动。使用场景：山川全景、街道路景、大殿全貌，节奏均匀
- 垂直摇镜：镜头上下平稳摇动。使用场景：高楼、瀑布、古建筑等高大主体
- 跟拍镜头：镜头跟随主体移动。使用场景：走路、奔跑场景，保持主体在画面中央
- 过肩镜头：从角色肩膀后方拍摄。使用场景：对话、对峙场景，增强代入感
- 俯拍镜头：从高处向下拍摄。使用场景：全景展示、多人场景、情侣约会
- 仰拍镜头：从低处向上拍摄。使用场景：英雄登场、突出气场、拍摄高大建筑
- 特写镜头：拍摄主体局部细节。使用场景：面部表情、手部动作、关键道具，传递情绪
- 中景镜头：拍摄主体半身。使用场景：展示动作、对话交流、两人对视
- 全景镜头：拍摄完整场景和主体。使用场景：开篇展示、结尾收束、展现场景全貌
- 慢镜头：画面速度放慢。使用场景：落泪、花瓣飘落、动作突出、氛围营造
- 快镜头：画面速度加快。使用场景：压缩时间、赶路场景、忙碌日常、紧张感营造
- 静止镜头：画面完全静止。使用场景：定格情绪、强调重点、主角愣住时刻

【特效运镜（10个）】
- 模糊转场运镜：画面从模糊变清晰或从清晰变模糊。使用场景：场景切换、时间跳转
- 光晕运镜：镜头带光晕效果。使用场景：甜宠场景、治愈场景、阳光照射、夜晚灯光
- 颗粒感运镜：画面添加颗粒质感。使用场景：悬疑场景、复古场景、电影质感
- 慢动作加光晕：慢镜头配合光晕效果。使用场景：高光瞬间、落泪、烟花绽放
- 抖动运镜：镜头轻微抖动。使用场景：紧张场景、惊悚场景、追逐、惊吓片段
- 叠化运镜：两个画面叠化衔接。使用场景：回忆场景、梦境场景、过渡自然
- 色差运镜：画面添加轻微色差。使用场景：复古场景、文艺场景、校园回忆
- 对焦模糊运镜：故意虚焦后慢慢对焦。使用场景：突出关键道具、营造悬念、发现线索
- 光影运镜：镜头跟随光影移动。使用场景：阳光透过树叶、灯光照射主角、营造氛围
- 全景模糊加聚焦：全景模糊后聚焦到主体。使用场景：主角登场、突出重点、画面层次

【运镜描述规则（重要）】
1. 必须使用自然语言描述运镜，禁止使用"运镜：XXX"的格式
2. 正确示例："镜头缓慢向前推进，逐渐靠近女主角面部，突出她眼中的泪光"
3. 错误示例："运镜：缓慢推镜"
4. 根据场景情绪和动作自动选择最合适的运镜方式
5. 单镜头只用一种运镜方式，避免频繁切换
6. 运镜描述要包含方向、速度、目的，让画面有电影感

【输出格式规则，必须严格遵守】
【全局基础设定】
注意：【全局基础设定】后面直接跟【角色引用】，不要添加任何其他内容，不要添加"整体风格"等额外信息。

【角色引用】只列出本分镜实际出现的角色：
@图1【角色】A名（性别，年龄，性格特征）
@图2【角色】B名（性别，年龄，性格特征）
@图x(x=角色引用的数量 + 1)【场景】场景名（场景描述）
场景环境：@图x场景名，时间、天气、氛围描述
光影色调：主色调、光线描述

【视频基础参数】
总时长：{seconds}秒
画面比例：{aspectRatio}
画风设定：{style}，4K分辨率，精细细节，画面层次丰富，清晰度高，全程画风统一，人物形象、五官全程固定，无变形、无穿模、无闪帧，动作自然流畅

【分镜明细】
0-4s: [承接上镜：分镜2结尾10秒时，黑色长剑反射出林风绝望的脸庞，苏清寒持剑而立。] + [场景信息：九天封神台] + 缓慢推镜，聚焦在林风的面部，展现他眼中的难以置信与失望，金色泪水混合着血液滑落。[cut]
4-7s: 镜头反打，特写苏清寒冰冷的眼神与嘴角的冷笑，她缓缓举起长剑，黑色煞气在剑身上盘旋。[cut]
7-10s: 仰拍镜头，苏清寒高举长剑，天空中一道闪电划过，照亮她狰狞的侧脸，煞气冲天而起。[cut]

【角色对话】
4-7s [林风, 痛苦颤抖]: "清寒……为什么？我待你如亲妹，你为何要背叛我？"[cut]

【背景音效】
0-4s: 林风沙哑的呼吸声[cut]
4-7s: 林风痛苦的质问声[cut]
7-10s: 闪电划破天际的轰鸣声、长剑凝聚煞气的嗡鸣声[cut]

【负面约束-必须遵守】
No music, no background music, no BGM, no soundtrack，只生成人物对话声音和环境音效

---SHOT_SEPARATOR---

【以下是输出格式模板，必须严格遵守】
【全局基础设定】
注意：【全局基础设定】后面直接跟【角色引用】，不要添加任何其他内容，不要添加"整体风格"等额外信息。

【角色引用】只列出本分镜实际出现的角色：
@图1【角色】A名（性别，年龄，性格特征）
@图2【角色】B名（性别，年龄，性格特征）
@图x(x=角色引用的数量 + 1)【场景】场景名（场景描述）
场景环境：@图x场景名，时间、天气、氛围描述
光影色调：主色调、光线描述

【视频基础参数】
总时长：{seconds}秒
画面比例：{aspectRatio}
画风设定：{style}，4K分辨率，精细细节，画面层次丰富，清晰度高，全程画风统一，人物形象、五官全程固定，无变形、无穿模、无闪帧，动作自然流畅

【分镜明细】
0-4s: [承接上镜：分镜2结尾10秒时，黑色长剑反射出林风绝望的脸庞，苏清寒持剑而立。] + [场景信息：九天封神台] + 缓慢推镜，聚焦在林风的面部，展现他眼中的难以置信与失望，金色泪水混合着血液滑落。[cut]
4-7s: 镜头反打，特写苏清寒冰冷的眼神与嘴角的冷笑，她缓缓举起长剑，黑色煞气在剑身上盘旋。[cut]
7-10s: 仰拍镜头，苏清寒高举长剑，天空中一道闪电划过，照亮她狰狞的侧脸，煞气冲天而起。[cut]

【角色对话】
4-7s [林风, 痛苦颤抖]: "清寒……为什么？我待你如亲妹，你为何要背叛我？"[cut]

【背景音效】
0-4s: 林风沙哑的呼吸声[cut]
4-7s: 林风痛苦的质问声[cut]
7-10s: 闪电划破天际的轰鸣声、长剑凝聚煞气的嗡鸣声[cut]

【负面约束-必须遵守】
No music, no background music, no BGM, no soundtrack，只生成人物对话声音和环境音效

---SHOT_SEPARATOR---

【模板结束】
```

---

### A.4 分镜后缀提示词

```
【重要要求】
1. 请为上述所有分镜一次性生成视频提示词.
2. 确保剧情连贯，角色状态一致，场景过渡自然
3. 每个分镜之间用 '---SHOT_SEPARATOR---' 分隔
4. 严禁输出 Markdown 符号（如 ** 或 *）
5. 只输出纯文本内容，不要添加任何说明文字
```

---

### A.5 大纲生成提示词

```
# 剧本大纲生成提示词
# 用途：根据剧本标题、主题和故事概要，一次性生成全部剧集的剧情大纲和每集摘要
# 调用位置：ScriptEditor.vue - generateOutline() 函数
# 参数说明：
#   - {episodeCount}: 用户设置的集数
#   - {scriptTitle}: 剧本标题
#   - {scriptTheme}: 核心主题
#   - {scriptSummary}: 故事概要/简介
#   - {styleHint}: 剧情风格要求（用户选择的关键词）

请根据以下剧本信息，一次性生成全部{episodeCount}集的剧情大纲和每集摘要。

剧本标题：{scriptTitle}
核心主题：{scriptTheme}
故事概要：{scriptSummary}
{styleHint}

重要要求：
1. 所有角色必须使用具体的名字，不能是泛称如"人类小孩"、"老人"、"神秘人"等
2. 角色命名示例：李涛、王小明、张道长、如来佛祖、玉帝、观音菩萨、太白金星、路人甲、店小二、黑衣人等
3. 场景必须使用具体的地点名称，如：花果山水帘洞、长安城醉仙楼、东海龙宫、天庭凌霄殿等
4. 道具必须使用具体的物品名称，如：如意金箍棒、乾坤圈、混天绫、紫金葫芦等
5. 每集剧情必须有明显差异，避免重复相似的情节
6. 剧情要有起伏变化，不能每集都是同样的套路

【单集摘要固定结构（必须严格遵守）】
1. 只写剧情梗概，不写台词、不分镜、不写心理活动
2. 每集摘要必须自然连贯地包含4要素（人物、场景、冲突、结尾悬念钩子），用流畅的叙述呈现，不要分点列出
3. 每集字数：200字以上。
4. 全程人设统一、剧情连贯、前后伏笔呼应

示例格式：
男主在酒楼被反派当众挑衅，对方故意刁难羞辱。男主隐忍不发，暗中观察发现对方背后另有势力。男主假意妥协稳住局面，暗中收集证据，反派嚣张离去，留下下次要彻底打压男主的伏笔。

请为全部{episodeCount}集的每一集生成：
1. id：集数编号
2. title：标题（4-10字，吸引人，概括核心内容，避免过于简单如"初遇"、"决战"等两字标题）
3. summary：分集摘要（70-200字），用连贯的叙述自然包含人物、场景、冲突、悬念四要素，只写梗概不写台词
4. characters：本集出现的角色列表（具体名字）
5. scenes：本集出现的场景列表（具体地点名）
6. props：本集出现的重要道具列表（具体物品名）

请按以下JSON格式返回全部{episodeCount}集：
{
  "episodes": [
    {
      "id": 1,
      "title": "第一集标题",
      "summary": "男主在酒楼被反派当众挑衅，对方故意刁难羞辱。男主隐忍不发，暗中观察发现对方背后另有势力。男主假意妥协稳住局面，暗中收集证据，反派嚣张离去，留下下次要彻底打压男主的伏笔。",
      "characters": ["李涛", "张道长"],
      "scenes": ["长安城", "青云观"],
      "props": ["桃木剑", "符咒"]
    }
  ]
}

严格要求：
1. 必须一次性生成全部{episodeCount}集，不能分批
2. 每集标题4-10字，要吸引人，概括本集核心内容，避免过于简单的两字标题，且各集标题要有区分度
3. 每集摘要用连贯的叙述自然包含4要素（人物、场景、冲突、悬念），不要分点列出，字数控制在70-200字
4. 只写剧情梗概，绝对不能写台词、不分镜、不写心理活动描写
5. 剧情中必须使用具体的角色名（如李涛、张道长），不能使用"人类小孩"、"老人"等泛称
6. 场景描述必须使用具体的地点名（如长安城、青云观），不能使用"某个地方"、"城里"等泛称
7. 道具必须使用具体的物品名（如桃木剑、符咒），不能使用"武器"、"法宝"等泛称
8. 各集之间剧情要有明显差异，避免雷同，每集都要有独特的冲突和转折
9. 确保整体剧情连贯，角色行为、情节发展符合逻辑，前后伏笔呼应
10. 只返回JSON格式数据，不要包含其他说明文字
```

---

### A.6 角色提示词

```
# 人物角色提取提示词

## 任务目标
根据剧本内容，提取并整理出所有角色、场景和道具的详细信息。

## 输入内容
剧本标题：{scriptTitle}
核心主题：{scriptTheme}
分集内容：
{episodesContent}

## 角色提取强制要求
1. 性别判断（必须返回male或female，禁止返回中文）：
- 根据角色名字、称谓、行为特征判断
- 男性角色（公子、将军、老爷、少爷、道士、和尚、护卫、统领等）→ 填写 "male"
- 女性角色（小姐、娘娘、夫人、丫鬟、公主、贵女等）→ 填写 "female"
- 严禁返回 "男"、"女"、"未知" 等中文，必须返回英文 "male" 或 "female"

2. 年龄推理：根据角色身份、行为、对话语气判断
- 少年（15-20岁）：年轻、冲动、学徒身份
- 青年（20-30岁）：主角常见年龄段
- 中年（30-50岁）：成熟稳重、长辈、领导身份
- 老年（50岁以上）：白发、胡须、宗师、老仆人

3. 服装类型判断：根据剧本时代背景、角色身份选择
- 古装：古代背景的角色
- 战甲：武将、士兵、战士
- 仙袍：仙人、道士、修真者
- 现代装：现代背景的角色
- 铠甲：盔甲类角色

4. 服装描述必须详细（用于AI定妆图生成，规避肖像权风险）：
- 颜色：具体颜色（青/白/黑/红/紫/金等）
- 材质：丝绸/锦缎/棉布/皮革/金属等
- 款式：长袍/短打/裙装/官服/道袍等
- 配饰：腰带/玉佩/武器/头饰等

5. 外貌特征推理：
- 脸型：根据角色气质选择（瓜子脸-柔美，方脸-刚毅，圆脸-可爱等）
- 眼睛：根据角色性格选择（丹凤眼-英气，桃花眼-妩媚，大眼睛-清纯等）
- 发型：根据性别年龄选择（长发/短发/白发/黑发等）

6. 【重要】AI定妆图描述（makeupImageDesc）：
- 请生成一段完整的AI绘画提示词，描述该角色的标准形象
- 用于生成独一无二的虚拟角色形象，确保与任何真实人物都不相似
- 必须包含：面部特征（脸型、眼睛、鼻子、嘴唇、眉毛的具体形状）、发型细节、服装完整描述、整体气质
- 示例："一位20岁左右的古代贵女，瓜子脸，丹凤眼微微上挑，柳叶眉，樱桃小嘴，肌肤白皙。乌黑长发挽成飞仙髻，插着珍珠发簪。身着淡紫色丝绸齐胸襦裙，外罩白色轻纱，腰间系着白玉腰带，裙摆绣有梅花纹样。气质温婉优雅，眼神清澈明亮。"

## 禁止事项
- 禁止所有角色使用相同的默认值
- 禁止填写"未知"、"无"等模糊描述
- 必须根据剧本内容具体推理

## 输出格式
请按以下JSON格式返回，角色信息必须完整且各不相同：
{
"characters": [
{
"name": "角色姓名",
"firstEpisode": 1,
"type": "主角/配角/反派",
"gender": "male/female",
"age": "具体年龄或年龄段",
"faceShape": "瓜子脸/鹅蛋脸/圆脸/方脸/清瘦/棱角分明",
"eyes": "大眼睛/丹凤眼/桃花眼/细长眼",
"hairStyle": "长发/短发/卷发/白发/黑发",
"costumeType": "古装/战甲/仙袍/现代装/铠甲",
"costumeDesc": "详细服装描述：颜色+材质+款式+配饰",
"makeupImageDesc": "【AI定妆图描述】完整的AI绘画提示词，包含面部特征、发型、服装、气质的详细描述，用于生成独一无二的虚拟角色形象，规避肖像权风险",
"features": ["特殊特征，如没有则留空数组"],
"description": "角色身份背景描述"
}
],
"scenes": [
{"name": "场景名", "firstEpisode": 1, "type": "主要场景/次要场景", "description": "场景描述"}
],
"props": [
{"name": "道具名", "firstEpisode": 1, "type": "武器/法器/日常物品", "description": "道具描述"}
]
}

## 示例
如果剧本中有"秦雪薇穿着一袭淡紫色长裙，腰间系着白玉腰带"，则：
- gender: "female"
- costumeType: "古装"
- costumeDesc: "淡紫色丝绸长裙，腰间系着白玉腰带，裙摆绣有梅花纹样"
- makeupImageDesc: "一位20岁左右的古代贵女，瓜子脸，丹凤眼微微上挑，柳叶眉，樱桃小嘴，肌肤白皙。乌黑长发挽成飞仙髻，插着珍珠发簪。身着淡紫色丝绸齐胸襦裙，外罩白色轻纱，腰间系着白玉腰带，裙摆绣有梅花纹样。气质温婉优雅，眼神清澈明亮，站姿端庄。"

如果剧本中有"卫无忌身披玄铁战甲，手持长枪"，则：
- gender: "male"
- costumeType: "战甲"
- costumeDesc: "玄铁战甲，肩甲刻有虎纹，腰悬长剑，披黑色披风"
- makeupImageDesc: "一位25岁左右的年轻武将，方脸，浓眉大眼，鼻梁挺直，嘴唇紧抿，面容刚毅。黑色短发束于头顶，戴着青铜头盔。身披玄铁战甲，肩甲刻有虎纹，胸前护心镜锃亮，腰悬长剑，披黑色披风。身姿挺拔，目光坚定，手持长枪站立，英气逼人。"

只返回JSON格式，不要其他说明文字。
```

---

### A.7 场景提取提示词

```
# 文生图场景静态初始状态提取提示词

## 任务目标
从提供的文档中，提取并整合每个独立"场景"的静态初始状态描述。此描述用于文生图的场景设计提示词，构建剧情发生前的环境基础。

## 核心原则
1. **静态优先**：只描述场景的初始、静态状态。不包含任何剧情发展产生的事件结果（如打碎的物品、泼洒的液体、移动过的家具等）。
2. **无角色信息**：场景描述中不出现人物，也不使用角色名称来定位物品。物品的位置仅通过文档中"角色位置"标注的方位（如"画面左侧""画面右侧""中景区域"）来反推和安排。
3. **初始状态**：场景为剧情发生前的状态。例如，若后续剧情有打翻杯子、摔门等变化，初始场景中杯子应完好置于桌面，门处于关闭状态。
4. **场景唯一性**：以文档中明确的"场景"标识（如"内景 卧室 - 深夜"）为单位进行合并输出，即使该场景对应多个分镜单元。

## 输出格式要求
为每个场景生成一个独立的描述块，结构如下：

```
[场景唯一名称，对应单元]

- 影片类型：精准匹配剧本核心影视类型、核心美术体系
- 核心场景：[场景唯一名称，如：卧室_深夜；核心叙事主体、剧情语境，一句话说明]
- 空间结构：描述房间的形状、门窗位置、动线关系等。
- 陈设布局：描述主要家具（书桌、床、沙发、餐桌等）的具体位置、朝向及其相互关系。位置参考文档中的"角色位置"信息来安排——例如，若文档标注某角色位于画面左侧，则与该角色交互的主要家具（如书桌、椅子）应放置在画面左侧。所有物品均处于未使用、未扰动的初始状态。
- 细节质感：描述道具（作业本、台灯、杯子、书包等）的材质、摆放状态、表面细节（如页面上的印刷或书写痕迹可存在，但无动态痕迹）。地面、墙面、织物等环境细节需完整。
- 光影氛围：直接引用或概括文档中的"光影氛围"描述，明确光源类型、位置、色温、强度及整体影调。
- 色彩基调：明确场景整体色彩风格与主色调，匹配影片类型与场景叙事属性，无模糊表述。
- 固定镜头视角：强制固定为「全景平视视角」
- 技术参数：8K超高清，超写实电影级摄影，全域清晰无景深虚化，无动态模糊，物理级精准光影渲染，UE5离线渲染，细节拉满，画面干净无杂质，无任何人物、无任何动物，无多余元素

---SCENE_SEPARATOR---
```

## 约束条件
1. 不出现人物。
2. 不描述任何动作、情绪、对话。
3. 不描述剧情过程中或之后产生的变化（如碎裂、泼洒、移动、摔门后的震动等）。
4. 不举例说明具体剧本情节。
```

---

### A.3 资产信息提取说明
已合并到 **A.6 角色提示词** 和 **A.7 场景提取提示词** 中。

---

### A.8 剧本解析与分章摘要提示词

> 支撑 ADR-19（三步分镜流程步骤1）和 ADR-9（分章节摘要策略）的核心提示词模板。

#### A.8.1 剧本解析为结构化数据提示词（ADR-19 步骤1）

```
# 剧本结构化解析提示词
# 用途：将当前集的剧本文本解析为结构化数据（场景列表+对白列表+动作列表+情绪标注）
# 调用位置：StoryboardEditor.vue - parseScript() 函数
# 参数说明：
#   - {episodeTitle}: 当前集标题
#   - {scriptContent}: 当前集剧本完整内容
#   - {characterList}: 本集涉及角色列表(JSON数组)
#   - {sceneList}: 本集涉及场景列表(JSON数组)

## 任务目标
将以下剧本内容解析为结构化的分镜预备数据，为后续生成分镜提示词提供精确输入。

## 输入信息
集标题：{episodeTitle}
本集角色：{characterList}
本集场景：{sceneList}

剧本原文：
{scriptContent}

## 解析规则
1. **场景切分**：按场景切换点拆分为独立的场景单元，每个场景单元包含：
   - scene_name: 场景名称（使用具体地点名，如"长安城醉仙楼"）
   - scene_id: 关联的场景ID
   - time_of_day: 时间段（早晨/中午/下午/傍晚/深夜/凌晨）

2. **镜头/分镜拆分**：在每个场景内，按以下规则识别独立分镜：
   - 镜头切换（视角变化、人物进出画面、时间跳跃）
   - 对话段落变化（不同角色的对话块）
   - 动作段落变化（从对话转为动作描写，或反之）
   - 情绪转折点（情绪发生明显变化的时刻）

3. **每个分镜必须提取**：
   - sequence: 序号（从0开始连续编号）
   - time_range: 时间范围预估（如"0-5秒"）
   - continuity: 承接上镜描述（描述上一个分镜结束时的状态）
   - dialogue: 角色对话（如有，格式：[角色名, 情绪]:"台词内容"）
   - action: 动作描述（如有）
   - emotion: 情绪/氛围标签（紧张/温馨/悲伤/愤怒/悬疑/轻松等）
   - involved_characters: 本分镜涉及的角色名列表
   - involved_scene: 本分镜所在场景名
   - bg_sound: 背景音效建议（如有）

4. **强制要求**：
   - 分镜之间必须有明确的承接关系（continuity），不能凭空开始
   - 所有角色名称必须是具体名字，禁止泛称
   - 对话必须保留原始台词，不可改写或省略
   - 每个分镜至少包含一项有效内容（对话/动作/音效）
   - 总时长估算应合理（参考{totalSeconds}秒的总预算）

5. **输出格式**：

{
  "total_shots": N,
  "estimated_total_seconds": N,
  "scenes": [
    {
      "scene_name": "场景名",
      "scene_id": 场景ID,
      "time_of_day": "时间段",
      "shots": [
        {
          "sequence": 0,
          "time_range": "0-4s",
          "continuity": "承接上镜描述...",
          "dialogue": "[角色名, 情绪]:\"台词\"",
          "action": "动作描述",
          "emotion": "情绪标签",
          "involved_characters": ["角色A", "角色B"],
          "involved_scene": "场景名",
          "bg_sound": "环境音效"
        }
      ]
    }
  ],
  "global_settings": {
    "style_hint": "整体风格建议",
    "special_notes": "特殊注意事项"
  }
}

只返回JSON格式数据，不要包含其他说明文字。
```

---

#### A.8.2 分章节摘要提示词（ADR-9）

```
# 小说分章节摘要生成提示词
# 用途：对小说每章独立生成结构化摘要，用于后续大纲和剧本生成
# 调用位置：NovelImport.vue - generateChapterSummary() 函数
# 参数说明：
#   - {chapterIndex}: 当前章节数（从1开始）
#   - {chapterTitle}: 章节标题
#   - {chapterContent}: 当前章节正文内容
#   - {previousSummary}: 前一章的摘要（用于保持上下文连贯性，第一章时为空）
#   - {novelTitle}: 小说标题
#   - {targetStyle}: 目标风格（短剧/漫剧/预告片）

## 任务目标
为小说的第{chapterIndex}章生成一段结构化摘要。这段摘要将被后续步骤用于生成剧集大纲和分集剧本。

## 输入信息
小说标题：{novelTitle}
目标转换风格：{targetStyle}
第{chapterIndex}章：{chapterTitle}
前一章摘要：{previousSummary}（若为第一章则无前文）

当前章节内容：
{chapterContent}

## 摘要要求
1. **长度控制**：200-500字，根据章节内容密度灵活调整
2. **叙事完整性**：
   - 必须包含本章核心事件的时间线顺序
   - 标注关键出场角色（具体姓名，禁止泛称）
   - 标注关键发生的地点（具体地名）
   - 标注重要道具/物品的出现和使用
   - 记录关键情节转折点和悬念
3. **与前文的衔接**：
   - 若提供了 previousSummary，需确保本段摘要在逻辑上前承后继
   - 标注"承接上章"的关键线索（如"上章末尾XX事件在本章继续发展"）
4. **后续伏笔标记**：
   - 明确标注本章结尾留下的悬念/未解决问题
   - 标注可能影响后续剧情的新引入元素（新角色/新道具/新地点）

## 输出格式

{
  "chapter_index": {chapterIndex},
  "chapter_title": "{chapterTitle}",
  "word_count": 原文大致字数,
  "summary_text": "200-500字的叙述性摘要...",
  "key_characters": ["具体角色名列表"],
  "key_locations": ["具体地点名列表"],
  "key_items": ["关键道具/物品列表"],
  "plot_turning_points": ["关键情节转折点1", "..."],
  "cliffhangers": ["本章留下的悬念1", "..."],
  "new_introductions": {
    "characters": ["新出现的角色"],
    "locations": ["新出现的地点"],
    "items": ["新出现的道具"]
  },
  "continuity_from_previous": "与上一章的衔接说明"
}

严格要求：
1. 只返回JSON格式数据
2. summary_text 必须是流畅的叙述性文字，不要分条列举
3. key_characters/key_locations 中禁止出现泛称
4. 若本章无明显悬念，cliffhangers 可为空数组
5. 若为第一章，continuity_from_previous 填写"故事开篇，无前文"
```

---

## 九、需求完整性交叉检查

> 以下为头脑风暴过程中发现的需求问题，已全部确认并修正。

### 9.1 与魔因漫创的差异点

| 差异项 | 魔因漫创 | 本平台 | 理由 |
|--------|----------|--------|------|
| 后端技术 | Electron + Node.js | Electron + Spring Boot (JVM) | 企业级更适合 |
| 视频模型绑定 | Seedance 2.0 深度绑定 | 模型无关，抽象化接口 | ADR-6，可插拔 |
| 用户模型 | 未知 | 纯单用户桌面 | ADR-3，简化架构 |
| 分镜生成流程 | 直接生成分镜提示词 | 三步流程：解析→编辑→生成 | ADR-19，更灵活 |
| 回退调整 | 无明确机制 | pipeline_state 脏标记 | ADR-20，核心需求 |
| 面板拆分 | 五面板(Script/Role/Scene/Shot/Director) | 六模块(剧本/角色/场景/分镜/导演/S级) | Shot+Director独立，与魔因漫创对齐 |

### 9.2 提示词模板与需求不一致

| 问题 | 位置 | 修正 | 状态 |
|------|------|------|------|
| A.2 标题含"Seedance 2.0" | A.2 | 添加模型无关化说明，标注为 Seedance 格式的默认模板 | ✅已修正 |
| A.1/A.5 直接引用 {novelContent} | A.1, A.5 | 变量名改为 {inputContent}（通用），后端根据文件大小决定传入摘要或原文 | ✅已修正 |
| 缺少"剧本解析为结构化数据"提示词 | — | 新增 A.8.1 | ⚠️待编写 |
| 缺少"分章节摘要"提示词 | — | 新增 A.8.2 | ⚠️待编写 |
| A.4 引用 Seedance 分隔符 | A.4 | 保留为模型特定模板，其他模型可配置不同分隔符 | ✅可接受 |

### 9.3 数据模型修正记录

| 修正项 | 原设计 | 修正后 | 理由 |
|--------|--------|--------|------|
| 缺 episode 表 | 无 | 新增 5.3 episode 表 | 集级管理缺失 |
| 分镜单角色 | character_id: Long | 新增 5.7 storyboard_character 关联表 | 一镜多角色 |
| 场景表编号冲突 | 5.3 scene | 改为 5.5 scene | 编号修正（第二轮再调整为5.5） |
| 缺流水线状态 | 无 | 新增 5.8 pipeline_state 表 | ADR-20 回退调整 |
| 扣子字段非结构化 | extra_config JSON | 结构化为 workflow_id 等独立字段 | 可维护性 |
| 生成任务缺用途 | target_type 仅图片/视频 | 新增 generation_purpose 区分定妆图/场景图/分镜图等 | 精确区分 |
| 小说表缺转换配置 | 无 | 新增 episode_count、convert_config | ADR-9 转换流程 |
| 小说状态不全 | UPLOADED/PROCESSING/PROCESSED | 新增 SUMMARIZING/OUTLINING 中间状态 | 匹配实际流程 |
| 数据模型编号冲突 | 5.4 scene 和 5.4 item 同编号 | 修正为连续编号 5.4~5.24 | 编号规范 |
| ADR 编号缺失 | ADR-1/3/7/12 无定义 | 补充缺失的 ADR 说明 | 完整性 |

### 9.4 第二轮审查新增问题（2026-06-08）

> 第二轮需求完整性交叉检查，结合魔因漫创最新版 V0.2.8 特性对比。

#### 9.4.1 与魔因漫创的新增差异

| # | 差异项 | 魔因漫创 | 本平台 | 建议处理 |
|---|--------|----------|--------|----------|
| 1 | SKYREELS-V4 支持 | S级模块同时支持 Seedance 2.0 + SKYREELS-V4 | 仅提及 Seedance 2.0 | 在 4.5 和 A.2 中补充 SKYREELS-V4 为已验证兼容模型 |
| 2 | 首帧图 N×N 网格拼接 | S级模块有首帧图网格拼接策略（多首帧拼成网格图） | 仅说"首帧图=分镜生成图"（ADR-16） | 在 4.5 中补充"首帧图网格拼接"策略，当多个分镜首帧需同时传入时拼接为网格 |
| 3 | 五大面板拆分 | Script → Role → Scene → **Shot** → **Director**（分镜和导演独立） | 合并为"导演板块（分镜系统）" | 建议拆分为 4.4 分镜板块 + 4.5 导演板块，与魔因漫创一致 |

#### 9.4.2 需求完整性新增问题

| # | 问题 | 详情 | 建议处理 |
|---|------|------|----------|
| 4 | ADR 编号缺失 | ADR-1、ADR-3、ADR-7、ADR-12 在文档中被引用但未定义 | 在第〇节补充缺失的 ADR 定义 |
| 5 | 数据模型编号冲突 | 5.4(scene/item)、5.7(storyboard_character/asset)、5.8(pipeline_state/generation_task)、5.9(model_config/prompt_template) 均有同编号冲突 | 重新编号为 5.4~5.24 连续无重复 |
| 6 | 分章摘要缺少调用机制 | 4.1 小说→剧本流程写了"AI 分章摘要"，但未定义触发时机（自动/手动），novel 表也无字段记录摘要结果 | novel 表新增 `chapter_summaries` 字段(JSON)，明确 SUMMARIZING 状态时自动触发 |

#### 9.4.3 提示词模板新增问题

| # | 问题 | 详情 | 建议处理 |
|---|------|------|----------|
| 7 | A.1/A.5 变量名 {novelContent} 需通用化 | 上轮已标记⚠️但未修改。分章节摘要场景传入摘要，直连场景传入原文，变量名应通用化 | 将 {novelContent} 改为 {inputContent}，后端根据文件大小决定传入摘要还是原文 |
| 8 | A.6 与 A.7 场景提取重复 | A.6 角色提示词同时输出 characters+scenes+props，A.7 场景提取又独立提取场景，可能重复和不一致 | 明确策略：A.6 仅提取角色和道具，场景提取独立走 A.7；或 A.6 一次性提取所有资产后 A.7 不再独立提取场景 |

#### 9.4.4 第二轮修正状态汇总

| # | 问题 | 修正方案 | 状态 |
|---|------|----------|------|
| 1 | SKYREELS-V4 支持 | 4.5/A.2 补充 | ✅已修正 |
| 2 | 首帧图网格拼接 | 4.5 补充策略 | ✅已修正 |
| 3 | 分镜/导演面板拆分 | 4.4 拆分为 4.4 分镜+4.5 导演，后续编号顺延 | ✅已修正 |
| 4 | ADR 编号缺失 | 新增第〇节 ADR 汇总表，补充 ADR-1/3/7/12 | ✅已修正 |
| 5 | 数据模型编号冲突 | 全局重编号5.1~5.28 | ✅已修正 |
| 6 | 分章摘要调用机制 | novel 表新增 chapter_summaries 字段 | ✅已修正 |
| 7 | 变量名通用化 | {novelContent}→{inputContent} | ✅已修正 |
| 8 | 场景提取重复 | 保持不变，A.6 一次性提取角色+场景+道具，A.7 用于场景细粒度补充 | ✅保持不变 |

---

### 9.5 第三轮审查新增问题（2026-06-08）

> 第三轮需求完整性交叉检查，聚焦编号体系一致性、提示词模板补全、API规范完整性。

#### 9.5.1 编号体系结构性问题

| # | 问题 | 详情 | 严重度 |
|---|------|------|--------|
| 1 | **4.10 子节编号结构错误** | 原 4.10 预览和播放 下嵌套了 4.10.1~4.10.6 共 6 个子节，但内容（批量编辑、素材管理、项目模板、项目设置、分镜体验优化、音频同步）与"预览和播放"完全无关。根因：第二轮拆分 4.4 时后续顺延 +1，但原 4.10~4.15 的子节未同步提升为独立章节。需将 6 个子节提升为 4.11~4.16 独立章节，后续所有章节顺延至 4.28。 | 🔴严重 |
| 2 | **4.3 数据流向错误** | 第 210 行写「场景产出 → 自动流入导演板块」，按流水线应为 场景→分镜→导演→S级，应改为「自动流入**分镜板块**」 | 🟡中等 |

#### 9.5.2 提示词模板补全

| # | 问题 | 详情 | 处理 |
|---|------|------|------|
| 3 | A.8.1 剧本解析提示词待编写 | ADR-19 三步流程步骤1 的核心提示词缺失，无法支撑"剧本→结构化数据→编辑→生成分镜"流程中的第一步 | ✅已编写完整提示词 |
| 4 | A.8.2 分章摘要提示词待编写 | ADR-9 分章节摘要策略的核心提示词缺失，无法支撑小说导入→逐章摘要→大纲生成→剧本生成的完整链路 | ✅已编写完整提示词 |

#### 9.5.3 文档完整性补充

| # | 问题 | 详情 | 处理 |
|---|------|------|------|
| 5 | 缺少 API 接口规范 | 原文档仅有 ModelProvider Java 伪代码和扣子 API 说明，缺少完整的 RESTful 端点定义、统一响应格式、错误码体系、文件上传规范 | ✅新增 6.4 节（RESTful API 接口规范），含 12 类共 60+ 端点 |
| 6 | 缺少 Electron 自动更新机制 | 8.1 部署方案中未提及应用更新策略 | ✅新增 8.1.5 节（应用自动更新机制），含 electron-updater 配置、更新流程、离线支持 |

#### 9.5.4 第三轮修正状态汇总

| # | 问题 | 修正方案 | 状态 |
|---|------|----------|------|
| 1 | 4.10 子节编号结构错误 | 4.10.1~4.10.6 提升为独立章节 4.11~4.16，原 4.11~4.22 顺延至 4.17~4.28 | ✅已修正 |
| 2 | 4.3 数据流向错误 | 「导演板块」→「分镜板块」 | ✅已修正 |
| 3 | A.8.1 剧本解析提示词 | 编写完整提示词模板（含输入/输出/规则/JSON格式） | ✅已补全 |
| 4 | A.8.2 分章摘要提示词 | 编写完整提示词模板（含上下文衔接/伏笔标记/JSON格式） | ✅已补全 |
| 5 | 缺少 API 接口规范 | 新增 6.4 RESTful API 接口规范（60+端点+统一响应+错误码） | ✅已补充 |
| 6 | 缺少 Electron 自动更新 | 新增 8.1.5 应用自动更新机制（electron-updater） | ✅已补充 |


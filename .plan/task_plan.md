# 开发计划

## 目标
按照 AI 漫剧制作平台设计文档，从骨架阶段推进到第一阶段完成，同时添加中文语言支持。

## 当前状态
- 阶段 1 已完成 ✅
- 架构搭建完成度约 45%
- 核心业务逻辑完成度约 15%

## 阶段 1：前端国际化 + 后端基础设施完善
**状态**: 已完成 ✅
**开始时间**: 2026-06-10
**完成时间**: 2026-06-10

### 子任务
- [x] 前端添加 vue-i18n 国际化支持（中文）
- [x] Element Plus 中文化配置
- [x] 所有前端页面文本改为 i18n key
- [x] 后端 Service 层骨架搭建（6 大模块 + 模型配置 + 提示词模板）
- [x] 任务调度引擎配置（@Async + ThreadPoolTaskExecutor）
- [x] SSE 实时推送端点实现
- [x] 模型配置管理 API 完善
- [x] 提示词模板引擎基础实现（含变量替换渲染）
- [x] Java 17 环境安装

### 新增/修改文件清单

#### 前端 (13 个文件)
| 文件 | 操作 |
|------|------|
| `frontend/package.json` | 新增 vue-i18n@10 依赖 |
| `frontend/src/i18n.ts` | 新增 i18n 配置 |
| `frontend/src/locales/zh-CN.ts` | 新增中文语言包 |
| `frontend/src/main.ts` | 集成 i18n + Element Plus 中文 |
| `frontend/src/layouts/MainLayout.vue` | 国际化改造 |
| `frontend/src/views/project/ProjectView.vue` | 国际化改造 |
| `frontend/src/views/config/ConfigView.vue` | 国际化改造 |
| `frontend/src/views/script/ScriptView.vue` | 国际化改造 |
| `frontend/src/views/character/CharacterView.vue` | 国际化改造 |
| `frontend/src/views/scene/SceneView.vue` | 国际化改造 |
| `frontend/src/views/storyboard/StoryboardView.vue` | 国际化改造 |
| `frontend/src/views/director/DirectorView.vue` | 国际化改造 |
| `frontend/src/views/slevel/SLevelView.vue` | 国际化改造 |

#### 后端 (18 个文件)
| 文件 | 操作 |
|------|------|
| `backend/.../common/config/AsyncConfig.java` | 新增 异步任务配置 |
| `backend/.../service/ScriptService.java` | 新增 剧本服务 |
| `backend/.../service/CharacterService.java` | 新增 角色服务 |
| `backend/.../service/SceneService.java` | 新增 场景服务 |
| `backend/.../service/StoryboardService.java` | 新增 分镜服务 |
| `backend/.../service/DirectorService.java` | 新增 导演服务 |
| `backend/.../service/SLevelService.java` | 新增 S级服务 |
| `backend/.../service/ModelConfigService.java` | 新增 模型配置服务 |
| `backend/.../service/PromptTemplateService.java` | 新增 提示词模板引擎服务 |
| `backend/.../service/SseService.java` | 新增 SSE 推送服务 |
| `backend/.../controller/SseController.java` | 新增 SSE 端点 |
| `backend/.../controller/ModelConfigController.java` | 新增 模型配置 API |
| `backend/.../controller/PromptTemplateController.java` | 新增 提示词模板 API |
| `backend/.../repository/ModelConfigRepository.java` | 新增 |
| `backend/.../repository/PromptTemplateRepository.java` | 新增 |
| `backend/.../repository/NovelRepository.java` | 新增 |
| `backend/.../repository/ChapterSummaryRepository.java` | 新增 |
| `backend/pom.xml` | 修复 重复依赖 |
| `backend/.../common/response/ApiResponse.java` | 修复 record→class |
| `backend/.../controller/ProjectController.java` | 修复 switch 语法 |
| `backend/.../repository/StoryboardRepository.java` | 修复 无效方法 |

### 阻塞问题
- ~~Java 8 环境不兼容~~ → 已安装 Java 17 (Eclipse Temurin 17.0.19)

### 下一步
进入阶段 2：剧本模块实现

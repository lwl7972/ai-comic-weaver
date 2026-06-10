# 研究发现

## 前端现状
- Vue 3.5 + Element Plus 2.9 + Vite 6 + TypeScript 5.6
- 已有 8 个视图页面，均为占位状态
- MainLayout.vue 含顶部步进器 + 深色侧边栏
- http.ts 已封装 Axios（含 Electron 端口适配）
- types/index.ts 定义 28 个数据实体接口

## 后端现状
- Spring Boot 3.3.5 + Spring Data JPA + SQLite
- 28 个 JPA 实体 + 8 个 Repository
- 2 个 Controller（ProjectController, AppConfigController）
- 无 Service 层
- 无异步任务配置
- 无 SSE 端点

## 国际化方案
- 使用 vue-i18n v10（与 Vue 3 兼容）
- Element Plus 内置中文语言包
- 语言文件放在 frontend/src/locales/

## 待确认
- 是否需要支持多语言切换，还是固定中文？（默认固定中文）

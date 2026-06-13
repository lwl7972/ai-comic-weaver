# AI 漫剧制作平台 (AI Comic Weaver)

> 基于六大模块流水线（剧本 → 角色 → 场景 → 分镜 → 导演 → S级）的 AI 漫剧创作桌面应用

**仓库地址**: https://github.com/lwl7972/ai-comic-weaver

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.x + Spring Data JPA + SQLite |
| 前端 | Vue 3 + Element Plus + Vite + Pinia |
| 桌面端 | Electron（内嵌 JVM，spawn 子进程） |
| 实时通信 | SSE（Server-Sent Events） |
| 视频合成 | FFmpeg |

## 项目结构

```
atomgit/
├── frontend/          # Vue 3 前端 (Vite)
├── backend/           # Spring Boot 后端 (Maven)
├── electron/          # Electron 桌面端
├── assets/            # 品牌资源 (Logo等)
└── docs/              # 设计文档
```

## 快速开始

### 环境要求
- **JDK 17+**
- **Node.js 18+**
- **Maven 3.8+**
- **FFmpeg** (视频合成)

### 开发模式

```bash
# 后端
cd backend && mvn spring-boot:run

# 前端
cd frontend && npm install && npm run dev

# Electron 桌面端
npm run dev:electron
```

## 六大模块流水线

1. **📝 剧本模块** - 小说导入 / AI 大纲生成 / 剧本创作 / 分集管理
2. **🎭 角色模块** - AI 资产提取 / 6层身份锚点 / 定妆图管理 / 角色圣经
3. **🌄 场景模块** - 场景创建 / 四视图生成 / 场景风格一致性
4. **🎬 分镜模块** - 三步分镜流程(解析→编辑→生成) / 专业电影级参数
5. **🎥 导演模块** - 整集视频生成 / 单镜头回退 / FFmpeg拼接
6. **⭐ S级模块** - 成片合成 / 导出 / 水印

## 设计文档

详见 [6a25944ac9764b6c22a2e8c1_2026-06-07-ai-comic-platform-design.md](./6a25944ac9764b6c22a2e8c1_2026-06-07-ai-comic-platform-design.md)

## License

MIT

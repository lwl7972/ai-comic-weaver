# GitHub 推送和发布指南

## ✅ 已完成

1. **代码已推送到 GitHub**
   - 分支：`main`
   - 最新提交：`adf25b5` - fix: 自动更新功能修复和优化
   - 仓库地址：https://github.com/lwl7972/ai-comic-weaver

2. **已配置自动化发布流程**
   - 工作流文件：`.github/workflows/release.yml`
   - 触发条件：推送版本 tag (如 `v0.1.0`, `v0.1.1`)
   - 自动执行：构建前端、构建后端、打包 Electron、发布 GitHub Releases

## 📦 执行发布（两种方式）

### 方式一：使用 GitHub Actions（推荐）

```bash
# 1. 修改版本号
cd ai-comic-weaver
# 编辑 package.json，将 "version": "0.1.0" 改为 "version": "0.1.1"

# 2. 提交版本变更
git add package.json
git commit -m "chore: bump version to 0.1.1"
git push origin main

# 3. 创建版本 tag 并推送（触发自动发布）
git tag v0.1.1
git push origin v0.1.1
```

**自动执行流程**：
1. GitHub Actions 检测到 tag 推送
2. 自动运行 `release.yml` 工作流
3. 在 GitHub-hosted runner 上执行：
   - 安装 Node.js 20, Java 17, Maven
   - 构建后端 (`mvn package`)
   - 构建前端 (`npm run build`)
   - 打包 Electron (`npx electron-builder`)
   - 创建 GitHub Release 并上传安装包
4. 发布完成后，用户可以在 Releases 页面下载安装

### 方式二：本地打包后手动发布

**前提条件**：
- Windows 环境（或使用 Windows VM/容器）
- JDK 17 已安装
- Maven 3.8+ 已安装
- Node.js 18+ 已安装

```bash
cd ai-comic-weaver

# 1. 构建后端
cd backend
mvn clean package -DskipTests

# 重命名 jar 文件（Electron 期望此名称）
cp target/ai-comic-weaver-0.1.0.jar target/ai-comic-platform.jar

# 2. 构建前端
cd ../frontend
npm install
npm run build

# 3. 打包 Electron
cd ..
npm install
npm run pack:win  # 生成 release/AI 漫剧_Setup_0.1.0.exe

# 4. 手动发布到 GitHub
# 访问 https://github.com/lwl7972/ai-comic-weaver/releases/new
# - Tag version: v0.1.0
# - Release title: AI 漫剧 v0.1.0
# - 上传文件:
#   - release/AI 漫剧_Setup_0.1.0.exe
#   - release/latest.yml
#   - release/*.blockmap (如果有)
```

## 🔍 验证发布

### 检查 GitHub Actions 状态
https://github.com/lwl7972/ai-comic-weaver/actions

### 查看 Releases
https://github.com/lwl7972/ai-comic-weaver/releases

### 测试自动更新

1. 下载并安装发布的版本
2. 启动应用
3. 进入"配置中心" → "软件更新"
4. 点击"检测更新"
5. 如有新版本，应显示更新提示

## 🐛 当前环境问题

当前工作区 (`/workspace`) 缺少以下构建工具：
- ❌ Maven (后端构建)
- ⚠️ Node.js 依赖已安装，但前端有 TypeScript 编译错误待修复

**建议**：使用 GitHub Actions 进行发布，无需本地构建环境。

## 📝 下一步

1. **立即发布 v0.1.0**
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

2. **观察 GitHub Actions 执行**
   https://github.com/lwl7972/ai-comic-weaver/actions

3. **验证 Release 文件**
   确保包含：
   - `AI 漫剧_Setup_0.1.0.exe`
   - `latest.yml`
   - `*.blockmap`

4. **测试自动更新**
   - 下载刚发布的安装包
   - 安装后运行
   - 点击"检测更新"验证功能

## 📌 注意事项

- **GitHub Token**: `release.yml` 使用 `GITHUB_TOKEN` 权限，自动包含在 Actions 中
- **首次发布**：发布 v0.1.0 后，后续版本才能正常检测更新
- **版本号规则**：必须遵循语义化版本（Major.Minor.Patch）
- **Release 类型**：必须为正式 Release（非 Draft，非 Pre-release）

---

**文档创建时间**：2026-06-13  
**最后更新**：2026-06-13

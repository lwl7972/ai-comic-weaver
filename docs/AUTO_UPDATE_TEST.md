# 自动更新功能测试清单

## 前置条件

- [ ] 已安装 JDK 17+
- [ ] 已安装 Node.js 18+
- [ ] 已安装 Maven 3.8+
- [ ] GitHub CLI (`gh`) 已安装并登录
- [ ] 本地有可用的 FFmpeg（可选，用于视频合成功能测试）

## 测试步骤

### 阶段 1: 打包应用

```bash
# 1. 确保版本号正确
cd /workspace/ai-comic-weaver
cat package.json | grep version  # 应显示 "version": "0.1.0"

# 2. 构建后端
cd backend
mvn clean package -DskipTests

# 3. 构建前端
cd ../frontend
npm install
npm run build

# 4. 打包 Electron
cd ..
npm install
npm run pack:win  # 或 npm run electron:build
```

**预期结果**：
- `release/` 目录下生成 `AI 漫剧_Setup_0.1.0.exe`
- `release/latest.yml` 文件存在

### 阶段 2: 发布到 GitHub

#### 方式 A: 手动发布（推荐初次测试）

1. 访问 https://github.com/lwl7972/ai-comic-weaver/releases/new

2. 填写信息：
   - **Tag version**: `v0.1.0`
   - **Release title**: `AI 漫剧 v0.1.0`
   - **Description**: 初始版本发布

3. 上传文件：
   - `release/AI 漫剧_Setup_0.1.0.exe`
   - `release/latest.yml`
   - `release/latest.yml.blockmap` (如果有)

4. 点击 "Publish release"

#### 方式 B: 使用 GitHub CLI

```bash
# 创建 Release
gh release create v0.1.0 \
  --title "AI 漫剧 v0.1.0" \
  --generate-notes \
  release/AI 漫剧_Setup_0.1.0.exe \
  release/latest.yml \
  release/*.blockmap
```

### 阶段 3: 安装并测试更新

1. **安装旧版本**（模拟用户场景）
   - 下载并安装 `AI 漫剧_Setup_0.1.0.exe`
   - 启动应用

2. **发布新版本**
   ```bash
   # 修改版本号
   # package.json: "version": "0.1.1"
   # electron-builder.json: 不需要修改，自动读取 package.json

   # 重新打包
   npm run pack:win

   # 创建新 Release
   gh release create v0.1.1 \
     --title "AI 漫剧 v0.1.1" \
     --generate-notes \
     release/AI 漫剧_Setup_0.1.1.exe \
     release/latest.yml
   ```

3. **测试更新功能**
   - 打开已安装的 AI 漫剧应用
   - 进入"配置中心"
   - 查看"软件更新"卡片
   - 点击"检测更新"

**预期结果**：
- ✅ 显示"有新版本 v0.1.1"
- ✅ 显示更新包大小
- ✅ 点击"立即更新"开始下载
- ✅ 下载进度显示
- ✅ 下载完成后显示"立即重启安装"
- ✅ 点击后应用重启并完成安装

## 常见问题排查

### 问题 1: 检测更新按钮无响应

**检查点**：
```bash
# 1. 确认是打包后的应用（不是开发模式）
# 应用标题栏应显示"AI 漫剧"而非"AI 漫剧 (Development)"

# 2. 查看开发者工具 Console
# F12 → Console → 查找 [Updater] 相关日志

# 3. 检查网络连接
# 访问 https://github.com/lwl7972/ai-comic-weaver/releases
```

### 问题 2: 显示"检测失败"

**可能原因**：
- GitHub API 访问受限
- Release 缺少 `latest.yml` 文件
- 版本号格式不正确（必须是语义化版本）

**解决方法**：
```bash
# 1. 验证 Release 文件完整性
gh release view v0.1.0 --json assets
# 应包含：AI 漫剧_Setup_0.1.0.exe, latest.yml

# 2. 检查 latest.yml 内容
cat release/latest.yml
# 应包含：version, files, path, sha512, size

# 3. 手动测试 GitHub API
curl -s https://api.github.com/repos/lwl7972/ai-comic-weaver/releases/latest | jq .tag_name
```

### 问题 3: 下载进度卡住

**可能原因**：
- 网络速度慢
- 防火墙拦截
- 安装包被杀毒软件阻止

**解决方法**：
1. 检查下载目录权限
2. 临时关闭杀毒软件
3. 尝试手动下载安装包

## 验证检查点

### Electron 主进程日志

启动应用后，在开发工具 Console 中应看到：

```text
[Electron] Starting JVM backend on port 18081...
[Electron] Backend started successfully
[Updater] Update available: 0.1.1
```

### 更新流程日志

```text
[Updater] Download: 0.0%
[Updater] Download: 25.3%
[Updater] Download: 50.1%
[Updater] Download: 75.8%
[Updater] Download: 100.0%
[Updater] Update downloaded: 0.1.1
```

### 文件验证

安装完成后，检查以下文件：

```bash
# Windows 默认安装路径
C:\Program Files\AI 漫剧\
├── AI 漫剧.exe
├── app-update.yml      # ← 更新配置（关键）
├── resources\
│   └── app\
│       └── package.json  # ← 版本号应已更新
```

## 自动化测试脚本

```powershell
# test-auto-update.ps1
# 自动化测试自动更新功能

$ErrorActionPreference = "Stop"

Write-Host "=== 自动更新功能测试 ===" -ForegroundColor Cyan

# 1. 检查环境
Write-Host "`n[1/5] 检查环境..." -ForegroundColor Yellow
node --version
java --version
mvn --version

# 2. 构建
Write-Host "`n[2/5] 构建应用..." -ForegroundColor Yellow
cd $PSScriptRoot
npm run build:frontend
npm run build:backend

# 3. 打包
Write-Host "`n[3/5] 打包 Electron..." -ForegroundColor Yellow
npm run pack:win

# 4. 验证输出
Write-Host "`n[4/5] 验证输出文件..." -ForegroundColor Yellow
$installer = Get-ChildItem -Path .\release\*.exe | Select-Object -First 1
if ($installer) {
    Write-Host "✓ 安装包已生成：$($installer.Name)" -ForegroundColor Green
} else {
    throw "安装包生成失败"
}

# 5. 发布到 GitHub
Write-Host "`n[5/5] 发布到 GitHub (跳过，需手动执行)" -ForegroundColor Yellow
Write-Host "请访问：https://github.com/lwl7972/ai-comic-weaver/releases/new" -ForegroundColor Cyan

Write-Host "`n=== 测试完成 ===" -ForegroundColor Green
```

## 回退方案

如果自动更新失败，用户可手动下载：

1. 访问 https://github.com/lwl7972/ai-comic-weaver/releases
2. 下载最新版本的安装包
3. 手动安装覆盖旧版本

## 后续优化建议

1. **增量更新**: 使用 differential updates 减少下载体积
2. **代码签名**: 购买 EV 证书避免 Windows SmartScreen 警告
3. **更新日志**: 自动生成 changelog 展示给用户
4. **灰度发布**: 先发布给测试用户，稳定后全量发布
5. **更新统计**: 收集更新成功率、失败原因等指标

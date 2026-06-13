# 自动更新功能指南

## 工作原理

AI 漫剧制作平台使用 `electron-updater` 实现自动更新功能，更新源为 **GitHub Releases**。

### 更新流程
```
1. 用户点击"检测更新"按钮
   ↓
2. 前端通过 IPC 调用主进程的 checkForUpdate
   ↓
3. 主进程访问 GitHub API 检查最新版本
   ↓
4. 发现有更新 → 显示新版本信息
   ↓
5. 用户点击"立即更新" → 下载安装包
   ↓
6. 下载完成 → 提示重启安装
```

## 使用前提

### 1. 必须是已打包的 Electron 应用

自动更新功能**仅在打包后的应用中生效**，开发模式下会提示错误。

**原因**：electron-updater 需要应用的 `app-update.yml` 配置文件和正确的 App ID

**验证方法**：
```bash
# 打包应用
npm run electron:build

# 安装生成的安装包
./release/AI 漫剧_Setup_0.1.0.exe  # Windows
```

### 2. GitHub Release 必须包含正确的文件

在 GitHub 仓库发布新版本时，需要包含：

- **安装包文件**: `AI 漫剧_Setup_0.1.0.exe`
- **latest.yml**: electron-builder 自动生成，包含版本、哈希等信息

**发布步骤**：
```bash
# 1. 修改 package.json 版本号
{
  "name": "ai-comic-weaver",
  "version": "0.1.1"  # ← 修改这里
}

# 2. 打包
npm run build:frontend
npm run build:backend
npm run pack:win  # 或使用 electron-builder

# 3. 上传到 GitHub Releases
# 访问 https://github.com/lwl7972/ai-comic-weaver/releases/new
# 创建新 tag (v0.1.1)，上传生成的安装包
```

## 当前配置

### electron-builder.json
```json
{
  "appId": "com.aicomic.platform",
  "productName": "AI 漫剧",
  "publish": {
    "provider": "github",
    "owner": "lwl7972",
    "repo": "ai-comic-weaver",
    "releaseType": "release"
  }
}
```

### Electron main.js
- 已配置 `autoUpdater.setFeedURL()` 明确指向 GitHub
- 已实现完整的 IPC handlers (`check-for-update`, `download-update`, `quit-and-install`)
- 已添加事件监听 (`update-available`, `download-progress`, `update-downloaded`, `error`)

## 故障排查

### 问题 1: 点击"检测更新"没有反应

**可能原因**：
- 开发模式下运行（未打包）
- 网络无法访问 GitHub
- 当前已是最新版本

**解决方法**：
1. 确保运行的是打包后的应用
2. 检查网络连接
3. 查看开发者工具 Console 的输出

### 问题 2: 显示"检测失败"错误

**查看错误日志**：
在 Electron 应用中按 `F12` 打开开发者工具，查看 Console 输出

**常见错误**：
```text
Cannot update at this moment: app is not running from appData
→ 应用未正确安装或打包

version is the same as current version
→ 当前已是最新版本

ETIMEDOUT / ECONNREFUSED
→ 无法访问 GitHub，检查网络
```

### 问题 3: 下载失败

**可能原因**：
- GitHub 下载被防火墙拦截
- Release 文件不完整（缺少 latest.yml）

**解决方法**：
1. 确保 GitHub Release 包含 `latest.yml` 文件
2. 尝试手动下载安装包
3. 检查防火墙设置

## 手动测试更新

### 步骤 1: 打包当前版本
```bash
cd /workspace/ai-comic-weaver
npm run build:frontend
npm run build:backend
npm run pack:win
```

### 步骤 2: 安装旧版本
在 GitHub Release 下载一个旧版本（如 v0.1.0）并安装

### 步骤 3: 创建新版本 Release
1. 修改 `package.json` 版本号：`"version": "0.1.1"`
2. 重新打包
3. 在 GitHub 创建 v0.1.1 Release
4. 上传新生成的安装包

### 步骤 4: 测试更新
打开已安装的旧版本应用：
1. 进入"配置中心" → "软件更新"
2. 点击"检测更新"
3. 应该显示新版本 v0.1.1
4. 点击"立即更新"下载
5. 下载完成后点击"立即重启安装"

## 开发模式下的替代方案

开发模式下自动更新不可用，建议使用：

### 方案 1: 手动下载
```bash
# 直接访问 GitHub Releases
https://github.com/lwl7972/ai-comic-weaver/releases

# 下载最新安装包并手动安装
```

### 方案 2: 开发环境更新提示
修改前端代码，在非打包环境下显示提示：

```vue
<div v-if="!isPackaged" class="dev-hint">
  🚧 开发模式：自动更新不可用
  <a href="https://github.com/lwl7972/ai-comic-weaver/releases" target="_blank">
    前往 GitHub 下载最新版
  </a>
</div>
```

## 注意事项

1. **版本号规则**: 使用语义化版本（Major.Minor.Patch），如 0.1.0
2. **Release 类型**: 必须发布为正式 Release（非 Draft、非 Pre-release）
3. **文件完整性**: 确保上传 `latest.yml` 和 `.blockmap` 文件
4. **签名证书**: Windows 平台建议使用代码签名证书（可选）
5. **网络访问**: 确保用户可访问 `api.github.com` 和 `github.com`

## 相关代码位置

- **主进程**: `electron/main.js` (setupAutoUpdater, IPC handlers)
- **预加载脚本**: `electron/preload.js` (electronAPI expose)
- **前端页面**: `frontend/src/views/config/ConfigView.vue` (更新 UI)
- **构建配置**: `electron-builder.json` (publish configuration)

## 参考资料

- electron-updater 文档：https://www.electron.build/auto-update
- GitHub Releases：https://docs.github.com/en/repositories/releasing-projects-on-github
- electron-builder 配置：https://www.electron.build/configuration/configuration

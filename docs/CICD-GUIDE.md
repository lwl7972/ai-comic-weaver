# GitCode CI/CD 配置指南

## 快速开启（3 步）

### 步骤 1：开启 Runner

1. 打开仓库页面：**https://gitcode.com/aicomic/aicomic**
2. 点击顶部菜单 **「设置」** → **「CI/CD」**
3. 找到 **「Runners」** 区域
4. 选择以下方式之一：

| 方式 | 操作 | 适用场景 |
|------|------|----------|
| **共享 Runner** | 点击「启用共享 Runner」✅ | 推荐，无需自建 |
| **自有 Runner** | 参考[官方文档](https://docs.gitcode.com)注册 Runner | 特殊环境/私有网络 |

> 如果没有「共享 Runner」选项，可能需要：
> - 确认账号已开通 DevOps 服务
> - 联系 GitCode 客服开通 CI/CD 功能

---

### 步骤 2：配置 CI/CD 变量

1. 在 **「CI/CD 设置」** 页面找到 **「变量」** 区域
2. 点击 **「添加变量」**：

| 变量名 | 值 | 说明 |
|--------|-----|------|
| `GITCODE_TOKEN` | `upmkdu_x9BChx2NCwBhGUmi-` | 您的 Personal Access Token |

3. 勾选 **「受保护变量」** 和 **「隐藏变量」** ✅
4. 点击 **「保存变量」**

---

### 步骤 3：推送代码触发构建

```bash
# 推送任意提交到 main 分支即可触发 CI
git commit --allow-empty -m "ci: trigger pipeline"
git push origin main
```

---

## 验证流水线状态

1. 打开仓库 → 点击左侧 **「CI/CD」** → **「流水线」**
2. 应该看到正在运行（🔄）或已完成（✅）的流水线
3. 点击进入可查看各阶段日志：

```
┌─────────────────────────────────────────────┐
│  Pipeline #1                                │
│                                             │
│  ✅ build-frontend   (Node 20 构建前端)      │
│  ✅ build-backend    (Maven + JDK17 构建)    │
│  📦 package-windows  (Electron 打包 .exe)    │
│  ⏸️ create-release   (手动触发创建 Release)  │
└─────────────────────────────────────────────┘
```

---

## 本地打包（立即可用）

如果 CI/CD 暂时无法使用，可用本地脚本打包：

### Windows PowerShell

```powershell
# 一键打包 Windows 版本
.\scripts\build-all.ps1 win

# 打包所有平台
.\scripts\build-all.ps1 all
```

### macOS / Linux

```bash
# 一键打包
./scripts/build-all.sh win

# 或打包 macOS 版
./scripts/build-all.sh mac
```

---

## 常见问题排查

### Q: 流水线不触发？

**检查项：**
- [ ] `.gitlab-ci.yml` 是否在仓库根目录
- [ ] 文件名是否正确（注意是下划线 `_` 不是连字符 `-`）
- [ ] 是否推送到 `main` 分支（其他分支需修改 `only:` 规则）

### Q: Runner 找不到？

**解决方案：**
```yaml
# 移除 tags 限制（如果有）
package-windows:
  # tags: [docker]  ← 删除或注释这行
```

### Q: 构建失败？

**常见原因：**
1. **依赖安装失败** — 检查网络/镜像源配置
2. **内存不足** — Maven/JVM 需要 ≥2GB RAM
3. **Electron 下载慢** — 配置镜像：

```yaml
variables:
  ELECTRON_MIRROR: "https://npmmirror.com/mirrors/electron/"
```

### Q: 如何手动触发 Release？

方法 1：在 CI/CD 页面的流水线详情中点击 **「运行」** 按钮
方法 2：打一个 tag 推送：

```bash
git tag v0.1.0
git push origin v0.1.0
```

---

## CI/CD 流程图

```
Git Push (main)
       │
       ▼
┌──────────────┐    ┌──────────────┐
│ Stage: Build │    │              │
│              │    │              │
│ ✅ frontend  │    │ ✅ backend   │
│ (Vue+Vite)   │    │ (Maven+JAR)  │
└──────┬───────┘    └──────┬───────┘
       │                   │
       └───────┬───────────┘
               ▼
     ┌──────────────────┐
     │ Stage: Package    │
     │                   │
     │ 📦 Electron Win   │
     │ (.exe + NSIS)     │
     └─────────┬─────────┘
               │
               ▼
     ┌──────────────────┐
     │ Stage: Release    │
     │  ⏸️ Manual        │
     │  (创建版本发布)    │
     └──────────────────┘
```

---

## 相关文件

| 文件 | 用途 |
|------|------|
| `.gitlab-ci.yml` | **GitCode 主 CI/CD 配置**（4阶段流水线） |
| `.github/workflows/release.yml` | GitHub Actions 备用方案 |
| `scripts/build-all.ps1` | Windows 本地一键打包 |
| `scripts/build-all.sh` | Linux/macOS 本地打包 |
| `electron-builder.json` | Electron 打包详细配置 |

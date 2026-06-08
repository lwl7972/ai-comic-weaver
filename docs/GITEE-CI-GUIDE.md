# Gitee Go CI/CD 配置指南

## 快速开启（3 步）

### 步骤 1：开启 Gitee Go 流水线

1. 打开仓库：**https://gitee.com/aiprojects_1/ai-comic-weaver**
2. 点击顶部菜单 **「服务」**
3. 找到 **「Gitee Go - 持续集成/部署」**
4. 点击 **「启用」**

> 如果没有看到 Gitee Go 选项，需要先在 Gitee 开发者设置中开通该服务

---

### 步骤 2：配置流水线变量

1. 在 **Gitee Go** 页面，找到 **「变量管理」**
2. 点击 **「新建变量」**：

| 变量名 | 值 | 类型 |
|--------|-----|------|
| `GITEE_TOKEN` | `292a18e3bdd03e83f231d5e6129fcc5` | 密码（加密） |

3. 保存 ✅

---

### 步骤 3：触发构建

代码已包含 `.gitee/pipelines/pipeline.yml`，推送后自动触发：

```bash
git push origin main
```

或在 Gitee Go 页面点击 **「运行」** 手动触发。

---

## 流水线说明

```
Push to main
    │
    ▼
┌─────────────────┐   ┌─────────────────┐
│ Stage 1:        │   │ Stage 2:        │
│ 构建前端         │   │ 构建后端         │
│ (Node 20)       │   │ (JDK 17 + Maven) │
└────────┬────────┘   └────────┬────────┘
         │                     │
         └──────────┬──────────┘
                    ▼
          ┌──────────────────┐
          │ Stage 3: 打包      │
          │ Electron Windows  │
          │ (.exe NSIS)       │
          └────────┬─────────┘
                   ▼
          ┌──────────────────┐
          │ Stage 4: 发布     │
          │ ⏸️ 手动触发        │
          │ 创建 Release      │
          └──────────────────┘
```

---

## 本地打包（立即可用）

```powershell
cd d:\AI\atomgit
.\scripts\build-all.ps1 win    # Windows .exe
.\scripts\build-all.ps1 dev    # 仅构建不打包
```

---

## 相关文件

| 文件 | 说明 |
|------|------|
| `.gitee/pipelines/pipeline.yml` | **Gitee Go 主流水线配置** |
| `.github/workflows/release.yml` | GitHub Actions 备用 |
| `.gitlab-ci.yml` | GitCode 备用 |
| `scripts/build-all.ps1` | Windows 本地一键打包 |
| `electron-builder.json` | Electron 打包参数 |

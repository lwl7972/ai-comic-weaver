# ============================================================
# AI漫剧制作平台 - Windows 一键打包脚本
# 用法:
#   .\scripts\build-all.ps1 win     # 打包 Windows 版
#   .\scripts\build-all.ps1 mac     # 打包 macOS 版(需在 Mac 上运行)
#   .\scripts\build-all.ps1 all     # 全平台打包
#   .\scripts\build-all.ps1 dev     # 开发模式(不打包,只构建)
# ============================================================

param(
    [ValidateSet("win", "mac", "all", "dev")]
    [string]$Target = "win"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path | Split-Path -Parent

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  AI漫剧制作平台 - 一键构建工具" -ForegroundColor Cyan
Write-Host "  Target: $Target" -ForegroundColor Yellow
Write-Host "  Time:  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor DarkGray
Write-Host "========================================`n" -ForegroundColor Cyan

# 检查 Node.js
Write-Host "[1/5] 检查环境..." -ForegroundColor Green
try {
    $nodeVersion = node --version
    Write-Host "  ✅ Node.js: $nodeVersion" -ForegroundColor Green
} catch {
    Write-Host "  ❌ 未安装 Node.js，请先安装: https://nodejs.org/" -ForegroundColor Red
    exit 1
}

# 检查 Java (后端需要)
try {
    $javaVersion = java -version 2>&1 | Select-String "version" | ForEach-Object { $_ -replace '.*"' }
    Write-Host "  ✅ Java: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "  ⚠️ 未安装 Java，跳过后端构建" -ForegroundColor Yellow
}

Write-Host ""

# Step 1: 安装前端依赖
Write-Host "[2/5] 安装前端依赖..." -ForegroundColor Green
Push-Location "$ProjectRoot\frontend"
npm ci --registry=https://registry.npmmirror.com
if ($LASTEXITCODE -ne 0) { throw "前端依赖安装失败" }
Pop-Location
Write-Host "  ✅ 前端依赖安装完成`n" -ForegroundColor Green

# Step 2: 构建前端
Write-Host "[3/5] 构建前端..." -ForegroundColor Green
Push-Location "$ProjectRoot\frontend"
npm run build
if ($LASTEXITCODE -ne 0) { throw "前端构建失败" }
Pop-Location
Write-Host "  ✅ 前端构建完成 ($(Get-ChildItem "$ProjectRoot\frontend\dist" -Recurse -File).Count 个文件)`n" -ForegroundColor Green

# Step 3: 构建后端（如果安装了 Java + Maven）
$backendBuilt = $false
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    Write-Host "[4/5] 构建后端..." -ForegroundColor Green
    Push-Location "$ProjectRoot\backend"
    mvn clean package -DskipTests -q
    if ($LASTEXITCODE -eq 0) {
        $backendBuilt = $true
        $jarFile = Get-ChildItem "$ProjectRoot\backend\target\*.jar" | Select-Object -First 1
        Write-Host "  ✅ 后端 JAR: $($jarFile.Name)`n" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️ 后端构建失败，继续打包前端版本...`n" -ForegroundColor Yellow
    }
    Pop-Location
} else {
    Write-Host "[4/5] 跳过后端构建 (未检测到 Maven)`n" -ForegroundColor DarkGray
}

# Dev 模式：只构建不打包
if ($Target -eq "dev") {
    Write-Host "`n🎉 开发模式构建完成！`n" -ForegroundColor Cyan
    Write-Host "  前端: cd frontend && npm run dev" -ForegroundColor White
    if ($backendBuilt) {
        Write-Host "  后端: cd backend && mvn spring-boot:run" -ForegroundColor White
    }
    Write-Host ""
    return
}

# Step 4: Electron 打包
Write-Host "[5/5] 打包 Electron..." -ForegroundColor Green

# 检查是否安装了 electron-builder
if (-not (Get-Command electron-builder -ErrorAction SilentlyContinue)) {
    Write-Host "  安装 electron-builder..." -ForegroundColor Yellow
    npm install -g electron-builder@latest
}

Push-Location $ProjectRoot

# 设置 Electron 镜像（国内加速）
$env:ELECTRON_MIRROR = "https://npmmirror.com/mirrors/electron/"

switch ($Target) {
    "win" {
        Write-Host "  打包 Windows 版本..." -ForegroundColor Yellow
        electron-builder --win --publish never
            --config.productName="AI漫剧"
            --config.appId="com.aicomic.platform"
            --config.directories.output="release/win"
            --config.win.target="nsis"
            --config.win.icon="assets/logo.png"
            --config.nsis.oneClick=$false
            --config.nsis.allowToChangeInstallationDirectory=$true
            --config.nsis.createDesktopShortcut=$true
    }
    "mac" {
        Write-Host "  打包 macOS 版本..." -ForegroundColor Yellow
        electron-builder --mac --publish never
            --config.directories.output="release/mac"
            --config.mac.target="dmg"
            --config.mac.icon="assets/logo.png"
    }
    "all" {
        Write-Host "  打包全平台..." -ForegroundColor Yellow
        electron-builder --win --mac --publish never
            --config.productName="AI漫剧"
            --config.appId="com.aicomic.platform"
            --config.win.target="nsis"
            --config.mac.target="dmg"
    }
}

if ($LASTEXITCODE -eq 0) {
    Pop-Location
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "  🎉 打包完成！" -ForegroundColor Green
    
    # 列出产物
    $releaseDir = "$ProjectRoot\release"
    if (Test-Path $releaseDir) {
        Write-Host "`n  📦 输出文件:`n" -ForegroundColor Yellow
        Get-ChildItem $releaseDir -Recurse -File | Where-Object { $_.Extension -match '\.(exe|dmg|yml|blockmap)$' } | ForEach-Object {
            $size = [math]::Round($_.Length / 1MB, 2)
            Write-Host "    • $($_.FullName.Replace($ProjectRoot, '.'))  ($size MB)" -ForegroundColor White
        }
    }
    
    Write-Host "`n  📁 输出目录: $releaseDir" -ForegroundColor DarkGray
    Write-Host "========================================`n" -ForegroundColor Cyan
} else {
    Pop-Location
    Write-Host "`n❌ 打包失败，请检查上方日志" -ForegroundColor Red
    exit 1
}

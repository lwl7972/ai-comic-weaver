# ============================================================
# AI漫剧制作平台 - 本地一键打包脚本 (Windows PowerShell)
# 用法: .\scripts\build-all.ps1 [win|mac|all]
# ============================================================

param(
    [ValidateSet("win", "mac", "all")]
    [string]$Platform = "win"
)

$ErrorActionPreference = "Stop"
$VERSION = "0.1.0"
$BuildDate = Get-Date -Format "yyyyMMdd"
$Timestamp = "$BuildDate-$(Get-Date -Format 'HHmm')"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  AI漫剧制作平台 - 一键构建 v$VERSION" -ForegroundColor Cyan
Write-Host "  平台: $Platform | 时间: $Timestamp" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

$RootDir = Split-Path -Parent $PSScriptRoot

# 1. 构建前端
Write-Host ""
Write-Host "[1/3] 🔨 构建前端 (Vue 3 + Vite)..." -ForegroundColor Yellow
Push-Location "$RootDir\frontend"
if (-not (Test-Path "node_modules")) {
    npm install --registry=https://registry.npmmirror.com
}
npm run build
Pop-Location
Write-Host "✅ 前端构建完成" -ForegroundColor Green

# 2. 构建后端 JAR
Write-Host ""
Write-Host "[2/3] ☕ 构建后端 (Spring Boot)..." -ForegroundColor Yellow
Push-Location "$RootDir\backend"
mvn clean package -DskipTests -q
$JarFile = Get-ChildItem "target\*.jar" | Select-Object -First 1
Write-Host "✅ 后端 JAR: $($JarFile.Name)" -ForegroundColor Green
Pop-Location

# 3. 复制 JAR
Copy-Item "$RootDir\backend\target\*.jar" "$RootDir\backend\target\" -Force

# 4. 安装 electron-builder
Write-Host ""
Write-Host "[3/3] 📦 打包 Electron 桌面应用..." -ForegroundColor Yellow
Push-Location $RootDir

# 确保前端已构建
Push-Location "$RootDir\frontend"
npm install --registry=https://registry.npmmirror.com 2>$null | Out-Null
npm run build
Pop-Location

# 全局安装 electron-builder
$npxList = npx electron-builder --version 2>$null
if (-not $npxList) {
    Write-Host "  → 安装 electron-builder..." -ForegroundColor DarkGray
    npm install -g electron-builder
}

if ($Platform -eq "win" -or $Platform -eq "all") {
    Write-Host "  → 构建 Windows NSIS 安装包..." -ForegroundColor White
    
    npx electron-builder --win --publish never `
        -c.productName="AI漫剧" `
        -c.appId="com.aicomic.platform" `
        -c.directories.output="release\win-$Timestamp" `
        -c.win.target=nsis `
        -c.win.icon="assets\logo.png" `
        -c.nsis.oneClick=$false `
        -c.nsis.allowToChangeInstallationDirectory=$true `
        -c.nsis.installerIcon="assets\logo.png" `
        -c.nsis.createDesktopShortcut=$true
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Windows 安装包: release\win-$Timestamp\" -ForegroundColor Green
        
        # 显示文件大小
        Get-ChildItem "release\win-$Timestamp\*.exe" | ForEach-Object {
            $sizeKB = [math]::Round($_.Length / 1MB, 2)
            Write-Host "  📄 $($_.Name) ($sizeKB MB)" -ForegroundColor Cyan
        }
    }
}

Pop-Location

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  🎉 构建完成!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Cyan

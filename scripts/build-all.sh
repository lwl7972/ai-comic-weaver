#!/bin/bash
# ============================================================
# AI漫剧制作平台 - 本地一键打包脚本 (macOS/Linux)
# 用法: ./scripts/build-all.sh [win|mac|all]
# ============================================================

set -e

PLATFORM=${1:-"win"}
VERSION="0.1.0"
BUILD_DATE=$(date +%Y%m%d)
TIMESTAMP="${BUILD_DATE}-$(date +%H%M)"

echo "============================================"
echo "  AI漫剧制作平台 - 一键构建 v${VERSION}"
echo "  平台: ${PLATFORM} | 时间: ${TIMESTAMP}"
echo "============================================"

# 1. 构建前端
echo ""
echo "[1/3] 🔨 构建前端 (Vue 3 + Vite)..."
cd frontend
npm ci --registry=https://registry.npmmirror.com 2>/dev/null || npm install
npm run build
echo "✅ 前端构建完成"
cd ..

# 2. 构建后端 JAR
echo ""
echo "[2/3] ☕ 构建后端 (Spring Boot)..."
cd backend
mvn clean package -DskipTests -q
JAR_FILE=$(ls target/*.jar | head -1)
echo "✅ 后端 JAR: ${JAR_FILE}"
cd ..

# 3. 复制 JAR 到正确位置
mkdir -p backend/target
cp backend/target/ai-comic-platform-*.jar backend/target/

# 4. 打包 Electron
echo ""
echo "[3/3] 📦 打包 Electron 桌面应用..."

cd frontend && npm ci --registry=https://registry.npmmirror.com && npm run build && cd ..
npm install -g electron-builder@latest 2>/dev/null || true

if [[ "$PLATFORM" == "win" ]] || [[ "$PLATFORM" == "all" ]]; then
    echo "  → 构建 Windows 安装包..."
    npx electron-builder --win --publish never \
        -c.productName="AI漫剧" \
        -c.appId="com.aicomic.platform" \
        -c.directories.output=release/win-${TIMESTAMP} \
        -c.win.target=nsis \
        -c.win.icon=assets/logo.png \
        -c.nsis.oneClick=false \
        -c.nsis.allowToChangeInstallationDirectory=true \
        -c.nsis.installerIcon=assets/logo.png \
        -c.nsis.createDesktopShortcut=true
    echo "✅ Windows 安装包: release/win-${TIMESTAMP}/"
fi

if [[ "$PLATFORM" == "mac" ]] || [[ "$PLATFORM" == "all" ]]; then
    echo "  → 构建 macOS DMG..."
    npx electron-builder --mac --publish never \
        -c.directories.output=release/mac-${TIMESTAMP} \
        -c.mac.target=dmg \
        -c.mac.icon=assets/logo.png \
        -c.mac.category=public.app-category.entertainment
    echo "✅ macOS DMG: release/mac-${TIMESTAMP}/"
fi

echo ""
echo "============================================"
echo "  🎉 构建完成!"
echo "============================================"

# 列出产物
find release/ -type f \( -name "*.exe" -o -name "*.dmg" \) 2>/dev/null | while read f; do
    SIZE=$(du -h "$f" | awk '{print $1}')
    echo "  📄 $(basename $f) (${SIZE})"
done

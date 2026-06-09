#!/bin/bash
set -e

WORKSPACE="${1:-$PWD}"
echo ">>> Workspace: $WORKSPACE"

# ====== Set mirrors ======
export ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/
eval $(printf 'export ELEC_BUILDER_BIN_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/')

# ====== Install electron-builder + fix @noble/hashes in isolated dir ======
echo ">>> Installing electron-builder + @noble/hashes@1.3.1..."
FIXDIR=/tmp/__ebfix
rm -rf "$FIXDIR" && mkdir "$FIXDIR" && cd "$FIXDIR"
npm init -y >/dev/null 2>&1
npm install \
  --registry=https://registry.npmmirror.com \
  --legacy-peer-deps \
  electron-builder \
  @noble/hashes@1.3.1 \
  2>&1 | tail -3

# ====== Find app-builder-lib node_modules and replace @noble/hashes ======
ABLNM=$(find node_modules -path '*/app-builder-lib/node_modules' -type d -print -quit)
if [ -z "$ABLNM" ]; then
  ABLNM="node_modules/electron-builder/node_modules"
fi
echo ">>> Patching @noble in: $ABLNM"

# Remove all existing @noble copies
find "$ABLNM" -name "@noble" -type d -exec rm -rf {} + 2>/dev/null || true

# Copy CJS v1.3.1
mkdir -p "$ABLNM/@noble"
cp -r node_modules/@noble/hashes "$ABLNM/@noble/hashes"

# ====== Verify ======
node -e "
var p=require('$ABLNM/@noble/hashes/package.json');
console.log('NOBLE v'+p.version+' type='+(p.type||'cjs'));
require('$ABLNM/@noble/hashes/blake2.js');
console.log('REQUIRE OK')
"

# ====== Clean any cached nested copies from global ======
GNM="/root/.npm-global/lib/node_modules"
EB_GLOBAL=$(find "$GNM" -maxdepth 1 -name '*electron*' -type d 2>/dev/null | head -1)
if [ -n "$EB_GLOBAL" ]; then
  find "$EB_GLOBAL" -name "@noble" -type d -exec rm -rf {} + 2>/dev/null || true
  mkdir -p "$EB_GLOBAL/node_modules/@noble"
  cp -r "$FIXDIR/$ABLNM/@noble/hashes" "$EB_GLOBAL/node_modules/@noble/hashes" 2>/dev/null || true
  echo ">>> Patched global @noble too"
fi

# ====== Run electron-builder ======
echo ">>> Running electron-builder..."
cd "$WORKSPACE"
export PATH="$FIXDIR/node_modules/.bin:$PATH"

electron-builder --win --publish never \
  --config.productName="AI漫剧" \
  --config.appId="com.aicomic.platform" \
  --config.directories.output="release/win" \
  --config.win.target="nsis" \
  --config.win.icon="assets/logo.png" \
  --config.nsis.oneClick=false \
  --config.nsis.allowToChangeInstallationDirectory=true \
  --config.nsis.createDesktopShortcut=true

echo ">>> Done"
ls -lh release/win/*.exe 2>/dev/null || echo "WARN: no exe"

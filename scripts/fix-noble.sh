#!/bin/bash
echo ">>> [fix-noble] Installing @noble/hashes@1.3.1 (CJS)..."
npm i -g @noble/hashes@1.3.1 \
  --legacy-peer-deps \
  --registry=https://registry.npmmirror.com \
  2>&1 | tail -3

GNM="/root/.npm-global/lib/node_modules"
echo ">>> [fix-noble] Looking for electron-builder to patch..."

EB=$(find "$GNM" -maxdepth 1 -name 'electron-builder*' -type d 2>/dev/null | head -1)
if [ -z "$EB" ]; then
  echo ">>> [fix-noble] WARN: electron-builder not found at $GNM, listing dirs:"
  ls "$GNM" 2>/dev/null || echo "  (cannot list)"
  echo ">>> [fix-noble] Skipping patch - hope for the best"
  exit 0
fi

echo ">>> [fix-noble] Patching $EB..."
NOBLE="$EB/node_modules/@noble/hashes"
if [ -d "$NOBLE" ]; then
  rm -rf "$NOBLE"
  cp -r "$GNM/@noble/hashes" "$NOBLE"
else
  mkdir -p "$(dirname "$NOBLE")"
  cp -r "$GNM/@noble/hashes" "$NOBLE"
fi

# 同样修补 app-builder-lib 里的引用（如果存在独立的 node_modules）
ABL=$(find "$EB" -path '*/app-builder-lib/node_modules' -type d 2>/dev/null | head -1)
if [ -n "$ABL" ]; then
  NOBLE2="$ABL/@noble/hashes"
  if [ -d "$NOBLE2" ] || [ ! -d "$NOBLE2" ]; then
    rm -rf "$NOBLE2" 2>/dev/null
    mkdir -p "$(dirname "$NOBLE2")"
    cp -r "$GNM/@noble/hashes" "$NOBLE2"
    echo ">>> [fix-noble] Also patched app-builder-lib/node_modules"
  fi
fi

# 验证
node -e "
var EP = '$EB/node_modules/@noble/hashes';
var pk = require(EP + '/package.json');
console.log('  noble version: v' + pk.version + ' type=' + (pk.type || 'cjs'));
require(EP + '/blake2.js');
console.log('  blake2 require: OK');
"

echo ">>> [fix-noble] Done"

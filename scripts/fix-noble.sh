#!/bin/bash
set -e
echo ">>> [fix-noble] Installing @noble/hashes@1.3.1 (CJS)..."
npm i -g @noble/hashes@1.3.1 \
  --legacy-peer-deps \
  --registry=https://registry.npmmirror.com \
  2>&1 | tail -2

GNM="/root/.npm-global/lib/node_modules"
echo ">>> [fix-noble] Patching electron-builder's @noble/hashes..."

# 找到所有 electron 相关的全局包，替换其 @noble/hashes
for EB in $(find "$GNM" -maxdepth 1 \( -name 'electron*' -o -name 'app-builder*' \) -type d 2>/dev/null); do
  NOBLE="$EB/node_modules/@noble/hashes"
  if [ -d "$NOBLE" ]; then
    rm -rf "$NOBLE"
    cp -r "$GNM/@noble/hashes" "$NOBLE"
    echo "  patched: $(basename $EB)"
  fi
done

# 验证
node -e "
var p='$GNM/electron-builder/node_modules/@noble/hashes/package.json';
var h=require(p);
console.log('  noble version: v'+h.version+' type='+(h.type||'cjs'));
require('$GNM/electron-builder/node_modules/@noble/hashes/blake2.js');
console.log('  blake2 require: OK');
"

echo ">>> [fix-noble] Done"

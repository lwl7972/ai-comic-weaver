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

# 修补一个 @noble/hashes 目录：复制 CJS + 删 exports
patch_one() {
  local target="$1"
  echo ">>> [fix-noble]   -> $target"
  rm -rf "$target" 2>/dev/null
  mkdir -p "$(dirname "$target")"
  cp -r "$GNM/@noble/hashes" "$target"
  # ★ 关键：删除 exports 字段，否则 require('./blake2.js') 仍会被拦截
  node -e "
    var fs=require('fs');
    var pj=JSON.parse(fs.readFileSync('$target/package.json','utf8'));
    delete pj.exports;
    fs.writeFileSync('$target/package.json',JSON.stringify(pj,null,2));
  "
}

# 修补 electron-builder 顶层的 @noble/hashes
patch_one "$EB/node_modules/@noble/hashes"

# 修补所有嵌套出现的（app-builder-lib 等包内的独立副本）
# 使用 for + find 避免管道子 shell 问题
ALLOBJS=$(find "$EB" -mindepth 3 -path '*/@noble/hashes' -type d 2>/dev/null)
for NH in $ALLOBJS; do
  if [ -d "$NH" ]; then
    patch_one "$NH"
  fi
done

# 验证
echo ">>> [fix-noble] Verifying..."

# 验证顶层
node -e "
var fs=require('fs');
var EP = '$EB/node_modules/@noble/hashes';
var pk = JSON.parse(fs.readFileSync(EP+'/package.json','utf8'));
console.log('  top-level: v'+pk.version+' exports='+JSON.stringify(pk.exports||'DELETED'));
try { require(EP+'/blake2.js'); console.log('  top-level require: OK'); }
catch(e) { console.log('  top-level require: FAIL - '+e.message); }
"

# 验证嵌套
for NH in $ALLOBJS; do
  if [ -d "$NH" ]; then
    node -e "
      var fs=require('fs');
      var pk=JSON.parse(fs.readFileSync('$NH/package.json','utf8'));
      console.log('  nested: v'+pk.version+' exports='+JSON.stringify(pk.exports||'DELETED'));
      try { require('$NH/blake2.js'); console.log('  nested require: OK'); }
      catch(e) { console.log('  nested require: FAIL - '+e.message); }
    "
  fi
done

echo ">>> [fix-noble] Done"

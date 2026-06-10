$ErrorActionPreference = "Continue"
Set-Location d:\AI\atomgit

Write-Host "=== Local Electron Build Test ===" -ForegroundColor Cyan

Write-Host "`n[1] electron-builder version:" -ForegroundColor Yellow
npx electron-builder --version

Write-Host "`n[2] Check @noble/hashes..." -ForegroundColor Yellow
node -e "try{var r1=require.resolve('@noble/hashes/blake2.js',{paths:['d:/AI/atomgit/node_modules']});console.log('found at:',r1);var p=require(r1.replace('blake2.js','package.json'));console.log('version:',p.version)}catch(e){console.log('Not found in local node_modules')}"

Write-Host "`n[3] Check frontend dist..." -ForegroundColor Yellow
if (Test-Path "frontend/dist/index.html") {
    Write-Host "  frontend/dist/ exists [OK]" -ForegroundColor Green
} else {
    Write-Host "  frontend/dist/ MISSING [FAIL]" -ForegroundColor Red
}

Write-Host "`n[4] Check backend jar..." -ForegroundColor Yellow
$jar = Get-ChildItem "backend/target/*.jar" -ErrorAction SilentlyContinue
if ($jar) {
    Write-Host "  $($jar.Name) exists [OK]" -ForegroundColor Green
} else {
    Write-Host "  backend/target/*.jar MISSING [FAIL]" -ForegroundColor Red
}

Write-Host "`n[5] Start Electron build (this may take minutes)..." -ForegroundColor Yellow
Write-Host ""

$result = npx electron-builder --win --publish never 2>&1
$exitCode = $LASTEXITCODE

Write-Host "`n=== Build Result ===" -ForegroundColor Cyan
Write-Host "Exit code: $exitCode"

if ($exitCode -eq 0) {
    Write-Host "BUILD SUCCESS!" -ForegroundColor Green
    Write-Host "`nArtifacts:" -ForegroundColor Yellow
    Get-ChildItem "release/win/" -ErrorAction SilentlyContinue | ForEach-Object {
        $sizeMB = [math]::Round($_.Length/1048576, 2)
        Write-Host "  $($_.Name) ($sizeMB MB)" -ForegroundColor White
    }
} else {
    Write-Host "BUILD FAILED!" -ForegroundColor Red
    Write-Host "`nLast 20 lines of output:" -ForegroundColor Yellow
    $result | Select-Object -Last 20
}

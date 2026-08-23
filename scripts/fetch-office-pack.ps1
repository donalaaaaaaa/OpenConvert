# 获取 LibreOfficeKit Office Pack 素材到 office flavor（不入库的大二进制）。
# 用法：.\scripts\fetch-office-pack.ps1
# 素材来源：gurecn/LibreOffice-android v1.0 release（APK 内提取 lib/arm64-v8a + assets）
# 版本校验：liblo-native-code.so BuildId 需与 docs/lokit-validation.md 一致

$ErrorActionPreference = 'Stop'

$repo = 'https://github.com/gurecn/LibreOffice-android/releases/download/v1.0/app-release.apk'
$expectedSha256 = '702b07299990150bb0ddec862a915d7492fd4f03094fe46f484b0d0ce054388d'
$dist = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'native\dist'
$apk = Join-Path $dist 'lo-viewer.apk'
$outLib = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'app\src\office\jniLibs\arm64-v8a'
$outAssets = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'app\src\office\assets'

New-Item -ItemType Directory -Force -Path $dist, $outLib, $outAssets | Out-Null

if (-not (Test-Path $apk)) {
    Write-Host "Downloading $repo ..."
    Invoke-WebRequest -Uri $repo -OutFile $apk
}

$actual = (Get-FileHash -Algorithm SHA256 -Path $apk).Hash.ToLowerInvariant()
if ($actual -ne $expectedSha256) {
    throw "SHA256 mismatch for lo-viewer.apk: expected $expectedSha256 got $actual"
}

python -c @"
import zipfile, os
apk = r'$apk'
out_lib = r'$outLib'
out_assets = r'$outAssets'
z = zipfile.ZipFile(apk)
libs = [n for n in z.namelist() if n.startswith('lib/arm64-v8a/') and n.endswith('.so')]
for n in libs:
    data = z.read(n)
    open(os.path.join(out_lib, os.path.basename(n)), 'wb').write(data)
count = 0
for n in z.namelist():
    if n.startswith('assets/') and not n.endswith('/'):
        data = z.read(n)
        fn = os.path.join(out_assets, n[len('assets/'):])
        os.makedirs(os.path.dirname(fn), exist_ok=True)
        open(fn, 'wb').write(data)
        count += 1
print(f'extracted {len(libs)} libs + {count} assets')
"@

Write-Host 'Done. Build office flavor: .\gradlew.bat :app:assembleOfficeDebug'

#!/usr/bin/env bash
# Fetch the upstream LibreOffice Android APK and extract Office flavor libs.
# Wrapper around native/deps.lock (lo-android-v1.0) + the existing unpack logic.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
./scripts/fetch-native-deps.sh --only lo-android-v1.0
python - <<'PY'
import zipfile, os
from pathlib import Path
root = Path('.')
apk = root / 'native' / 'dist' / 'lo-viewer.apk'
out_lib = root / 'app' / 'src' / 'office' / 'jniLibs' / 'arm64-v8a'
out_assets = root / 'app' / 'src' / 'office' / 'assets'
out_lib.mkdir(parents=True, exist_ok=True)
out_assets.mkdir(parents=True, exist_ok=True)
z = zipfile.ZipFile(apk)
libs = [n for n in z.namelist() if n.startswith('lib/arm64-v8a/') and n.endswith('.so')]
for n in libs:
    (out_lib / os.path.basename(n)).write_bytes(z.read(n))
count = 0
for n in z.namelist():
    if n.startswith('assets/') and not n.endswith('/'):
        dest = out_assets / n[len('assets/'):]
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(z.read(n))
        count += 1
print(f'extracted {len(libs)} libs + {count} assets')
PY
echo "Done. Build office flavor: ./gradlew :app:assembleOfficeDebug"

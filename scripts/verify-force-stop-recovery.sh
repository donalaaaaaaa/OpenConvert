# 真机：安装一次 → instrument 入队 → force-stop → instrument 验证。
# 全程不走 connectedAndroidTest，避免 Gradle 卸包清空 Room。
# 用法：./scripts/verify-force-stop-recovery.sh [deviceId]

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DEVICE="${1:-${ANDROID_SERIAL:-}}"
ADB=(adb)
if [[ -n "$DEVICE" ]]; then
  ADB=(adb -s "$DEVICE")
  export ANDROID_SERIAL="$DEVICE"
fi

PKG="com.openconvert.app.debug"
RUNNER="com.openconvert.app.debug.test/androidx.test.runner.AndroidJUnitRunner"
SEED="com.openconvert.app.work.ForceStopLiveSeedTest"
VERIFY="com.openconvert.app.work.ForceStopVerifyTest"

echo "0/3 install debug + androidTest"
./gradlew.bat installOfficeDebug installOfficeDebugAndroidTest --no-daemon

echo "1/3 enqueue long archive"
"${ADB[@]}" shell am instrument -w -e class "$SEED" "$RUNNER"

echo "2/3 am force-stop $PKG"
sleep 2
"${ADB[@]}" shell am force-stop "$PKG"
sleep 1

echo "3/3 instrument verify (no reinstall)"
OUT="$("${ADB[@]}" shell am instrument -w -e class "$VERIFY" "$RUNNER")"
printf '%s\n' "$OUT"
if printf '%s' "$OUT" | grep -q 'FAILURES'; then
  echo "verify failed"
  exit 1
fi
if printf '%s' "$OUT" | grep -q 'INSTRUMENTATION_FAILED'; then
  echo "instrument failed"
  exit 1
fi
echo "force-stop recovery OK"

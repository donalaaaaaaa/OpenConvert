#!/usr/bin/env bash
# Office flavor instrumented suite on a connected arm64 device.
#
# Do NOT use Gradle connectedOfficeDebugAndroidTest here: split-APK install
# sessions fail with Error -99 on PHY110. Install the two APKs, then drive
# AndroidJUnitRunner directly.
#
# Skips 4GB stability and the force-stop seed/verify pair. Those have their
# own scripts and must not ride along with a full suite.
#
# Usage:
#   ./scripts/run-office-instrumented.sh
#   ./scripts/run-office-instrumented.sh --no-install
#   ./scripts/run-office-instrumented.sh --class com.openconvert.app.ui.TaskCenterInstrumentedTest

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

RUNNER="com.openconvert.app.debug.test/androidx.test.runner.AndroidJUnitRunner"
SKIP_CLASSES="com.openconvert.app.domain.work.LargeFileStabilityInstrumentedTest,com.openconvert.app.domain.converter.LargeFileConversionInstrumentedTest,com.openconvert.app.work.ForceStopLiveSeedTest,com.openconvert.app.work.ForceStopVerifyTest"

DO_INSTALL=1
CLASS_FILTER=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-install) DO_INSTALL=0; shift ;;
    --class)
      CLASS_FILTER="${2:-}"
      if [[ -z "$CLASS_FILTER" ]]; then
        echo "--class needs a fully-qualified test class" >&2
        exit 2
      fi
      shift 2
      ;;
    *)
      echo "unknown arg: $1" >&2
      exit 2
      ;;
  esac
done

if [[ "$DO_INSTALL" -eq 1 ]]; then
  echo "install Office debug + androidTest"
  if [[ -x ./gradlew ]]; then
    ./gradlew installOfficeDebug installOfficeDebugAndroidTest --no-daemon
  else
    ./gradlew.bat installOfficeDebug installOfficeDebugAndroidTest --no-daemon
  fi
fi

ARGS=(-w)
if [[ -n "$CLASS_FILTER" ]]; then
  ARGS+=(-e class "$CLASS_FILTER")
else
  ARGS+=(-e notClass "$SKIP_CLASSES")
fi

echo "am instrument ${ARGS[*]} $RUNNER"
OUT="$(adb shell am instrument "${ARGS[@]}" "$RUNNER")"
printf '%s\n' "$OUT"
if printf '%s' "$OUT" | grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Error in '; then
  echo "instrumented suite failed" >&2
  exit 1
fi
if ! printf '%s' "$OUT" | grep -q 'OK ('; then
  echo "instrumented suite did not report OK" >&2
  exit 1
fi
echo "office instrumented suite OK"

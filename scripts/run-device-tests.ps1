# 在已连接的 arm64 真机上跑本轮硬化相关 instrumented 测试。
# 用法：
#   .\scripts\run-device-tests.ps1
#   .\scripts\run-device-tests.ps1 -DeviceId 8ed34578
#   .\scripts\run-device-tests.ps1 -IncludeLargeFiles

param(
    [string]$DeviceId = "",
    [switch]$IncludeLargeFiles
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ($DeviceId) {
    $env:ANDROID_SERIAL = $DeviceId
}

$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) { throw "adb not on PATH" }

$serial = if ($DeviceId) { $DeviceId } else { "" }
$devices = if ($serial) {
    & adb -s $serial get-state
} else {
    & adb get-state
}
if ($LASTEXITCODE -ne 0 -and -not $serial) {
    throw "no adb device"
}

$classes = @(
    "com.openconvert.app.domain.work.ConversionRecoveryInstrumentedTest",
    "com.openconvert.app.domain.converter.OfficePackIsolationInstrumentedTest"
)
if ($IncludeLargeFiles) {
    $classes += "com.openconvert.app.domain.work.LargeFileStabilityInstrumentedTest"
}

$joined = ($classes -join ",")
Write-Host "connectedOfficeDebugAndroidTest class=$joined"
cmd /c "gradlew.bat connectedOfficeDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=$joined --no-daemon --stacktrace"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

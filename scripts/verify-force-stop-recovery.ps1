# 真机：安装一次 → instrument 入队 → force-stop → instrument 验证。
# 全程不走 connectedAndroidTest，避免 Gradle 卸包清空 Room。
# 用法：.\scripts\verify-force-stop-recovery.ps1 [-DeviceId 8ed34578]

param([string]$DeviceId = "")

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ($DeviceId) { $env:ANDROID_SERIAL = $DeviceId }
$adbArgs = @()
if ($DeviceId) { $adbArgs = @("-s", $DeviceId) }

$pkg = "com.openconvert.app.debug"
$runner = "com.openconvert.app.debug.test/androidx.test.runner.AndroidJUnitRunner"
$seed = "com.openconvert.app.work.ForceStopLiveSeedTest"
$verify = "com.openconvert.app.work.ForceStopVerifyTest"

Write-Host "0/3 install debug + androidTest"
cmd /c "gradlew.bat installOfficeDebug installOfficeDebugAndroidTest --no-daemon"
if ($LASTEXITCODE -ne 0) { throw "install failed" }

Write-Host "1/3 enqueue long archive"
$seedOut = & adb @adbArgs shell am instrument -w -e class $seed $runner
Write-Host $seedOut
if ($seedOut -match "FAILURES|INSTRUMENTATION_FAILED") { throw "seed failed" }

Start-Sleep -Seconds 2
Write-Host "2/3 am force-stop $pkg"
& adb @adbArgs shell am force-stop $pkg
Start-Sleep -Seconds 1

Write-Host "3/3 instrument verify (no reinstall)"
$verifyOut = & adb @adbArgs shell am instrument -w -e class $verify $runner
Write-Host $verifyOut
if ($verifyOut -match "FAILURES|INSTRUMENTATION_FAILED") { throw "verify failed" }
Write-Host "force-stop recovery OK"

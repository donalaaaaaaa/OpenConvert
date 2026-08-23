param(
    [string]$DeviceId = ""
)

$ErrorActionPreference = "Stop"
$workspacePath = Split-Path -Parent $PSScriptRoot
$adbPrefix = if ($DeviceId) { @("-s", $DeviceId) } else { @() }
$remoteUiDump = "/sdcard/openconvert-upgrade-ui.xml"
$remoteFixture = "/sdcard/Download/OpenConvert-upgrade-SAF-marker.jpg"
$uiLabels = Get-Content -LiteralPath (Join-Path $PSScriptRoot "upgrade-ui-labels.json") -Raw -Encoding UTF8 | ConvertFrom-Json

function Invoke-Adb {
    $commandArgs = @($args)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $commandOutput = & adb @adbPrefix @commandArgs 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "adb failed: adb $($commandArgs -join ' ')`n$($commandOutput | Out-String)"
        }
        return $commandOutput
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Get-UiHierarchy {
    Invoke-Adb shell uiautomator dump $remoteUiDump | Out-Null
    $localDump = Join-Path ([System.IO.Path]::GetTempPath()) "openconvert-upgrade-ui.xml"
    Invoke-Adb pull $remoteUiDump $localDump | Out-Null
    return [xml](Get-Content -LiteralPath $localDump -Raw -Encoding UTF8)
}

function Node-Attr {
    param($Node, [string]$Name)
    if ($null -eq $Node) { return "" }
    return [string]$Node.GetAttribute($Name)
}

function Tap-UiNode {
    param(
        [string]$Description,
        [scriptblock]$Predicate
    )
    foreach ($attempt in 1..15) {
        $hierarchy = Get-UiHierarchy
        $node = $hierarchy.SelectNodes("//*[@bounds]") |
            Where-Object { & $Predicate $_ } |
            Select-Object -First 1
        if ($null -ne $node -and $node.bounds -match '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
            $tapX = ([int]$Matches[1] + [int]$Matches[3]) / 2
            $tapY = ([int]$Matches[2] + [int]$Matches[4]) / 2
            Invoke-Adb shell input tap ([int]$tapX) ([int]$tapY) | Out-Null
            Start-Sleep -Milliseconds 600
            return
        }
        Start-Sleep -Milliseconds 300
    }
    throw "UI node not found: $Description"
}

function Invoke-Instrumentation {
    param([string]$ClassName)
    $instrumentOutput = Invoke-Adb shell am instrument -w -r -e class $ClassName `
        com.openconvert.app.test/androidx.test.runner.AndroidJUnitRunner | Out-String
    Write-Host $instrumentOutput
    if ($instrumentOutput -notmatch 'OK \(1 test\)') {
        throw "Instrumentation failed: $ClassName"
    }
}

Push-Location $workspacePath
try {
    & .\gradlew.bat -PopenconvertTestBuildType=release `
        :app:assembleBasicRelease `
        :app:assembleOfficeRelease `
        :app:assembleBasicReleaseAndroidTest `
        :app:assembleOfficeReleaseAndroidTest
    if ($LASTEXITCODE -ne 0) { throw "Release upgrade artifacts failed to build" }

    $basicApk = "app\build\outputs\apk\basic\release\app-basic-arm64-v8a-release.apk"
    $officeApk = "app\build\outputs\apk\office\release\app-office-arm64-v8a-release.apk"
    $basicTestApk = "app\build\outputs\apk\androidTest\basic\release\app-basic-release-androidTest.apk"
    $officeTestApk = "app\build\outputs\apk\androidTest\office\release\app-office-release-androidTest.apk"

    Invoke-Adb install -r $basicApk | Write-Host
    Invoke-Adb install -r $basicTestApk | Write-Host
    Invoke-Adb push "device-artifacts\OpenConvert_E2E_Source.jpg" $remoteFixture | Write-Host
    Invoke-Adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE `
        -d "file://$remoteFixture" | Out-Null

    Invoke-Adb shell am force-stop com.openconvert.app | Out-Null
    Invoke-Adb shell monkey -p com.openconvert.app -c android.intent.category.LAUNCHER 1 | Out-Null
    Start-Sleep -Milliseconds 1500
    Tap-UiNode "OpenConvert pick file" { param($node) (Node-Attr $node "text") -eq $uiLabels.pickFileText }
    Tap-UiNode "files tab" {
        param($node) (Node-Attr $node "text") -in @($uiLabels.filesTab, $uiLabels.filesTabEn)
    }
    Tap-UiNode "downloads folder" {
        param($node) (Node-Attr $node "content-desc") -in @($uiLabels.downloads, $uiLabels.downloadsEn)
    }
    Tap-UiNode "picker search" {
        param($node) (Node-Attr $node "content-desc") -in @($uiLabels.search, $uiLabels.searchEn)
    }
    Invoke-Adb shell input text "OpenConvert-upgrade-SAF-marker.jpg" | Out-Null
    Start-Sleep -Milliseconds 800
    Tap-UiNode "upgrade SAF JPG" {
        param($node)
        ((Node-Attr $node "resource-id") -like "*:id/file_list_item_title" -or
            (Node-Attr $node "resource-id") -like "*:id/title_tv") -and
            (Node-Attr $node "text") -like "*OpenConvert*" -and
            (Node-Attr $node "text") -like "*.jpg*"
    }
    Start-Sleep -Milliseconds 800

    Invoke-Instrumentation "com.openconvert.app.upgrade.BasicUpgradeSeedInstrumentedTest"

    Invoke-Adb install -r $officeApk | Write-Host
    Invoke-Adb install -r $officeTestApk | Write-Host
    Invoke-Instrumentation "com.openconvert.app.upgrade.OfficeUpgradeVerifyInstrumentedTest#officeReplacementPreservesBasicStateAndAddsOfficeCapability"
    Invoke-Instrumentation "com.openconvert.app.domain.converter.OfficeConverterInstrumentedTest#bundledEngineConvertsAllOfficeFormats"
    Invoke-Instrumentation "com.openconvert.app.upgrade.OfficeUpgradeVerifyInstrumentedTest#cleanupUpgradeFixtures"

    Invoke-Adb uninstall com.openconvert.app.test | Write-Host
    Invoke-Adb shell rm -f $remoteFixture | Out-Null

    # Release instrumentation uses conditional keep rules in the target APK. Rebuild without
    # that property so the published artifacts and the package left on-device are production.
    & .\gradlew.bat :app:assembleBasicRelease :app:assembleOfficeRelease
    if ($LASTEXITCODE -ne 0) { throw "Production Release artifacts failed to rebuild" }
    Invoke-Adb install -r $officeApk | Write-Host

    Invoke-Adb shell dumpsys package com.openconvert.app |
        Select-String "versionName=|versionCode=" |
        Select-Object -First 2 |
        Write-Host
} finally {
    Pop-Location
}

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$buildRoot = $projectRoot
$createdMapping = $false
$verifyDrive = $null

if ($projectRoot -match '[^\u0000-\u007F]') {
    $driveLetter = @('X', 'W', 'V') |
        Where-Object { -not (Get-PSDrive -Name $_ -ErrorAction SilentlyContinue) } |
        Select-Object -First 1

    if (-not $driveLetter) {
        throw 'No free X:, W:, or V: drive is available for the ASCII build path.'
    }

    $verifyDrive = "${driveLetter}:"
    & subst.exe $verifyDrive $projectRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to map $verifyDrive to the project directory."
    }

    $createdMapping = $true
    $buildRoot = "$verifyDrive\"
}

try {
    if (-not $env:JAVA_HOME) {
        $jdk = Get-ChildItem -LiteralPath 'C:\Program Files\Microsoft' -Directory -ErrorAction SilentlyContinue |
            Where-Object Name -Like 'jdk-17*' |
            Sort-Object Name -Descending |
            Select-Object -First 1
        if ($jdk) {
            $env:JAVA_HOME = $jdk.FullName
        }
    }

    Push-Location $buildRoot
    try {
        & .\gradlew.bat testBasicDebugUnitTest assembleBasicDebug assembleOfficeDebug --no-daemon
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle verification failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    if ($createdMapping) {
        & subst.exe $verifyDrive /D
    }
}

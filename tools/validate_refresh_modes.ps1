$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
$originalMin = (& adb shell settings get system min_refresh_rate).Trim()
$originalPeak = (& adb shell settings get system peak_refresh_rate).Trim()
try {
    foreach ($mode in @('adaptive', '60', '120')) {
        $minimum = if ($mode -eq 'adaptive') { '60' } else { $mode }
        $maximum = if ($mode -eq 'adaptive') { '120' } else { $mode }
        & adb shell settings put system min_refresh_rate $minimum
        & adb shell settings put system peak_refresh_rate $maximum
        $gradleArguments = @(
            '-Pledger.testApplicationId=in.thomas.pocketledger.validation',
            '-Pandroid.testInstrumentationRunnerArguments.class=in.thomas.pocketledger.RedesignUiTest'
        )
        if ($mode -ne 'adaptive') { $gradleArguments += "-Pandroid.testInstrumentationRunnerArguments.ledger.expectedHz=$mode" }
        $gradleArguments += ':app:connectedDebugAndroidTest'
        & .\gradlew.bat @gradleArguments
        if ($LASTEXITCODE -ne 0) { throw "UI checks failed in $mode mode" }
        $destination = Join-Path $projectRoot "artifacts/ui-$mode"
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        Copy-Item -Path 'app/build/outputs/androidTest-results/connected/debug/*.xml' -Destination $destination
    }
} finally {
    if ($originalMin -eq 'null') { & adb shell settings delete system min_refresh_rate }
    else { & adb shell settings put system min_refresh_rate $originalMin }
    if ($originalPeak -eq 'null') { & adb shell settings delete system peak_refresh_rate }
    else { & adb shell settings put system peak_refresh_rate $originalPeak }
    Pop-Location
}

# Keep arguments unbound so Gradle flags such as --info are not PowerShell parameters.
$taskGradleArgs = @($args)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskTools = Join-Path $taskRoot '.local-tools'
if (-not $env:JAVA_HOME) {
    $taskJava = (Get-Command java -ErrorAction Stop).Source
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $taskJava)
}
$env:GRADLE_USER_HOME = Join-Path $taskTools 'gradle-home'
$env:ANDROID_USER_HOME = Join-Path $taskTools 'android-user'
$env:ANDROID_AVD_HOME = Join-Path $taskTools 'android-avd'
$taskLocalSdk = Join-Path $taskTools 'android-sdk'
if (Test-Path -LiteralPath $taskLocalSdk) {
    $env:ANDROID_HOME = $taskLocalSdk
    $env:ANDROID_SDK_ROOT = $taskLocalSdk
}
if (-not $taskGradleArgs) { $taskGradleArgs = @(':app:testDebugUnitTest', ':app:assembleDebug') }
Push-Location $taskRoot
try {
    & (Join-Path $taskRoot 'gradlew.bat') @taskGradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }

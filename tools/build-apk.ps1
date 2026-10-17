# 在 Windows 电脑上一键打出 APK（不需要 Android Studio 图形界面，但需要它装好的 Android SDK）
#
# 前提：这台电脑装过 Android Studio，并且至少打开过一次让 SDK 下载完成。
# 用法（在工程根目录或任意位置）：
#     powershell -ExecutionPolicy Bypass -File tools\build-apk.ps1
# 产物：app\build\outputs\apk\debug\app-debug.apk

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$gradleVersion = '8.7'

Write-Host "工程目录: $root"

# ---------- 1. 找 Android SDK ----------
$candidates = @(
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    (Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'),
    'C:\Android\Sdk'
) | Where-Object { $_ -and (Test-Path (Join-Path $_ 'platforms')) }

if (-not $candidates -or $candidates.Count -eq 0) {
    throw "没有找到 Android SDK。请先安装 Android Studio，打开一次让它下载 SDK，再运行本脚本。"
}
$sdk = @($candidates)[0]
Write-Host "Android SDK: $sdk"

# SDKDIR 用正斜杠写入，避免 Java properties 把反斜杠当转义符
$sdkForProps = $sdk -replace '\\', '/'
"sdk.dir=$sdkForProps" | Set-Content -Path (Join-Path $root 'local.properties') -Encoding ASCII
Write-Host "已写入 local.properties"

# ---------- 2. 准备 Gradle ----------
$gradleDest = Join-Path $env:LOCALAPPDATA 'Gradle'
$gradleHome = Join-Path $gradleDest "gradle-$gradleVersion"
if (-not (Test-Path $gradleHome)) {
    Write-Host "下载 Gradle $gradleVersion ..."
    New-Item -ItemType Directory -Force -Path $gradleDest | Out-Null
    $zip = Join-Path $env:TEMP "gradle-$gradleVersion-bin.zip"
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $gradleDest -Force
    Remove-Item $zip -Force -ErrorAction SilentlyContinue
}
$gradleBat = Join-Path $gradleHome 'bin\gradle.bat'
if (-not (Test-Path $gradleBat)) {
    throw "Gradle 安装失败：找不到 $gradleBat"
}

# ---------- 3. 构建 Debug APK ----------
Write-Host "开始编译（首次会下载依赖，可能 3~10 分钟）..."
& $gradleBat -p $root assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) {
    throw "编译失败（退出码 $LASTEXITCODE），请把上面的报错发给我。"
}

$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) {
    throw "编译似乎成功，但没有找到 APK：$apk"
}

Write-Host ""
Write-Host "✅ 完成！APK 路径：$apk"
Write-Host "   把它传到平板（微信文件传输助手 / 数据线）→ 点开安装即可。"
Write-Host "   或直接打开所在文件夹："
Write-Host "   explorer.exe /select,`"$apk`""

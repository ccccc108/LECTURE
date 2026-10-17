# 一键准备 Gradle（用于在电脑上用 Android Studio 打开本工程）
#
# 背景：本工程没有附带 gradle-wrapper.jar（二进制文件无法通过纯文本方式生成）。
# 如果电脑上已经装了 Gradle，可以直接在工程根目录执行：
#     gradle wrapper --gradle-version 8.7 --distribution-type bin
# 如果没装，运行本脚本会自动下载 Gradle 8.7 并生成 wrapper。
#
# 用法（PowerShell）：
#     powershell -ExecutionPolicy Bypass -File tools\bootstrap-gradle.ps1

$ErrorActionPreference = 'Stop'

$version = '8.7'
$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $env:LOCALAPPDATA 'Gradle'
$gradleHome = Join-Path $dest "gradle-$version"

if (-not (Test-Path $gradleHome)) {
    Write-Host "下载 Gradle $version ..."
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    $zip = Join-Path $env:TEMP "gradle-$version-bin.zip"
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$version-bin.zip" -OutFile $zip
    Write-Host "解压到 $dest ..."
    Expand-Archive -Path $zip -DestinationPath $dest -Force
    Remove-Item $zip -Force -ErrorAction SilentlyContinue
}

Write-Host "生成 gradle wrapper ..."
& (Join-Path $gradleHome 'bin\gradle.bat') wrapper --gradle-version $version --distribution-type bin -p $root
if ($LASTEXITCODE -ne 0) {
    throw "gradle wrapper 执行失败（退出码 $LASTEXITCODE）"
}

Write-Host ""
Write-Host "完成！现在可以用 Android Studio 打开工程目录：$root"
Write-Host "或者直接命令行打包：$gradleHome\bin\gradle.bat -p `"$root`" assembleDebug"

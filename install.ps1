param([Parameter(Mandatory=$true)][string]$TvHost,[string]$ApkPath=(Join-Path $PSScriptRoot 'launcher\app\build\outputs\apk\release\app-release.apk'))
$ErrorActionPreference='Stop'
if(-not(Test-Path -LiteralPath $ApkPath -PathType Leaf)){throw "请先编译 APK：$ApkPath"}
$adb=(Get-Command adb -ErrorAction Stop).Source
& $adb connect ($TvHost+':5555')
if($LASTEXITCODE -ne 0){throw 'ADB 连接失败'}
$serial=$TvHost+':5555'
& $adb -s $serial install -r $ApkPath
if($LASTEXITCODE -ne 0){throw 'APK 安装失败'}
$commands=@(
 @('appops','set','com.bytv.launcher','SYSTEM_ALERT_WINDOW','allow'),
 @('appops','set','com.bytv.launcher','GET_USAGE_STATS','allow'),
 @('pm','grant','com.bytv.launcher','android.permission.DUMP'),
 @('pm','grant','com.bytv.launcher','android.permission.WRITE_SECURE_SETTINGS')
)
foreach($command in $commands){& $adb -s $serial shell @command;if($LASTEXITCODE -ne 0){Write-Warning ('权限配置失败：'+($command -join ' '))}}
& $adb -s $serial shell am start -n com.bytv.launcher/com.gathin.tvhome.MainActivity
Write-Host '安装完成。请在电视系统中选择默认桌面；此脚本不会禁用原厂桌面。'

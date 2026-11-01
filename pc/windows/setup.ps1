# SIM切替 導入スクリプト（Windows）
#  - ADBを探す（無ければGoogle公式のものをダウンロード）
#  - Shizuku と SIM切替 の最新版をGitHubからダウンロードしてスマホ／タブレットに入れる
#  - Shizuku をADBで起動する
#  -StartOnly を付けると Shizuku の起動だけ行う
param([switch]$StartOnly)

# adb はメッセージをエラー出力に出すので、止まらないよう Continue にしておく
$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$Base = $PSScriptRoot
$ApkDir = Join-Path $Base 'apk'

function Say($m, $c = 'White') { Write-Host $m -ForegroundColor $c }
function Fail($m) { Say "`n[失敗] $m" Red; Read-Host "`nEnterで閉じます"; exit 1 }

# ---------- 1. ADB ----------
function Find-Adb {
  $c = Get-Command adb -ErrorAction SilentlyContinue
  if ($c) { return $c.Source }
  $cands = @(
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "$env:USERPROFILE\platform-tools\adb.exe",
    "C:\platform-tools\adb.exe",
    "C:\adb\adb.exe",
    "$env:USERPROFILE\tools\_portable\platform-tools\adb.exe",
    "$env:USERPROFILE\scoop\apps\adb\current\platform-tools\adb.exe"
  )
  foreach ($p in $cands) { if (Test-Path $p) { return $p } }
  foreach ($d in @("$env:USERPROFILE\tools", "$env:USERPROFILE\Downloads", "$env:USERPROFILE\Documents", "$env:USERPROFILE\Desktop", "C:\tools")) {
    if (Test-Path $d) {
      $f = Get-ChildItem $d -Filter adb.exe -Recurse -Depth 4 -ErrorAction SilentlyContinue | Select-Object -First 1
      if ($f) { return $f.FullName }
    }
  }
  return $null
}

Say "=== SIM切替 導入 ===" Cyan
$adb = Find-Adb
if (-not $adb) {
  Say "ADBが見つからないので、Google公式のADB（platform-tools）をダウンロードします…" Yellow
  $dest = "$env:USERPROFILE\tools\_portable"
  New-Item -ItemType Directory -Force $dest | Out-Null
  $zip = Join-Path $dest 'platform-tools.zip'
  try {
    Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip -UseBasicParsing -ErrorAction Stop
    Expand-Archive $zip -DestinationPath $dest -Force -ErrorAction Stop
  } catch { Fail "ADBのダウンロードに失敗しました：$($_.Exception.Message)" }
  Remove-Item $zip
  $adb = Join-Path $dest 'platform-tools\adb.exe'
  if (-not (Test-Path $adb)) { Fail "ADBの準備ができませんでした" }
}
Say "ADB: $adb" Gray
& $adb start-server 2>&1 | Out-Null

# ---------- 2. スマホ／タブレットをつなぐ ----------
Say "`nスマホ／タブレットをUSBでつないでください。" White
Say "スマホ／タブレットに「USBデバッグを許可しますか？」が出たら「このパソコンからのUSBデバッグを常に許可」にチェックして「許可」。" White
$serial = $null
$deadline = (Get-Date).AddMinutes(3)
$warned = $false
while ((Get-Date) -lt $deadline) {
  $lines = (& $adb devices) -split "`r?`n" | Where-Object { $_ -match "`t" }
  $ok = @($lines | Where-Object { $_ -match "`tdevice$" } | ForEach-Object { ($_ -split "`t")[0] })
  $unauth = @($lines | Where-Object { $_ -match "`tunauthorized$" })
  if ($ok.Count -eq 1) { $serial = $ok[0]; break }
  if ($ok.Count -gt 1) {
    Say "`n複数の端末がつながっています。どれに入れますか？" Yellow
    for ($i = 0; $i -lt $ok.Count; $i++) {
      $model = (& $adb -s $ok[$i] shell getprop ro.product.model).Trim()
      Say ("  {0}: {1}（{2}）" -f ($i + 1), $model, $ok[$i])
    }
    $n = 0
    while (-not ([int]::TryParse((Read-Host "番号を入力"), [ref]$n) -and $n -ge 1 -and $n -le $ok.Count)) { Say "1〜$($ok.Count) の番号を入れてください" Yellow }
    $serial = $ok[$n - 1]
    break
  }
  if ($unauth.Count -and -not $warned) { Say "→ スマホ／タブレットの画面で「許可」を押してください" Yellow; $warned = $true }
  Start-Sleep -Seconds 2
}
if (-not $serial) {
  Fail "スマホ／タブレットが見つかりません。`n  ・設定 → スマホ／タブレット情報 → ビルド番号を7回タップ → 開発者向けオプション → USBデバッグをON`n  ・USBケーブルがデータ通信対応か確認`nしてから、もう一度実行してください。"
}
$model = (& $adb -s $serial shell getprop ro.product.model).Trim()
$sdk = (& $adb -s $serial shell getprop ro.build.version.release).Trim()
Say "`nつながりました：$model（Android $sdk）" Green

# ---------- 3. アプリを入れる ----------
function Get-LatestApk($repo, $name) {
  Say "$name の最新版を確認中…" Gray
  try { $r = Invoke-RestMethod "https://api.github.com/repos/$repo/releases/latest" -Headers @{ 'User-Agent' = 'sim-kirikae-setup' } -ErrorAction Stop }
  catch { Fail "$name の情報を取得できませんでした：$($_.Exception.Message)" }
  $a = $r.assets | Where-Object { $_.name -like '*.apk' } | Select-Object -First 1
  if (-not $a) { Fail "$name のAPKが見つかりませんでした（$repo）" }
  New-Item -ItemType Directory -Force $ApkDir | Out-Null
  # 版ごとに別名で保存（同じ名前でも新しい版を取り直す）
  $out = Join-Path $ApkDir ("{0}_{1}_{2}" -f $repo.Replace('/', '_'), (([string]$a.id) + '-' + ([string]$a.size)), $a.name)
  if (-not (Test-Path $out)) {
    Say "  ダウンロード：$($a.name)（$($r.tag_name)）" Gray
    try { Invoke-WebRequest $a.browser_download_url -OutFile $out -UseBasicParsing -ErrorAction Stop }
    catch { if (Test-Path $out) { Remove-Item $out }; Fail "$name のダウンロードに失敗しました：$($_.Exception.Message)" }
  }
  return $out
}
function Install-Apk($apk, $name, $pkg) {
  Say "$name を入れています…" White
  $res = (& $adb -s $serial install -r $apk 2>&1) -join "`n"
  if ($res -match 'UPDATE_INCOMPATIBLE|signatures do not match' -and $pkg) {
    # 署名の違う古い版が入っているときは、消してから入れ直す（設定は残らないアプリなので影響なし）
    Say "  古い版と署名が違うので、いったん削除して入れ直します" Yellow
    & $adb -s $serial uninstall $pkg | Out-Null
    $res = (& $adb -s $serial install $apk 2>&1) -join "`n"
  }
  if ($res -notmatch 'Success') { Fail "$name を入れられませんでした：`n$res" }
  Say "  $name：OK" Green
}

if (-not $StartOnly) {
  $shizuku = Get-LatestApk 'RikkaApps/Shizuku' 'Shizuku'
  $app = Get-LatestApk 'hakusyaku-lab/sim-kirikae' 'SIM切替'
  Install-Apk $shizuku 'Shizuku' $null
  Install-Apk $app 'SIM切替' 'jp.hakusyaku.simkirikae'
}

# ---------- 4. Shizukuを起動 ----------
Say "`nShizukuを起動しています…" White
$pkg = (& $adb -s $serial shell pm path moe.shizuku.privileged.api) -join ''
if ($pkg -notmatch 'package:') { Fail "Shizukuが入っていません。先に「1_導入する.bat」を実行してください" }
$cmd = 'p=$(pm path moe.shizuku.privileged.api | head -n 1 | sed -e s/^package:// -e s#/base.apk##); for a in arm64 arm x86_64 x86; do if [ -f $p/lib/$a/libshizuku.so ]; then $p/lib/$a/libshizuku.so; exit; fi; done; echo NOTFOUND'
$out = (& $adb -s $serial shell $cmd 2>&1) -join "`n"
Say $out Gray
if ($out -match 'NOTFOUND') { Fail "Shizukuの起動ファイルが見つかりませんでした。スマホ／タブレットでShizukuアプリを開き、「ADBで起動」に表示されるコマンドを教えてください" }
Start-Sleep -Seconds 2
$running = (& $adb -s $serial shell 'ps -A | grep -c shizuku_server' 2>&1) -join ''
if ($running.Trim() -match '^[1-9]') { Say "Shizuku：起動しました" Green } else { Say "Shizukuが起動したか確認できませんでした。スマホ／タブレットでShizukuを開き「実行中」になっているか見てください" Yellow }

if (-not $StartOnly) {
  Say "`n=== PCでの作業は完了です。ここからはスマホ／タブレットで ===" Cyan
  & $adb -s $serial shell am start -n jp.hakusyaku.simkirikae/.MainActivity | Out-Null
  Say "1.「SIM切替」アプリが開きます。Shizukuの許可が出たら「常に許可」"
  Say "2. アプリの「SIM①」「SIM②」ボタンで切り替え"
  Say "3. アイコン長押しで出るショートカットをホーム画面に置くと1タップで切り替え"
  Say "4. クイック設定の編集から「データ SIM①」「データ SIM②」タイルも追加できます"
  Say "`n再起動したときは、Shizukuの起動だけやり直しが必要です（「2_Shizukuを起動する.bat」）" Yellow
} else {
  Say "`n完了。タイルで切り替えられます。" Cyan
}
Read-Host "`nEnterで閉じます"

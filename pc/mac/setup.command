#!/bin/bash
# SIM切替 導入スクリプト（Mac / Linux）
#   ./setup.command          … Shizuku と SIM切替 を入れて、Shizuku を起動
#   ./setup.command start    … Shizuku の起動だけ（再起動後に使う）
# Mac ではダブルクリックで実行できます（初回は右クリック→開く）。
set -u
cd "$(dirname "$0")"
say() { printf '%s\n' "$*"; }
fail() { say ""; say "[失敗] $*"; read -r -p "Enterで閉じます" _; exit 1; }

# 1. ADB
ADB="$(command -v adb || true)"
if [ -z "$ADB" ]; then
  say "ADBが見つからないので、Google公式のADB（platform-tools）をダウンロードします…"
  case "$(uname -s)" in
    Darwin) OS=darwin ;; Linux) OS=linux ;; *) fail "この OS には対応していません" ;;
  esac
  mkdir -p "$HOME/.sim-kirikae" && cd "$HOME/.sim-kirikae" || fail "作業フォルダを作れません"
  curl -fsSL -o pt.zip "https://dl.google.com/android/repository/platform-tools-latest-$OS.zip" || fail "ADBのダウンロードに失敗しました"
  unzip -oq pt.zip && rm -f pt.zip
  ADB="$HOME/.sim-kirikae/platform-tools/adb"
  cd - >/dev/null
fi
"$ADB" start-server >/dev/null 2>&1

# 2. 端末をつなぐ
say "スマホ／タブレットをUSBでつないでください。「USBデバッグを許可しますか？」が出たら「常に許可」にチェックして「許可」。"
SERIAL=""
for _ in $(seq 1 90); do
  LIST="$("$ADB" devices | awk -F'\t' 'NR>1 && $2=="device"{print $1}')"
  N=$(printf '%s\n' "$LIST" | grep -c . || true)
  if [ "$N" -eq 1 ]; then SERIAL="$LIST"; break; fi
  if [ "$N" -gt 1 ]; then
    say "複数の端末がつながっています。番号を選んでください："
    i=1; for s in $LIST; do say "  $i: $("$ADB" -s "$s" shell getprop ro.product.model | tr -d '\r')（$s）"; i=$((i+1)); done
    read -r -p "番号: " k; SERIAL="$(printf '%s\n' "$LIST" | sed -n "${k}p")"; break
  fi
  sleep 2
done
[ -n "$SERIAL" ] || fail "端末が見つかりません。設定→端末情報→ビルド番号を7回タップ→開発者向けオプション→USBデバッグをON にしてください"
A() { "$ADB" -s "$SERIAL" "$@"; }
say "つながりました：$(A shell getprop ro.product.model | tr -d '\r')"

latest_apk() { # $1=owner/repo
  curl -fsSL "https://api.github.com/repos/$1/releases/latest" | grep -o '"browser_download_url": *"[^"]*\.apk"' | head -n1 | sed 's/.*"\(http[^"]*\)"/\1/'
}
install_apk() { # $1=URL $2=名前 $3=パッケージ名
  local f; f="$(mktemp -t simkirikae.XXXXXX).apk"
  curl -fsSL -o "$f" "$1" || fail "$2 のダウンロードに失敗しました"
  local r; r="$(A install -r "$f" 2>&1)"
  if printf '%s' "$r" | grep -qE 'UPDATE_INCOMPATIBLE|signatures do not match' && [ -n "$3" ]; then
    say "  古い版と署名が違うので、いったん削除して入れ直します"
    A uninstall "$3" >/dev/null; r="$(A install "$f" 2>&1)"
  fi
  rm -f "$f"
  printf '%s' "$r" | grep -q Success || fail "$2 を入れられませんでした: $r"
  say "  $2：OK"
}

# 3. アプリを入れる
if [ "${1:-}" != "start" ]; then
  U1="$(latest_apk RikkaApps/Shizuku)"; [ -n "$U1" ] || fail "Shizuku の最新版が見つかりません"
  U2="$(latest_apk hakusyaku-lab/sim-kirikae)"; [ -n "$U2" ] || fail "SIM切替 の最新版が見つかりません"
  install_apk "$U1" Shizuku ""
  install_apk "$U2" SIM切替 jp.hakusyaku.simkirikae
fi

# 4. Shizuku を起動
A shell pm path moe.shizuku.privileged.api | grep -q package: || fail "Shizukuが入っていません"
OUT="$(A shell 'p=$(pm path moe.shizuku.privileged.api | head -n 1 | sed -e s/^package:// -e s#/base.apk##); for a in arm64 arm x86_64 x86; do if [ -f $p/lib/$a/libshizuku.so ]; then $p/lib/$a/libshizuku.so; exit; fi; done; echo NOTFOUND' 2>&1)"
printf '%s\n' "$OUT" | tail -n 3
printf '%s' "$OUT" | grep -q NOTFOUND && fail "Shizukuの起動ファイルが見つかりません"
say "Shizuku：起動しました"
[ "${1:-}" != "start" ] && A shell am start -n jp.hakusyaku.simkirikae/.MainActivity >/dev/null 2>&1
say ""
say "完了。端末で「SIM切替」を開き、Shizukuの許可が出たら「常に許可」を押してください。"
read -r -p "Enterで閉じます" _

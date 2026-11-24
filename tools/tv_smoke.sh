#!/usr/bin/env bash
# Walks the TV build through first-run setup on an emulator, saving a screenshot and the
# on-screen text for every step. Usage: tools/tv_smoke.sh <apk> [output-dir]
set -u
APK="$1"
OUT="${2:-tv-smoke}"
PKG=com.craftworks.music.tvcustom
mkdir -p "$OUT"

step() {
  sleep "${2:-2}"
  adb exec-out screencap -p > "$OUT/$1.png"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 &&
    adb exec-out cat /sdcard/ui.xml | grep -o 'text="[^"]\+"' | sort -u > "$OUT/$1.txt"
  echo "== $1"; cat "$OUT/$1.txt" 2>/dev/null | head -40
}
key() { for k in "$@"; do adb shell input keyevent "$k"; sleep 0.8; done; }

adb install -r "$APK" || exit 1
adb shell cmd locale set-app-locales "$PKG" --locales zh-CN || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb shell pm grant "$PKG" android.permission.READ_MEDIA_AUDIO || true
adb logcat -c
adb shell am start -n "$PKG/com.craftworks.music.MainActivity"

step 01-first-run 10
key DPAD_DOWN DPAD_CENTER
step 02-sources
key DPAD_DOWN
step 03-add-server-focused
key DPAD_CENTER
step 04-server-form 3

# A wrong address first: the form must explain itself instead of doing nothing.
adb shell input text "999.invalid"
key DPAD_DOWN
adb shell input text "demo"
key DPAD_DOWN
adb shell input text "demo"
key DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_RIGHT
step 05-ready-to-connect
key DPAD_CENTER
step 06-unreachable-address 12

# Then the public Navidrome demo server.
key DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_UP
for _ in $(seq 12); do adb shell input keyevent DEL; done
adb shell input text "https://demo.navidrome.org"
key DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_RIGHT
step 07-demo-address
key DPAD_CENTER
step 08-after-connect 15

key DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_CENTER
step 09-setup-done 3
key DPAD_CENTER
step 10-home 15
key DPAD_DOWN DPAD_DOWN
step 11-home-row 3
key DPAD_LEFT DPAD_LEFT DPAD_LEFT
step 12-navigation 2
key DPAD_DOWN DPAD_CENTER DPAD_RIGHT
step 13-albums 8

adb logcat -d -s AndroidRuntime:E > "$OUT/crash.txt"
adb logcat -d | grep -E "NAVIDROME|$PKG" | tail -200 > "$OUT/app-log.txt"
if grep -q "FATAL EXCEPTION" "$OUT/crash.txt"; then
  echo "The app crashed:"; cat "$OUT/crash.txt"; exit 1
fi

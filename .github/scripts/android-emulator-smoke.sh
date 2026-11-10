#!/usr/bin/env bash
set -euo pipefail

# The emulator action runs each inline YAML command in a separate shell.
# Keep this sequence in a single script so the APK path remains available.
package='app.hiddify.com'
activity='com.hiddify.hiddify.MainActivity'

adb wait-for-device
booted=false
for attempt in {1..90}; do
  if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == '1' ]]; then
    booted=true
    break
  fi
  sleep 2
done
if [[ "$booted" != true ]]; then
  echo 'ERROR: emulator did not complete boot' >&2
  exit 1
fi

mapfile -t apks < <(find integration-apk -type f -name '*.apk' -print)
if (( ${#apks[@]} != 1 )); then
  echo "ERROR: expected exactly one downloaded APK, found ${#apks[@]}" >&2
  find integration-apk -maxdepth 3 -type f -print >&2
  exit 1
fi
adb install -r "${apks[0]}"

adb logcat -c
adb shell am start -W -n "$package/$activity"

original_pid=''
assert_app_healthy() {
  local pid=''
  for attempt in {1..20}; do
    pid="$(adb shell pidof -s "$package" 2>/dev/null | tr -d '\r' || true)"
    if [[ "$pid" =~ ^[0-9]+$ ]]; then break; fi
    sleep 2
  done
  if [[ ! "$pid" =~ ^[0-9]+$ ]]; then
    echo "ERROR: $package is not running" >&2
    exit 1
  fi
  if [[ -n "$original_pid" && "$pid" != "$original_pid" ]]; then
    echo "ERROR: $package restarted unexpectedly ($original_pid -> $pid)" >&2
    exit 1
  fi
  original_pid="$pid"

  if adb logcat -d -v brief -s AndroidRuntime:E | awk -v target="Process: $package" '
    /FATAL EXCEPTION/ { fatal = 1 }
    /Process: / { if (fatal && index($0, target)) crashed = 1; fatal = 0 }
    END { exit !crashed }
  '; then
    echo "ERROR: FATAL EXCEPTION observed for $package" >&2
    exit 1
  fi
  if ! adb shell dumpsys activity activities | grep -Fq "$package"; then
    echo "ERROR: no activity found for $package after launch" >&2
    exit 1
  fi
}

wait_for_wifi_state() {
  local expected="$1"
  for attempt in {1..20}; do
    if adb shell cmd wifi status | tr '[:upper:]' '[:lower:]' | grep -Eq "wi-?fi is $expected"; then
      return 0
    fi
    sleep 2
  done
  echo "ERROR: emulator Wi-Fi did not become $expected" >&2
  exit 1
}

assert_app_healthy
adb shell svc wifi disable
wait_for_wifi_state disabled
assert_app_healthy
adb shell svc wifi enable
wait_for_wifi_state enabled
assert_app_healthy
echo 'PASS: APK launch and Wi-Fi handover smoke check'

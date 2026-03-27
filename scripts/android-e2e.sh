#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="$ROOT_DIR/src"
E2E_DIR="$ROOT_DIR/e2e"
ARTIFACT_ROOT="$ROOT_DIR/artifacts/android-e2e"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
RUN_DIR="$ARTIFACT_ROOT/$RUN_ID"

APP_ID="com.franklinharper.social.media.client"
MAIN_ACTIVITY="$APP_ID/.MainActivity"
SERVER_HEALTH_URL="http://127.0.0.1:8080/health"
FIXTURE_URL="http://10.0.2.2:9090/feeds/hn-frontpage.xml"
FIXTURE_HEALTH_URL="http://127.0.0.1:9090/health"
AVD_NAME="${ANDROID_E2E_AVD_NAME:-AndroidE2E_API35_Pixel5}"
DEVICE_PROFILE="${ANDROID_E2E_DEVICE_PROFILE:-pixel_5}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
ADB_BIN="${ADB:-$ANDROID_SDK_ROOT/platform-tools/adb}"
EMULATOR_BIN="${EMULATOR:-$ANDROID_SDK_ROOT/emulator/emulator}"
NODE_BIN="${NODE:-node}"

ANDROID_SERIAL=""
EMULATOR_PORT=""
SERVER_PID=""
FIXTURE_PID=""
STARTED_SERVER=0
STARTED_FIXTURE=0
STARTED_EMULATOR=0
STAGE="preflight"
TEST_RAN=0
RUN_STATUS=0

mkdir -p "$RUN_DIR"

require_tool() {
  local tool_path="$1"
  local tool_name="$2"
  if [[ "$tool_path" = */* ]]; then
    if [[ -x "$tool_path" ]]; then
      return 0
    fi
    echo "$tool_name not found at: $tool_path" >&2
    exit 1
  fi

  if command -v "$tool_path" >/dev/null 2>&1; then
    return 0
  fi

  echo "$tool_name not found at: $tool_path" >&2
  exit 1
}

resolve_android_sdk_tool() {
  local env_override="$1"
  local tool_name="$2"
  if [[ -n "$env_override" ]]; then
    echo "$env_override"
    return 0
  fi

  echo "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/$tool_name"
}

run_with_yes() {
  local response="$1"
  shift
  set +o pipefail
  printf '%s\n' "$response" | "$@"
  local status=$?
  set -o pipefail
  return "$status"
}

http_ready() {
  local url="$1"
  local max_attempts="${2:-60}"
  local sleep_seconds="${3:-2}"
  local attempt=1
  while [[ "$attempt" -le "$max_attempts" ]]; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$sleep_seconds"
    attempt=$((attempt + 1))
  done
  return 1
}

device_ready() {
  local serial="$1"
  local boot_state
  boot_state="$("$ADB_BIN" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  [[ "$boot_state" == "1" ]]
}

device_visible() {
  local serial="$1"
  "$ADB_BIN" devices | awk -v target="$serial" 'NR > 1 && $1 == target && $2 == "device" { found = 1 } END { exit(found ? 0 : 1) }'
}

wait_for_device() {
  local serial="$1"
  local attempt=1
  while [[ "$attempt" -le 120 ]]; do
    if device_visible "$serial" && device_ready "$serial"; then
      return 0
    fi
    sleep 2
    attempt=$((attempt + 1))
  done
  return 1
}

choose_emulator_port() {
  local port
  for port in $(seq 5554 2 5584); do
    if ! "$ADB_BIN" devices | awk -v target="emulator-$port" 'NR > 1 && $1 == target { found = 1 } END { exit(found ? 0 : 1) }'; then
      if ! lsof -nP -iTCP:"$port" >/dev/null 2>&1; then
        echo "$port"
        return 0
      fi
    fi
  done
  return 1
}

ensure_system_image() {
  local arch image_package image_dir
  arch="$(uname -m)"
  case "$arch" in
    arm64)
      image_package="system-images;android-35;google_apis;arm64-v8a"
      image_dir="$ANDROID_SDK_ROOT/system-images/android-35/google_apis/arm64-v8a"
      ;;
    x86_64)
      image_package="system-images;android-35;google_apis;x86_64"
      image_dir="$ANDROID_SDK_ROOT/system-images/android-35/google_apis/x86_64"
      ;;
    *)
      echo "Unsupported host architecture for Android E2E: $arch" >&2
      exit 1
      ;;
  esac

  if [[ ! -d "$image_dir" ]]; then
    run_with_yes y "$SDKMANAGER_BIN" --install "$image_package"
  fi

  echo "$image_package"
}

ensure_avd() {
  local system_image="$1"
  if ! "$EMULATOR_BIN" -list-avds | rg -x "$AVD_NAME" >/dev/null 2>&1; then
    run_with_yes no "$AVDMANAGER_BIN" create avd --force --name "$AVD_NAME" --package "$system_image" --device "$DEVICE_PROFILE"
  fi
}

start_background_if_needed() {
  local label="$1"
  local ready_url="$2"
  local command_dir="$3"
  local log_file="$4"
  shift 4

  if http_ready "$ready_url" 1 1; then
    return 0
  fi

  (
    cd "$command_dir"
    "$@"
  ) >"$log_file" 2>&1 &

  local pid=$!
  case "$label" in
    server)
      SERVER_PID="$pid"
      STARTED_SERVER=1
      ;;
    fixture)
      FIXTURE_PID="$pid"
      STARTED_FIXTURE=1
      ;;
  esac
}

pull_run_as_file() {
  local serial="$1"
  local remote_path="$2"
  local local_path="$3"
  if "$ADB_BIN" -s "$serial" shell run-as "$APP_ID" cat "$remote_path" >"$local_path" 2>/dev/null; then
    return 0
  fi
  rm -f "$local_path"
  return 1
}

collect_android_test_outputs() {
  local results_root results_dir
  results_root="$SRC_DIR/androidApp/build/outputs/androidTest-results/connected/debug"
  if [[ -d "$results_root" ]]; then
    results_dir="$(find "$results_root" -mindepth 1 -maxdepth 1 -type d -print0 | while IFS= read -r -d '' candidate; do
      if [[ -f "$candidate/test-result.textproto" ]] && rg -q "id: \"$ANDROID_SERIAL\"" "$candidate/test-result.textproto"; then
        printf '%s\n' "$candidate"
        break
      fi
    done)"
    if [[ -n "${results_dir:-}" && -d "$results_dir" ]]; then
      rm -rf "$RUN_DIR/androidTest-results"
      cp -R "$results_dir" "$RUN_DIR/androidTest-results"
    fi
  fi
}

collect_app_artifacts() {
  if [[ -n "$ANDROID_SERIAL" ]]; then
    pull_run_as_file "$ANDROID_SERIAL" "files/android-e2e-progress.json" "$RUN_DIR/android-e2e-progress.json" || true
    pull_run_as_file "$ANDROID_SERIAL" "files/android-e2e-report.json" "$RUN_DIR/android-e2e-report.json" || true
    "$ADB_BIN" -s "$ANDROID_SERIAL" logcat -d -v threadtime >"$RUN_DIR/logcat.txt" 2>/dev/null || true
  fi
  collect_android_test_outputs
}

cleanup() {
  if [[ "$STARTED_FIXTURE" -eq 1 && -n "$FIXTURE_PID" ]]; then
    kill "$FIXTURE_PID" >/dev/null 2>&1 || true
    wait "$FIXTURE_PID" >/dev/null 2>&1 || true
  fi

  if [[ "$STARTED_SERVER" -eq 1 && -n "$SERVER_PID" ]]; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
    wait "$SERVER_PID" >/dev/null 2>&1 || true
  fi

  if [[ "$STARTED_EMULATOR" -eq 1 && -n "$ANDROID_SERIAL" ]]; then
    "$ADB_BIN" -s "$ANDROID_SERIAL" emu kill >/dev/null 2>&1 || true
  fi
}

generate_report() {
  local exit_code="$1"
  RUN_STATUS="$exit_code"
  export ANDROID_E2E_EXIT_CODE="$RUN_STATUS"
  export ANDROID_E2E_STAGE="$STAGE"
  export ANDROID_E2E_RUN_ID="$RUN_ID"
  export ANDROID_E2E_RUN_DIR="$RUN_DIR"
  export ANDROID_E2E_APP_ID="$APP_ID"
  export ANDROID_E2E_MAIN_ACTIVITY="$MAIN_ACTIVITY"
  export ANDROID_E2E_SERVER_HEALTH_URL="$SERVER_HEALTH_URL"
  export ANDROID_E2E_FIXTURE_HEALTH_URL="$FIXTURE_HEALTH_URL"
  export ANDROID_E2E_FIXTURE_URL="$FIXTURE_URL"
  export ANDROID_E2E_DEVICE_SERIAL="$ANDROID_SERIAL"
  export ANDROID_E2E_AVD_NAME="$AVD_NAME"
  export ANDROID_E2E_DEVICE_PROFILE="$DEVICE_PROFILE"
  export ANDROID_E2E_STARTED_SERVER="$STARTED_SERVER"
  export ANDROID_E2E_STARTED_FIXTURE="$STARTED_FIXTURE"
  export ANDROID_E2E_STARTED_EMULATOR="$STARTED_EMULATOR"

  "$NODE_BIN" "$ROOT_DIR/scripts/android-e2e-report.js" "$RUN_DIR"
}

on_exit() {
  local exit_code="$1"
  collect_app_artifacts || true
  generate_report "$exit_code" || true
  cleanup || true
}

trap 'on_exit "$?"' EXIT

SDKMANAGER_BIN="$(resolve_android_sdk_tool "${SDKMANAGER:-}" sdkmanager)"
AVDMANAGER_BIN="$(resolve_android_sdk_tool "${AVDMANAGER:-}" avdmanager)"

require_tool "$ADB_BIN" adb
require_tool "$EMULATOR_BIN" emulator
require_tool "$SDKMANAGER_BIN" sdkmanager
require_tool "$AVDMANAGER_BIN" avdmanager
require_tool "$NODE_BIN" node

cd "$ROOT_DIR"

system_image="$(ensure_system_image)"
ensure_avd "$system_image"

STAGE="start_fixture"
start_background_if_needed fixture "$FIXTURE_HEALTH_URL" "$E2E_DIR" "$RUN_DIR/fixture-server.log" node scripts/fixture-server.js
if [[ "$STARTED_FIXTURE" -eq 1 ]]; then
  http_ready "$FIXTURE_HEALTH_URL" 60 1
fi

STAGE="start_server"
start_background_if_needed server "$SERVER_HEALTH_URL" "$SRC_DIR" "$RUN_DIR/server.log" ./gradlew :server:run --no-daemon
if [[ "$STARTED_SERVER" -eq 1 ]]; then
  http_ready "$SERVER_HEALTH_URL" 60 2
fi

STAGE="start_emulator"
EMULATOR_PORT="$(choose_emulator_port)"
ANDROID_SERIAL="emulator-$EMULATOR_PORT"
export ANDROID_SERIAL
export ANDROID_E2E_DEVICE_SERIAL="$ANDROID_SERIAL"
"$EMULATOR_BIN" -avd "$AVD_NAME" -port "$EMULATOR_PORT" -no-window -no-audio -no-boot-anim -no-snapshot -no-snapshot-save -gpu swiftshader_indirect -accel auto -wipe-data >"$RUN_DIR/emulator.log" 2>&1 &
EMULATOR_PID=$!
STARTED_EMULATOR=1
wait_for_device "$ANDROID_SERIAL"

STAGE="fresh_install"
"$ADB_BIN" -s "$ANDROID_SERIAL" uninstall "$APP_ID" >/dev/null 2>&1 || true

STAGE="run_tests"
TEST_RAN=1
export ANDROID_E2E_TEST_RAN="$TEST_RAN"
cd "$SRC_DIR"
ANDROID_SERIAL="$ANDROID_SERIAL" ./gradlew :androidApp:connectedDebugAndroidTest --no-daemon | tee "$RUN_DIR/gradle.log"

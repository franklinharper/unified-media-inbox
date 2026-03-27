#!/bin/zsh

set -euo pipefail

APP_ID="com.franklinharper.social.media.client"
MAIN_ACTIVITY="${APP_ID}/.MainActivity"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SRC_DIR="${ROOT_DIR}/src"
ADB_BIN="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"

if ! command -v "$ADB_BIN" >/dev/null 2>&1; then
  echo "adb not found at: $ADB_BIN" >&2
  exit 1
fi

cd "$SRC_DIR"

"$ADB_BIN" uninstall "$APP_ID" >/dev/null 2>&1 || true
./gradlew :androidApp:installDebug --no-daemon
"$ADB_BIN" shell am start -n "$MAIN_ACTIVITY"

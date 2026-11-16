#!/usr/bin/env bash
# run-mac-emulator.sh — Launches interactive GUI emulator on macOS for visual validation
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

AVD_NAME="${AVD_NAME:-hermes_test_avd}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

echo "==> Checking for attached emulator..."
DEV="$(adb devices | grep -E '^emulator-[0-9]+[[:space:]]+device' | head -n1 | awk '{print $1}' || true)"

if [ -z "$DEV" ]; then
    echo "==> Launching interactive GUI emulator: $AVD_NAME on macOS desktop..."
    emulator -avd "$AVD_NAME" -gpu host &
    
    echo "==> Waiting for device boot..."
    adb wait-for-device
    while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
        sleep 1
    done
    DEV="$(adb devices | grep -E '^emulator-[0-9]+[[:space:]]+device' | head -n1 | awk '{print $1}')"
fi

echo "==> Device online: $DEV"
echo "==> Assembling debug APK..."
./gradlew assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
echo "==> Installing $APK to $DEV..."
adb -s "$DEV" install -r "$APK"

echo "==> Launching Hermes Mobile MainActivity..."
adb -s "$DEV" shell am start -n ai.hermes.mobile.debug/ai.hermes.mobile.MainActivity

echo ""
echo "✓ Hermes Mobile is now running in your Mac desktop emulator window."
echo "  You can interact with the WebUI, pull open the PTY terminal drawer, and test foldable postures directly."

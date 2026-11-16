#!/usr/bin/env bash
# run-device-tests.sh - Emulator lab runner with full telemetry capture.
# Every run writes an artifact bundle to build/lab-runs/<timestamp>/ and a
# machine-readable summary.json so headless agents can diagnose without re-running.
# Exit code: 0 = all checks pass, 1 = any check failed, 2 = infra failure.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

AVD_NAME="${AVD_NAME:-hermes_test_avd}"
TARGET_PKG="ai.hermes.mobile.debug"
TARGET_ACTIVITY="ai.hermes.mobile.MainActivity"
EMU_PORT=5554
PORT_FORWARD=8888
EMU_PID=""
TARGET_DEVICE=""
LOGCAT_PID=""
RUN_START="$(date +%s)"

RUN_ID="$(date +%Y%m%d-%H%M%S)"
ART="$PROJECT_ROOT/build/lab-runs/$RUN_ID"
mkdir -p "$ART"
ln -sfn "$ART" "$PROJECT_ROOT/build/lab-runs/latest"
exec > >(tee -a "$ART/runner.log") 2>&1

# check results: name|status|seconds|detail
CHECKS=()
FAILED=0
STEP_T0=0

log() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] [LAB] $*"; }
step() { STEP_T0="$(date +%s)"; log "== $* =="; }
record() { # name status detail
    local dt=$(( $(date +%s) - STEP_T0 ))
    CHECKS+=("$1|$2|$dt|$3")
    log "[$2] $1 (${dt}s) $3"
    [[ "$2" == "FAIL" ]] && FAILED=1 || true
}
adbt() { adb -s "$TARGET_DEVICE" "$@"; }

snapshot() { # label
    [[ -z "$TARGET_DEVICE" ]] && return 0
    local d="$ART/snap-$1"; mkdir -p "$d"
    adbt shell dumpsys activity services "$TARGET_PKG" > "$d/services.txt" 2>&1 || true
    adbt shell dumpsys activity processes "$TARGET_PKG" > "$d/processes.txt" 2>&1 || true
    adbt shell dumpsys meminfo "$TARGET_PKG" > "$d/meminfo.txt" 2>&1 || true
    adbt shell dumpsys notification --noredact > "$d/notifications.txt" 2>&1 || true
    adbt shell cmd appops get "$TARGET_PKG" > "$d/appops.txt" 2>&1 || true
    adbt shell ss -ltnp > "$d/listening-sockets.txt" 2>&1 || adbt shell netstat -ltn > "$d/listening-sockets.txt" 2>&1 || true
    adbt shell ps -A -o PID,PPID,USER,RSS,NAME > "$d/ps.txt" 2>&1 || true
    adbt exec-out screencap -p > "$d/screen.png" 2>/dev/null || true
    adbt shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adbt pull /sdcard/ui.xml "$d/ui.xml" >/dev/null 2>&1 || true
}

write_summary() {
    local total=$(( $(date +%s) - RUN_START ))
    {
        echo '{'
        echo "  \"run_id\": \"$RUN_ID\","
        echo "  \"git_sha\": \"$(git -C "$PROJECT_ROOT" rev-parse --short HEAD 2>/dev/null)\","
        echo "  \"git_dirty\": $( [[ -n "$(git -C "$PROJECT_ROOT" status --porcelain 2>/dev/null)" ]] && echo true || echo false ),"
        echo "  \"device\": \"$TARGET_DEVICE\","
        echo "  \"duration_s\": $total,"
        echo "  \"result\": \"$( [[ $FAILED -eq 0 ]] && echo PASS || echo FAIL )\","
        echo '  "checks": ['
        local i=0 n=${#CHECKS[@]}
        for c in "${CHECKS[@]}"; do
            IFS='|' read -r name st dt detail <<< "$c"
            detail="${detail//\\/\\\\}"; detail="${detail//\"/\\\"}"
            i=$((i+1))
            printf '    {"name": "%s", "status": "%s", "seconds": %s, "detail": "%s"}%s\n' "$name" "$st" "$dt" "$detail" "$( [[ $i -lt $n ]] && echo , )"
        done
        echo '  ],'
        echo "  \"artifacts\": \"$ART\""
        echo '}'
    } > "$ART/summary.json"
}

cleanup() {
    local rc=$?
    set +e
    if [[ -n "$TARGET_DEVICE" ]]; then
        snapshot final
        adbt logcat -d -v threadtime > "$ART/logcat-full.txt" 2>&1
        adbt logcat -d -b crash > "$ART/logcat-crash.txt" 2>&1
        adbt shell dumpsys dropbox --print > "$ART/dropbox.txt" 2>&1
        adbt pull /data/anr "$ART/anr" >/dev/null 2>&1
    fi
    [[ -n "$LOGCAT_PID" ]] && kill "$LOGCAT_PID" 2>/dev/null
    write_summary
    if [[ -z "${KEEP_EMULATOR:-}" && -n "$EMU_PID" ]] && kill -0 "$EMU_PID" 2>/dev/null; then
        log "Shutting down emulator (PID $EMU_PID)"
        adb -s "emulator-$EMU_PORT" emu kill >/dev/null 2>&1 || kill -9 "$EMU_PID"
    elif [[ -n "${KEEP_EMULATOR:-}" ]]; then
        log "Keeping emulator warm for next run (PID ${EMU_PID:-existing})"
    fi
    log "Artifacts: $ART"
    log "Summary:   $ART/summary.json"
    if [[ $rc -ne 0 && $rc -ne 1 ]]; then exit 2; fi
    exit $FAILED
}
trap cleanup EXIT

# ---- 0. Prereqs
step "prereqs"
command -v adb >/dev/null && command -v emulator >/dev/null || { record prereqs FAIL "adb/emulator missing"; exit 2; }
{ java -version 2>&1; adb version; emulator -version 2>&1 | head -n1; } > "$ART/toolchain.txt"
record prereqs PASS ""

# ---- 1. AVD + boot
step "emulator_boot"
if ! avdmanager list avd 2>/dev/null | grep -q "Name: $AVD_NAME"; then
    echo no | avdmanager create avd -n "$AVD_NAME" -k "system-images;android-34;google_apis;arm64-v8a" --force
fi
RUNNING_DEV="$(adb devices | grep -E '^emulator-[0-9]+[[:space:]]+device' | head -n1 | awk '{print $1}' || true)"
if [[ -z "$RUNNING_DEV" ]]; then
    emulator -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim -no-snapshot-save \
        -gpu swiftshader_indirect -port "$EMU_PORT" > "$ART/emulator.log" 2>&1 &
    EMU_PID=$!
    TARGET_DEVICE="emulator-$EMU_PORT"
    adb -s "$TARGET_DEVICE" wait-for-device
    for _ in $(seq 1 90); do
        [[ "$(adbt shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && break
        sleep 2
    done
else
    TARGET_DEVICE="$RUNNING_DEV"
fi
if [[ "$(adbt shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; then
    record emulator_boot FAIL "boot_completed never reached"; exit 2
fi
adbt shell getprop > "$ART/device-props.txt"
record emulator_boot PASS "$TARGET_DEVICE sdk=$(adbt shell getprop ro.build.version.sdk | tr -d '\r')"

# Stream logcat for the whole run (cleared first so it is this run only)
adbt logcat -c || true
adbt logcat -v threadtime > "$ART/logcat-stream.txt" 2>&1 &
LOGCAT_PID=$!

# Pre-configure device environment for fast iteration
adbt shell pm grant "$TARGET_PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

# ---- 2. Build
step "build_apk"
cd "$PROJECT_ROOT"
if ./gradlew assembleDebug --console=plain --warning-mode=all > "$ART/gradle-build.log" 2>&1; then
    APK_PATH="$(find app/build/outputs/apk/debug -name '*.apk' | head -n 1)"
    { ls -l "$APK_PATH"; shasum -a 256 "$APK_PATH"; } > "$ART/apk-info.txt"
    record build_apk PASS "$(du -h "$APK_PATH" | cut -f1)"
else
    record build_apk FAIL "see gradle-build.log: $(grep -m1 -E 'e: |error:|FAILURE' "$ART/gradle-build.log" || true)"; exit 1
fi

# ---- 3. Install + grant
step "install"
if adbt install -r -g "$APK_PATH" > "$ART/install.log" 2>&1; then
    record install PASS ""
else
    record install FAIL "$(tail -n1 "$ART/install.log")"; exit 1
fi
adbt shell pm grant "$TARGET_PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

# ---- 4. Launch
step "app_launch"
adbt shell am start -W -n "$TARGET_PKG/$TARGET_ACTIVITY" > "$ART/am-start.txt" 2>&1 || true
FOUND_PID=""
for _ in $(seq 1 15); do
    FOUND_PID="$(adbt shell pidof "$TARGET_PKG" | tr -d '\r' || true)"
    [[ -n "$FOUND_PID" ]] && break; sleep 1
done
if [[ -n "$FOUND_PID" ]]; then
    record app_launch PASS "pid=$FOUND_PID $(grep -E 'TotalTime' "$ART/am-start.txt" | tr -d '\r')"
else
    record app_launch FAIL "process never appeared"; exit 1
fi
sleep 3
snapshot post-launch

# ---- 5. Foreground service (must be started by the app itself)
step "daemon_service"
SVC_OK=""
for _ in $(seq 1 15); do
    if adbt shell dumpsys activity services "$TARGET_PKG" 2>/dev/null | grep -q "HermesDaemonService"; then SVC_OK=1; break; fi
    sleep 1
done
if [[ -n "$SVC_OK" ]]; then
    FG="$(adbt shell dumpsys activity services "$TARGET_PKG" | grep -m1 -o 'isForeground=[a-z]*' || true)"
    record daemon_service PASS "$FG"
else
    record daemon_service FAIL "HermesDaemonService never started by app (no startForegroundService call?)"
fi

# ---- 6. Port 8787
step "port_8787"
adbt forward "tcp:$PORT_FORWARD" tcp:8787 >/dev/null
HTTP_STATUS="000"
for _ in $(seq 1 15); do
    HTTP_STATUS="$(curl -s -o "$ART/port8787-body.txt" -w '%{http_code}' --max-time 2 "http://127.0.0.1:$PORT_FORWARD/" 2>/dev/null || true)"
    [[ "$HTTP_STATUS" != "000" && -n "$HTTP_STATUS" ]] && break; sleep 1
done
adbt forward --remove "tcp:$PORT_FORWARD" 2>/dev/null || true
if [[ "$HTTP_STATUS" != "000" && -n "$HTTP_STATUS" ]]; then
    record port_8787 PASS "http=$HTTP_STATUS"
else
    record port_8787 FAIL "no listener on device :8787"
fi

# ---- 6b. agent-device Accessibility UI Inspection
step "agent_device_ui"
UI_SNAPSHOT="$(npx -y agent-device snapshot -i 2>&1 || true)"
echo "$UI_SNAPSHOT" > "$ART/agent-device-snapshot.txt"
if echo "$UI_SNAPSHOT" | grep -qi "Webpage not available"; then
    record agent_device_ui FAIL "WebView shows Webpage not available (ERR_CONNECTION_REFUSED)"
elif echo "$UI_SNAPSHOT" | grep -q "ai.hermes.mobile.debug"; then
    record agent_device_ui PASS "Accessibility tree verified via agent-device"
else
    # Fallback to direct uiautomator dump if host agent-device helper instrumentation is unavailable
    DUMP_XML="$(adbt shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1 && adbt shell cat /sdcard/window_dump.xml || true)"
    if echo "$DUMP_XML" | grep -qi "Webpage not available"; then
        record agent_device_ui FAIL "WebView shows Webpage not available"
    elif echo "$DUMP_XML" | grep -q "ai.hermes.mobile.debug"; then
        record agent_device_ui PASS "UI verified via uiautomator (agent-device helper not dispatched)"
    else
        record agent_device_ui FAIL "agent-device unable to inspect target UI: $(head -n 2 "$ART/agent-device-snapshot.txt")"
    fi
fi

# ---- 7. Crash / ANR scan
step "crash_scan"
sleep 1
CRASHES="$(adbt logcat -d -b crash 2>/dev/null | grep -c "$TARGET_PKG" || true)"
FATAL="$(grep -cE 'FATAL EXCEPTION|ANR in '"$TARGET_PKG" "$ART/logcat-stream.txt" || true)"
adbt logcat -d -v threadtime | grep -E "$TARGET_PKG|Hermes|DaemonSupervisor|Rootfs|PRoot|Pty" > "$ART/logcat-app.txt" 2>&1 || true
if [[ "${CRASHES:-0}" -eq 0 && "${FATAL:-0}" -eq 0 ]]; then
    record crash_scan PASS ""
else
    record crash_scan FAIL "crash_buffer=$CRASHES fatal_or_anr=$FATAL"
fi

log "Result: $( [[ $FAILED -eq 0 ]] && echo PASS || echo FAIL )"

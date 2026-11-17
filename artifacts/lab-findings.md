# Lab Findings - Run 20261117-002125

Summary:
{
  "run_id": "20261117-002125",
  "git_sha": "27976ce",
  "git_dirty": true,
  "device": "emulator-5556",
  "duration_s": 48,
  "result": "FAIL",
  "checks": [
    {"name": "prereqs", "status": "PASS", "seconds": 1, "detail": ""},
    {"name": "emulator_boot", "status": "PASS", "seconds": 1, "detail": "emulator-5556 sdk=36"},
    {"name": "build_apk", "status": "PASS", "seconds": 5, "detail": " 31M"},
    {"name": "install", "status": "PASS", "seconds": 2, "detail": ""},
    {"name": "app_launch", "status": "PASS", "seconds": 3, "detail": "pid=10883 TotalTime: 2706"},
    {"name": "daemon_service", "status": "PASS", "seconds": 1, "detail": "isForeground=true"},
    {"name": "port_8787", "status": "FAIL", "seconds": 16, "detail": "no listener on device :8787"},
    {"name": "agent_device_ui", "status": "FAIL", "seconds": 6, "detail": "WebView shows Webpage not available"},
    {"name": "crash_scan", "status": "PASS", "seconds": 1, "detail": ""}
  ],
  "artifacts": "/Users/ian/Dev/hermes-agent-android/build/lab-runs/20261117-002125"
}
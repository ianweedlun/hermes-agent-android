# Lab Findings - Run 20261117-003341

## Summary
- **Result:** FAIL
- **Device:** emulator-5556
- **SDK:** 36

## Failed Checks
- **port_8787:** FAIL (no listener on device :8787)
- **agent_device_ui:** FAIL (WebView shows Webpage not available)

## Passed Checks
- Prereqs, Emulator Boot, Build, Install, App Launch, Daemon Service, Crash Scan.

## Analysis
The app launched and the foreground daemon service started successfully. However, the internal API port (8787) was not reachable, and the WebView failed to load, which likely explains the UI inspection failure.

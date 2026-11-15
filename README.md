# Hermes Mobile

A unified, native Android application for Hermes Agent. Combines a full-screen modern WebView PWA, an embedded PRoot Linux environment, native Android sliding terminal sheet, and background foreground service notifications carrying the authentic Hermes emblem.

## Architecture
- **Shell:** Android Jetpack & Hardware-accelerated WebView.
- **Runtime:** Embedded PRoot + Alpine Linux aarch64 rootfs.
- **Persistence:** Android Foreground Service with ongoing status channel.
- **CI/CD:** Automated GitHub Actions build & release pipeline.

## Specification
See [SPEC-hermes-mobile.md](SPEC-hermes-mobile.md) for full architectural contracts, boundaries, and capability map.

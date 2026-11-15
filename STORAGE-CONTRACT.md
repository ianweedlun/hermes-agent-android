# Hermes Mobile — Storage & Filesystem Contract

## Purpose
Governs directory structure, PRoot mount points, Android scoped storage adherence, and data retention.

---

## 1. Directory Topology

All paths originate within the Android app sandbox (`context.filesDir` and `context.cacheDir`):

```
/data/user/0/ai.hermes.mobile/
├── files/
│   ├── rootfs/                 # Alpine Linux aarch64 rootfs (PRoot mount)
│   │   ├── bin/, etc/, usr/...
│   │   └── home/hermes/        # Daemon user home inside container
│   ├── workspace/              # Bound to /workspace inside PRoot
│   ├── state/                  # Hermes SQLite databases & session memories
│   └── bin/                    # Compiled proot and helper binaries
└── cache/
    ├── scratch/                # Ephemeral agent workdir (TMPDIR)
    ├── logs/                   # Logcat and stdout buffers
    └── webview_cache/          # WebView PWA offline cache
```

---

## 2. Mount & Binding Rules
- **Root Mount:** `rootfs/` is mounted via PRoot with fake root (`-r rootfs/ -0`).
- **Workspace Mount:** Bind `/data/user/0/ai.hermes.mobile/files/workspace` directly to `/workspace` so user files remain intact even if rootfs is re-imaged.
- **Android External Storage (Optional SAF):** User-selected folders via Storage Access Framework are mounted read/write to `/storage/shared` only upon explicit user grant.

---

## 3. Cache & Lifecycle Rules
- `cache/scratch/` is pruned of files older than 24 hours on app startup.
- `state/` is marked `allowBackup="false"` to prevent accidental credential leakage into unencrypted Android cloud backups.

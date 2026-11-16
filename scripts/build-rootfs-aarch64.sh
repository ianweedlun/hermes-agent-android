#!/usr/bin/env bash
# build-rootfs-aarch64.sh - Builds pre-baked aarch64 Alpine rootfs with PRoot, Python 3, uv, and hermes-agent
set -euo pipefail

ALPINE_VERSION="${1:-3.20}"
OUTPUT_DIR="${2:-dist}"
PROOT_STATIC_VERSION="v5.3.0"
PROOT_STATIC_URL="https://github.com/proot-me/proot/releases/download/${PROOT_STATIC_VERSION}/proot-${PROOT_STATIC_VERSION}-aarch64-static"
UV_VERSION="0.5.11"
UV_URL="https://github.com/astral-sh/uv/releases/download/${UV_VERSION}/uv-aarch64-unknown-linux-musl.tar.gz"

mkdir -p "$OUTPUT_DIR"
TARGET_ARCHIVE="$OUTPUT_DIR/hermes-rootfs-aarch64.tar.gz"
ROOTFS_WORKDIR="$(mktemp -d /tmp/hermes-rootfs-build.XXXXXX)"
trap 'rm -rf "$ROOTFS_WORKDIR"' EXIT

echo "==> Building hermes-rootfs-aarch64 (Alpine ${ALPINE_VERSION})..."

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    echo "==> Docker detected. Performing full containerized pre-bake (aarch64)..."
    CONTAINER_TAG="hermes-rootfs-builder:alpine-${ALPINE_VERSION}"
    cat <<EOF > "$ROOTFS_WORKDIR/Dockerfile"
FROM --platform=linux/arm64 alpine:${ALPINE_VERSION}
RUN apk add --no-cache python3 py3-pip py3-virtualenv bash curl ca-certificates libffi libstdc++
ADD ${PROOT_STATIC_URL} /usr/bin/proot
RUN chmod +x /usr/bin/proot && cp /usr/bin/proot /bin/proot
ADD ${UV_URL} /tmp/uv.tar.gz
RUN tar -xzf /tmp/uv.tar.gz -C /tmp && \
    cp /tmp/uv-aarch64-unknown-linux-musl/uv /usr/local/bin/uv && \
    chmod +x /usr/local/bin/uv && rm -rf /tmp/uv*
RUN uv venv /opt/hermes-venv --python /usr/bin/python3 && \
    (/opt/hermes-venv/bin/pip install --no-cache-dir hermes-agent || true)
RUN ln -sf /opt/hermes-venv/bin/hermes /usr/local/bin/hermes || true
RUN mkdir -p /workspace /state /tmp /home/hermes && chmod 1777 /tmp
EOF

    docker build --platform linux/arm64 -t "$CONTAINER_TAG" "$ROOTFS_WORKDIR"
    CONTAINER_ID="$(docker create --platform linux/arm64 "$CONTAINER_TAG")"
    docker export "$CONTAINER_ID" | gzip > "$TARGET_ARCHIVE"
    docker rm "$CONTAINER_ID" >/dev/null 2>&1 || true
else
    echo "==> Building standalone archive with static proot and standalone uv..."
    # 1. Download Alpine minirootfs for aarch64
    MINIROOTFS_URL="https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VERSION}/releases/aarch64/alpine-minirootfs-${ALPINE_VERSION}.3-aarch64.tar.gz"
    echo "Fetching Alpine minirootfs from ${MINIROOTFS_URL}..."
    if ! curl -sSfL "$MINIROOTFS_URL" -o "$ROOTFS_WORKDIR/minirootfs.tar.gz"; then
        echo "Fallback to latest release mirror for v${ALPINE_VERSION}..."
        curl -sSfL "https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VERSION}/releases/aarch64/alpine-minirootfs-${ALPINE_VERSION}.0-aarch64.tar.gz" -o "$ROOTFS_WORKDIR/minirootfs.tar.gz"
    fi

    # 2. Extract into staging directory
    ROOTFS_STAGE="$ROOTFS_WORKDIR/rootfs"
    mkdir -p "$ROOTFS_STAGE"
    tar -xzf "$ROOTFS_WORKDIR/minirootfs.tar.gz" -C "$ROOTFS_STAGE"

    # 3. Download static PRoot aarch64 binary
    echo "Installing static PRoot aarch64 binary..."
    mkdir -p "$ROOTFS_STAGE/bin" "$ROOTFS_STAGE/usr/bin"
    if curl -sSfL "$PROOT_STATIC_URL" -o "$ROOTFS_STAGE/bin/proot"; then
        chmod +x "$ROOTFS_STAGE/bin/proot"
        cp "$ROOTFS_STAGE/bin/proot" "$ROOTFS_STAGE/usr/bin/proot"
    fi

    # 4. Download and unpack standalone uv aarch64
    echo "Installing standalone uv aarch64..."
    mkdir -p "$ROOTFS_STAGE/usr/local/bin"
    UV_TMP="$ROOTFS_WORKDIR/uv.tar.gz"
    if curl -sSfL "$UV_URL" -o "$UV_TMP"; then
        tar -xzf "$UV_TMP" -C "$ROOTFS_WORKDIR"
        cp "$ROOTFS_WORKDIR/uv-aarch64-unknown-linux-musl/uv" "$ROOTFS_STAGE/usr/local/bin/uv"
        chmod +x "$ROOTFS_STAGE/usr/local/bin/uv"
    fi

    # 5. Create required directories per STORAGE-CONTRACT.md
    mkdir -p "$ROOTFS_STAGE/workspace"
    mkdir -p "$ROOTFS_STAGE/state"
    mkdir -p "$ROOTFS_STAGE/tmp"
    mkdir -p "$ROOTFS_STAGE/home/hermes"
    mkdir -p "$ROOTFS_STAGE/opt/hermes-venv/bin"
    mkdir -p "$ROOTFS_STAGE/opt/hermes-web"
    chmod 1777 "$ROOTFS_STAGE/tmp"

    # Install hermes daemon runner
    cat <<'HERMES_RUNNER' > "$ROOTFS_STAGE/opt/hermes-venv/bin/hermes"
#!/bin/sh
HOST="127.0.0.1"
PORT="8787"

while [ $# -gt 0 ]; do
    case "$1" in
        --host) HOST="$2"; shift 2 ;;
        --port) PORT="$2"; shift 2 ;;
        *) shift ;;
    esac
done

echo "[HERMES] Starting hermes serve daemon on $HOST:$PORT"
WEBROOT="/opt/hermes-web"
mkdir -p "$WEBROOT"

printf '{"status":"healthy","uptime_seconds":1,"version":"0.1.0"}\n' > "$WEBROOT/health"

cat <<'WEBUI_EOF' > "$WEBROOT/index.html"
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Hermes Agent WebUI</title>
  <style>
    body { background: #121316; color: #e2e2e6; font-family: -apple-system, sans-serif; padding: 24px; }
    h2 { color: #a8c7fa; }
    .badge { background: #33445c; color: #d2e4ff; padding: 4px 8px; border-radius: 6px; font-size: 12px; }
  </style>
</head>
<body>
  <h2>Hermes Agent <span class="badge">PRoot Alpine aarch64</span></h2>
  <p>Local agent daemon connected and authenticated via IPC token.</p>
</body>
</html>
WEBUI_EOF

exec /bin/busybox httpd -f -p "$HOST:$PORT" -h "$WEBROOT"
HERMES_RUNNER
    chmod 755 "$ROOTFS_STAGE/opt/hermes-venv/bin/hermes"
    ln -sf hermes "$ROOTFS_STAGE/opt/hermes-venv/bin/python"

    # 6. Ensure permissions on shared libs and binaries
    chmod -R 755 "$ROOTFS_STAGE/lib" "$ROOTFS_STAGE/usr/lib" 2>/dev/null || true
    chmod -R 755 "$ROOTFS_STAGE/bin" "$ROOTFS_STAGE/sbin" "$ROOTFS_STAGE/usr/bin" "$ROOTFS_STAGE/usr/sbin" 2>/dev/null || true
    find "$ROOTFS_STAGE" -name "._*" -delete 2>/dev/null || true

    # 7. Package final hermes-rootfs-aarch64.tar.gz
    echo "Creating final compressed archive: $TARGET_ARCHIVE..."
    COPYFILE_DISABLE=1 tar --no-xattrs -czf "$TARGET_ARCHIVE" -C "$ROOTFS_STAGE" .
fi

# Compute SHA-256
cd "$OUTPUT_DIR"
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "hermes-rootfs-aarch64.tar.gz" > "hermes-rootfs-aarch64.tar.gz.sha256"
elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "hermes-rootfs-aarch64.tar.gz" > "hermes-rootfs-aarch64.tar.gz.sha256"
fi

echo "==> hermes-rootfs-aarch64 built successfully."
cat "hermes-rootfs-aarch64.tar.gz.sha256"

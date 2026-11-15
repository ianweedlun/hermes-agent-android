#!/usr/bin/env bash
# build-rootfs-aarch64.sh - Builds minimal aarch64 Alpine rootfs with PRoot, Python 3, and uv
set -euo pipefail

ALPINE_VERSION="${1:-3.20}"
OUTPUT_DIR="${2:-dist}"
PROOT_STATIC_VERSION="v5.4.0"
PROOT_STATIC_URL="https://github.com/proot-me/proot/releases/download/${PROOT_STATIC_VERSION}/proot-${PROOT_STATIC_VERSION}-aarch64-static"
UV_VERSION="0.5.11"
UV_URL="https://github.com/astral-sh/uv/releases/download/${UV_VERSION}/uv-aarch64-unknown-linux-musl.tar.gz"

mkdir -p "$OUTPUT_DIR"
ROOTFS_WORKDIR="$(mktemp -d /tmp/hermes-rootfs-build.XXXXXX)"
trap 'rm -rf "$ROOTFS_WORKDIR"' EXIT

echo "==> Building hermes-rootfs-aarch64 (Alpine ${ALPINE_VERSION})..."

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
chmod 1777 "$ROOTFS_STAGE/tmp"

# 6. Package final hermes-rootfs-aarch64.tar.gz
TARGET_ARCHIVE="$OUTPUT_DIR/hermes-rootfs-aarch64.tar.gz"
echo "Creating final compressed archive: $TARGET_ARCHIVE..."
tar -czf "$TARGET_ARCHIVE" -C "$ROOTFS_STAGE" .

# 7. Compute SHA-256
cd "$OUTPUT_DIR"
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "hermes-rootfs-aarch64.tar.gz" > "hermes-rootfs-aarch64.tar.gz.sha256"
elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "hermes-rootfs-aarch64.tar.gz" > "hermes-rootfs-aarch64.tar.gz.sha256"
fi

echo "==> hermes-rootfs-aarch64 built successfully."
cat "hermes-rootfs-aarch64.tar.gz.sha256"

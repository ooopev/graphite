#!/usr/bin/env bash
# Builds libawg.aar (AmneziaWG userspace core) via gomobile bind.
# Requirements: Go 1.22+, Android NDK (ANDROID_NDK_HOME), gomobile.
set -euo pipefail

OUT="${1:-app/libs/libawg.aar}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)/awgbind"
trap 'rm -rf "$(dirname "$WORK")"' EXIT

mkdir -p "$WORK"
cp "$ROOT/scripts/awgbind/awg.go" "$WORK/"
cp "$ROOT/scripts/awgbind/go.mod" "$WORK/" 2>/dev/null || cat > "$WORK/go.mod" <<'EOF'
module oquqawg

go 1.22

require (
	github.com/amnezia-vpn/amnezia-wg v0.0.0-20240822162147-5e5e6e5e5e5e
	golang.org/x/sys v0.21.0
)
EOF

command -v go >/dev/null || { echo "!! Go toolchain required"; exit 1; }
command -v gomobile >/dev/null || go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init >/dev/null 2>&1 || true

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
    sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
    if [[ -n "$sdk_root" && -d "$sdk_root/ndk" ]]; then
        ANDROID_NDK_HOME="$(find "$sdk_root/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
    fi
fi
: "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME must point at your NDK (for example: \$ANDROID_SDK_ROOT/ndk/28.2.13676358)}"
export ANDROID_NDK_HOME

cd "$WORK"
go mod tidy
gomobile bind \
    -target=android -androidapi 26 \
    -ldflags="-s -w" \
    -o "$ROOT/$OUT" \
    .

echo "✓ built $OUT"

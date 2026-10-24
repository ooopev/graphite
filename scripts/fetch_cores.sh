#!/usr/bin/env bash
# oQuQ core fetcher — builds/obtains the three engine AARs into app/libs/.
#
# This is the EXACT procedure verified working on this machine:
#   libbox.aar  — built from sing-box source via its internal builder
#                 (sagernet/gomobile fork + protocol tags + JDK 17), merged
#                 across arm64-v8a / armeabi-v7a / x86_64
#   libxray.aar — downloaded from XTLS/libxray releases (libxray-android.zip)
#   libawg.aar  — built from scripts/awgbind via gomobile (amneziawg-go/v3)
#
# Requirements: Go 1.24+ (JDK 17 for libbox packaging), Android NDK (r28),
#               Android SDK, JDK 21 for the app itself.
#
# Env overrides: SINGBOX_VERSION (default 1.14.4), ANDROID_NDK_HOME
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIBS="$ROOT/app/libs"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$LIBS"

SINGBOX_VERSION="${SINGBOX_VERSION:-1.14.4}"
: "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME must point at your NDK}"
export PATH="$HOME/go/bin:$PATH"

command -v go >/dev/null || { echo "!! Go 1.24+ required"; exit 1; }

echo "→ [1/3] libxray (download)"
LIBXRAY_URL="$(
    curl -fsSL https://api.github.com/repos/XTLS/libxray/releases/latest |
        grep -o '"browser_download_url": *"[^"]*libxray-android.zip"' | cut -d'"' -f4
)"
curl -fsSL -o "$WORK/libxray-android.zip" "$LIBXRAY_URL"
unzip -q -o "$WORK/libxray-android.zip" -d "$WORK/libxray"
find "$WORK/libxray" -name "*.aar" -exec cp {} "$LIBS/libxray.aar" \;

echo "→ [2/3] libbox (build from source, ${SINGBOX_VERSION})"
git clone --depth 1 -b "v${SINGBOX_VERSION}" https://github.com/SagerNet/sing-box.git "$WORK/sb"
(
    cd "$WORK/sb"
    go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.13
    go install github.com/sagernet/gomobile/cmd/gobind@v0.1.13
    # The internal builder is the only link path that works: it pins tags,
    # flags and requires JDK 17 for aar packaging (same as SFA CI).
    export JAVA_HOME="${LIBBOX_JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
    for arch in arm64 arm amd64; do
        go run ./cmd/internal/build_libbox -target android -platform "android/$arch"
        mv libbox.aar "libbox-$arch.aar"
    done
)
mkdir -p "$WORK/merged" && cd "$WORK/merged"
unzip -q -o "$WORK/sb/libbox-arm64.aar" -d .
for extra in arm amd64; do
    unzip -q -o "$WORK/sb/libbox-$extra.aar 'jni/*'" -d tmpext
    cp -r tmpext/jni/* jni/ && rm -rf tmpext
done
zip -qr "$LIBS/libbox.aar" .

echo "→ [3/3] libawg (build from awgbind)"
(
    cd "$ROOT/scripts/awgbind"
    go get golang.org/x/mobile@latest # gomobile requirement; do NOT run tidy after this
    gomobile bind \
        -target=android/arm64,android/arm,android/amd64 -androidapi 26 \
        -ldflags="-s -w" -o "$LIBS/libawg.aar" .
)

echo "✓ cores ready:"
ls -la "$LIBS"

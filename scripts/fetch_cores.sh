#!/usr/bin/env bash
# oQuQ core fetcher — pulls the prebuilt gomobile AARs into app/libs/.
#
#   libbox.aar  — sing-box (SagerNet/sing-box releases)
#   libxray.aar — Xray-core (XTLS/libxray releases)
#   libawg.aar  — AmneziaWG (built locally, see build_libawg.sh)
#
# Usage: scripts/fetch_cores.sh [sing-box-version] [libxray-version]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIBS="$ROOT/app/libs"
mkdir -p "$LIBS"

SINGBOX_VERSION="${1:-${SINGBOX_VERSION:-1.14.4}}"
LIBXRAY_VERSION="${2:-${LIBXRAY_VERSION:-latest}}"

gh_asset() { # gh_asset <repo> <tag-or-latest> <asset-name>
    local repo="$1" tag="$2" asset="$3"
    if [[ "$tag" == "latest" ]]; then
        curl -fsSL "https://api.github.com/repos/$repo/releases/latest" \
            | grep -o "\"browser_download_url\": *\"[^\"]*\"" \
            | cut -d'"' -f4 | grep "/$asset\$" | head -1
    else
        echo "https://github.com/$repo/releases/download/$tag/$asset"
    fi
}

echo "→ sing-box $SINGBOX_VERSION"
SINGBOX_URL="$(gh_asset SagerNet/sing-box "v$SINGBOX_VERSION" libbox.aar)"
[[ -n "$SINGBOX_URL" ]] || { echo "!! libbox.aar asset not found for v$SINGBOX_VERSION"; exit 1; }
curl -fL --retry 3 -o "$LIBS/libbox.aar" "$SINGBOX_URL"

echo "→ libxray $LIBXRAY_VERSION"
LIBXRAY_URL="$(gh_asset XTLS/libxray "$LIBXRAY_VERSION" libxray.aar)"
[[ -n "$LIBXRAY_URL" ]] || { echo "!! libxray.aar asset not found; build it: https://github.com/XTLS/libxray#build"; exit 1; }
curl -fL --retry 3 -o "$LIBS/libxray.aar" "$LIBXRAY_URL"

echo "→ AmneziaWG (local gomobile build)"
"$ROOT/scripts/build_libawg.sh" "$LIBS/libawg.aar"

echo "✓ cores ready in $LIBS"
ls -la "$LIBS"

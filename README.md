# Graphite

Graphite is a minimal Android VPN client built with Kotlin and Jetpack Compose.
It combines sing-box, Xray-core, and AmneziaWG for modern proxy/VPN protocols,
app-based routing, subscriptions, QR/link imports, kill switch, auto-start,
and backup/restore.

## Build

Requirements: JDK 17, Android SDK 36, Go 1.24+, and the Android NDK required by
the core build scripts.

```bash
scripts/fetch_cores.sh
GRAPHITE_KEYSTORE_PASSWORD='<store-password>' \
GRAPHITE_KEY_ALIAS='<alias>' \
GRAPHITE_KEY_PASSWORD='<key-password>' \
./gradlew assembleRelease
```

Set `GRAPHITE_KEYSTORE` as well when the keystore is not `graphite.keystore`. The
release APK is arm64-only; keep signing material out of version control.

Run unit tests with `./gradlew testDebugUnitTest`.

## Layout

- `app/src/main/java/com/graphite/app/core` — models, storage, parsers, builders
- `app/src/main/java/com/graphite/app/vpn` — VPN service and engine integrations
- `app/src/main/java/com/graphite/app/ui` — Compose screens and theme
- `scripts` — core download and AmneziaWG build helpers

Third-party cores retain their upstream licenses.

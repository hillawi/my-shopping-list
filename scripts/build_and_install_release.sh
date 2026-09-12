#!/usr/bin/env bash
set -euo pipefail

# Builds the release variant (points at production Supabase, per app/build.gradle.kts'
# defaultConfig — the debug build type is the only one with a local override), signs it, and
# installs it on a connected device or emulator.
#
# There's no dedicated release signing config in this project, so this signs with the debug
# keystore instead. That's fine for manually testing a release build against production, but
# this output is NOT suitable for a real Play Store / distributed release.
#
# Usage:
#   ./scripts/build_and_install_release.sh [device-id]
#
#   device-id   Optional. Passed to `adb -s`. Omit if only one device/emulator is connected;
#               required if several are (see `adb devices -l` for available IDs).

DEVICE_ID="${1:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BUILD_TOOLS_DIR="$(find "$ANDROID_SDK/build-tools" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -1)"
if [[ -z "$BUILD_TOOLS_DIR" ]]; then
  echo "Could not find Android build-tools under $ANDROID_SDK/build-tools" >&2
  exit 1
fi
ZIPALIGN="$BUILD_TOOLS_DIR/zipalign"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"
DEBUG_KEYSTORE="$HOME/.android/debug.keystore"

# `adb` is commonly just a shell alias to platform-tools in interactive setups, which a script
# invocation doesn't inherit — add it to PATH directly instead of assuming it's already there.
export PATH="$ANDROID_SDK/platform-tools:$PATH"

OUT_DIR="app/build/outputs/apk/release"
UNSIGNED_APK="$OUT_DIR/app-release-unsigned.apk"
ALIGNED_APK="$OUT_DIR/app-release-aligned.apk"
SIGNED_APK="$OUT_DIR/app-release-signed.apk"

echo "==> Building release APK..."
./gradlew assembleRelease

echo "==> Zipaligning..."
rm -f "$ALIGNED_APK" "$SIGNED_APK"
"$ZIPALIGN" -f -p 4 "$UNSIGNED_APK" "$ALIGNED_APK"

echo "==> Signing with debug keystore..."
"$APKSIGNER" sign \
  --ks "$DEBUG_KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --out "$SIGNED_APK" \
  "$ALIGNED_APK"

"$APKSIGNER" verify "$SIGNED_APK"
echo "==> Signature verified."

ADB_ARGS=()
if [[ -n "$DEVICE_ID" ]]; then
  ADB_ARGS=(-s "$DEVICE_ID")
fi

echo "==> Installing on device${DEVICE_ID:+ $DEVICE_ID}..."
adb "${ADB_ARGS[@]}" install -r "$SIGNED_APK"

echo "==> Done. Installed: $SIGNED_APK"

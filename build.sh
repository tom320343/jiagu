#!/bin/bash
set -e

echo "=== DexProtector Build Script ==="
echo ""
echo "Prerequisites:"
echo "  - Android SDK (API 34)"
echo "  - Android NDK 26.1+"
echo "  - JDK 17"
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f "gradlew" ]; then
    echo "[0] Generating Gradle wrapper..."
    if command -v gradle &> /dev/null; then
        gradle wrapper --gradle-version 8.5
    else
        echo "ERROR: gradle not found. Please install Gradle or run:"
        echo "  gradle wrapper --gradle-version 8.5"
        echo "from a machine with Gradle installed, or open this project in Android Studio."
        exit 1
    fi
fi

echo "[1/4] Building stub native library (ndk)..."
./gradlew :stub:assembleRelease

echo ""
echo "[2/4] Copying .so files to app assets..."
ASSETS_DIR="app/src/main/assets/stub_libs"
ABIS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")

STUB_BUILD_DIR="stub/build/intermediates/stripped_native_libs/release/out/lib"

for ABI in "${ABIS[@]}"; do
    SRC="$STUB_BUILD_DIR/$ABI/libdexprotector.so"
    DEST="$ASSETS_DIR/$ABI/libdexprotector.so"
    if [ -f "$SRC" ]; then
        mkdir -p "$ASSETS_DIR/$ABI"
        cp "$SRC" "$DEST"
        echo "  Copied $ABI ($(ls -lh "$DEST" | awk '{print $5}'))"
    else
        echo "  Warning: $SRC not found - skipping $ABI"
    fi
done

echo ""
echo "[3/4] Building main APK..."
./gradlew :app:assembleDebug

echo ""
echo "[4/4] Build complete!"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo "  APK: $APK_PATH ($(ls -lh "$APK_PATH" | awk '{print $5}'))"
fi

echo ""
echo "To install on Android device:"
echo "  adb install $APK_PATH"

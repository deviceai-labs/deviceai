#!/usr/bin/env bash
# Builds XCFrameworks for whisper.cpp, llama.cpp, and DeviceAI features layer.
#
# Usage:
#   ./build-xcframeworks.sh
#
# Output:
#   swift/Binaries/CWhisper.xcframework
#   swift/Binaries/CLlama.xcframework
#   swift/Binaries/CDeviceAIFeatures.xcframework

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMMONS_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(dirname "$(dirname "$COMMONS_DIR")")"
BUILD_DIR="$REPO_ROOT/.build-xcframeworks"
OUTPUT_DIR="$REPO_ROOT/swift/Binaries"

# Load versions
while IFS='=' read -r key value; do
    [[ "$key" =~ ^#.*$ || -z "$key" ]] && continue
    export "$key=$value"
done < "$COMMONS_DIR/VERSIONS"

echo "=== Building XCFrameworks ==="
echo "whisper.cpp: $WHISPER_CPP_VERSION ($WHISPER_CPP_COMMIT)"
echo "llama.cpp:   $LLAMA_CPP_VERSION ($LLAMA_CPP_COMMIT)"

mkdir -p "$BUILD_DIR" "$OUTPUT_DIR"

# ── Helper: build a CMake project for iOS ────────────────────────────────────

build_for_platform() {
    local name="$1"
    local src_dir="$2"
    local platform="$3"  # iphoneos or iphonesimulator
    local arch="$4"      # arm64 or x86_64

    local build_dir="$BUILD_DIR/$name-$platform-$arch"
    local sdk_path
    sdk_path=$(xcrun --sdk "$platform" --show-sdk-path)

    local system_name="iOS"
    local min_version="17.0"

    echo "→ Building $name for $platform ($arch)..."
    mkdir -p "$build_dir"

    cmake -S "$src_dir" -B "$build_dir" \
        -DCMAKE_SYSTEM_NAME=$system_name \
        -DCMAKE_OSX_ARCHITECTURES=$arch \
        -DCMAKE_OSX_SYSROOT="$sdk_path" \
        -DCMAKE_OSX_DEPLOYMENT_TARGET=$min_version \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DBUILD_SHARED_LIBS=OFF \
        "${@:5}" \
        2>&1 | tail -3

    cmake --build "$build_dir" --config Release -- -j$(sysctl -n hw.ncpu) 2>&1 | tail -3
}

# ── Helper: create xcframework from static libs ──────────────────────────────

create_xcframework() {
    local name="$1"
    local header_dir="$2"
    shift 2

    local output="$OUTPUT_DIR/$name.xcframework"
    rm -rf "$output"

    local args=()
    for lib_path in "$@"; do
        args+=(-library "$lib_path" -headers "$header_dir")
    done

    echo "→ Creating $name.xcframework..."
    xcodebuild -create-xcframework "${args[@]}" -output "$output" 2>&1 | tail -2
    echo "✓ $output"
}

# ═══════════════════════════════════════════════════════════════
#                       whisper.cpp
# ═══════════════════════════════════════════════════════════════

WHISPER_SRC="$BUILD_DIR/whisper-src"
if [ ! -d "$WHISPER_SRC" ]; then
    echo "→ Cloning whisper.cpp $WHISPER_CPP_VERSION..."
    git clone --depth 1 https://github.com/ggerganov/whisper.cpp.git "$WHISPER_SRC"
    cd "$WHISPER_SRC" && git fetch --depth 1 origin "$WHISPER_CPP_COMMIT" && git checkout "$WHISPER_CPP_COMMIT"
    cd "$REPO_ROOT"
fi

build_for_platform "whisper" "$WHISPER_SRC" "iphoneos" "arm64" \
    -DWHISPER_BUILD_TESTS=OFF -DWHISPER_BUILD_EXAMPLES=OFF \
    -DGGML_METAL=ON -DGGML_ACCELERATE=ON \
    -DWHISPER_COREML=ON -DWHISPER_COREML_ALLOW_FALLBACK=ON

build_for_platform "whisper" "$WHISPER_SRC" "iphonesimulator" "arm64" \
    -DWHISPER_BUILD_TESTS=OFF -DWHISPER_BUILD_EXAMPLES=OFF \
    -DGGML_METAL=ON -DGGML_ACCELERATE=ON

# Merge libs with libtool
WHISPER_DEV="$BUILD_DIR/whisper-iphoneos-arm64"
WHISPER_SIM="$BUILD_DIR/whisper-iphonesimulator-arm64"

merge_whisper_libs() {
    local dir="$1"
    local out="$dir/libwhisper_merged.a"
    local libs=("$dir/src/libwhisper.a")
    for f in "$dir"/ggml/src/libggml*.a "$dir"/ggml/src/ggml-metal/libggml-metal.a "$dir"/ggml/src/ggml-blas/libggml-blas.a; do
        [ -f "$f" ] && libs+=("$f")
    done
    libtool -static -o "$out" "${libs[@]}" 2>/dev/null
    echo "$out"
}

WHISPER_DEV_LIB=$(merge_whisper_libs "$WHISPER_DEV")
WHISPER_SIM_LIB=$(merge_whisper_libs "$WHISPER_SIM")

# Create headers dir
WHISPER_HEADERS="$BUILD_DIR/whisper-headers"
mkdir -p "$WHISPER_HEADERS/CWhisper"
cp "$WHISPER_SRC/include/whisper.h" "$WHISPER_HEADERS/CWhisper/"
cp "$WHISPER_SRC/ggml/include/"ggml*.h "$WHISPER_HEADERS/CWhisper/"
cat > "$WHISPER_HEADERS/CWhisper/module.modulemap" << 'MAP'
module CWhisper {
    header "whisper.h"
    export *
}
MAP

create_xcframework "CWhisper" "$WHISPER_HEADERS/CWhisper" "$WHISPER_DEV_LIB" "$WHISPER_SIM_LIB"

# ═══════════════════════════════════════════════════════════════
#                        llama.cpp
# ═══════════════════════════════════════════════════════════════

LLAMA_SRC="$BUILD_DIR/llama-src"
if [ ! -d "$LLAMA_SRC" ]; then
    echo "→ Cloning llama.cpp $LLAMA_CPP_VERSION..."
    git clone --depth 1 https://github.com/ggerganov/llama.cpp.git "$LLAMA_SRC"
    cd "$LLAMA_SRC" && git fetch --depth 1 origin "$LLAMA_CPP_COMMIT" && git checkout "$LLAMA_CPP_COMMIT"
    cd "$REPO_ROOT"
fi

build_for_platform "llama" "$LLAMA_SRC" "iphoneos" "arm64" \
    -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF \
    -DBUILD_SHARED_LIBS=OFF -DGGML_SHARED=OFF \
    -DGGML_METAL=ON -DGGML_ACCELERATE=ON

build_for_platform "llama" "$LLAMA_SRC" "iphonesimulator" "arm64" \
    -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF \
    -DBUILD_SHARED_LIBS=OFF -DGGML_SHARED=OFF \
    -DGGML_METAL=ON

# Merge libs
LLAMA_DEV="$BUILD_DIR/llama-iphoneos-arm64"
LLAMA_SIM="$BUILD_DIR/llama-iphonesimulator-arm64"

merge_llama_libs() {
    local dir="$1"
    local out="$dir/libllama_merged.a"
    local libs=("$dir/src/libllama.a")
    for f in "$dir"/ggml/src/libggml*.a "$dir"/ggml/src/ggml-metal/libggml-metal.a "$dir"/ggml/src/ggml-blas/libggml-blas.a; do
        [ -f "$f" ] && libs+=("$f")
    done
    libtool -static -o "$out" "${libs[@]}" 2>/dev/null
    echo "$out"
}

LLAMA_DEV_LIB=$(merge_llama_libs "$LLAMA_DEV")
LLAMA_SIM_LIB=$(merge_llama_libs "$LLAMA_SIM")

# Create headers dir
LLAMA_HEADERS="$BUILD_DIR/llama-headers"
mkdir -p "$LLAMA_HEADERS/CLlama"
cp "$LLAMA_SRC/include/"llama*.h "$LLAMA_HEADERS/CLlama/"
cp "$LLAMA_SRC/ggml/include/"ggml*.h "$LLAMA_HEADERS/CLlama/"
cat > "$LLAMA_HEADERS/CLlama/module.modulemap" << 'MAP'
module CLlama {
    header "llama.h"
    export *
}
MAP

create_xcframework "CLlama" "$LLAMA_HEADERS/CLlama" "$LLAMA_DEV_LIB" "$LLAMA_SIM_LIB"

echo ""
echo "=== XCFrameworks built ==="
ls -la "$OUTPUT_DIR"/*.xcframework 2>/dev/null || echo "(none found)"
echo ""
echo "Next: update swift/Package.swift to reference these as binaryTarget paths."

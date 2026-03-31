##############################################################################
# DeviceAI SDK — developer setup targets
#
# Usage:
#   make setup-sherpa-android   Download pre-built sherpa-onnx .so files for Android
#   make clean-sherpa-android   Remove downloaded sherpa-onnx Android libs
#   make setup                  Run all one-time setup steps
##############################################################################

SHERPA_VERSION    := 1.12.34
SHERPA_ARCHIVE    := /tmp/sherpa-onnx-android.tar.bz2
SHERPA_JNILIBS    := kotlin/speech/src/androidMain/jniLibs
SHERPA_RELEASE_URL := https://github.com/k2-fsa/sherpa-onnx/releases/download/v$(SHERPA_VERSION)/sherpa-onnx-v$(SHERPA_VERSION)-android.tar.bz2

# ABIs we support (matches ndk { abiFilters } in speech/build.gradle.kts)
ANDROID_ABIS := arm64-v8a x86_64

SENTINEL := $(SHERPA_JNILIBS)/arm64-v8a/libsherpa-onnx-c-api.so

.PHONY: setup setup-sherpa-android clean-sherpa-android

## Run all one-time developer setup steps
setup: setup-sherpa-android

## Download pre-built sherpa-onnx Android .so files (v$(SHERPA_VERSION))
## Required once before building the speech module for Android.
## Files are placed in kotlin/speech/src/androidMain/jniLibs/ and are gitignored.
setup-sherpa-android: $(SENTINEL)

$(SENTINEL):
	@echo "→ Downloading sherpa-onnx v$(SHERPA_VERSION) for Android (~42 MB)..."
	@curl -fL "$(SHERPA_RELEASE_URL)" -o "$(SHERPA_ARCHIVE)"
	@echo "→ Extracting jniLibs/ for ABIs: $(ANDROID_ABIS)..."
	@$(foreach ABI,$(ANDROID_ABIS), \
		mkdir -p "$(SHERPA_JNILIBS)/$(ABI)" && \
		tar -xjf "$(SHERPA_ARCHIVE)" \
			--strip-components=3 \
			-C "$(SHERPA_JNILIBS)/$(ABI)" \
			"./jniLibs/$(ABI)/libsherpa-onnx-c-api.so" \
			"./jniLibs/$(ABI)/libonnxruntime.so" \
			2>/dev/null || true; \
	)
	@rm -f "$(SHERPA_ARCHIVE)"
	@echo "✓ sherpa-onnx Android libs ready in $(SHERPA_JNILIBS)/"

## Remove downloaded sherpa-onnx Android libs
clean-sherpa-android:
	@echo "→ Removing sherpa-onnx Android libs..."
	@rm -rf $(foreach ABI,$(ANDROID_ABIS), \
		"$(SHERPA_JNILIBS)/$(ABI)/libsherpa-onnx-c-api.so" \
		"$(SHERPA_JNILIBS)/$(ABI)/libonnxruntime.so" \
	)
	@echo "✓ Done"

# Show available targets
help:
	@grep -E '^## ' $(MAKEFILE_LIST) | sed 's/## /  /'

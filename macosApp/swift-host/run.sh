#!/bin/sh
# Builds the Kotlin framework, compiles the SwiftUI host against it, and runs it.
set -e
cd "$(dirname "$0")/../.."

./gradlew :macosApp:linkDebugFrameworkMacosArm64

FRAMEWORK_DIR="macosApp/build/bin/macosArm64/debugFramework"
OUT_DIR="macosApp/build/swift-host"
mkdir -p "$OUT_DIR"

swiftc -parse-as-library macosApp/swift-host/HostApp.swift \
  -F "$FRAMEWORK_DIR" \
  -framework CupboardCanvas \
  -Xlinker -rpath -Xlinker "$PWD/$FRAMEWORK_DIR" \
  -o "$OUT_DIR/CupboardHost"

# cwd matters: compose resources fall back to src/commonMain/composeResources relative to cwd
exec "$OUT_DIR/CupboardHost"

#!/bin/sh
# Packages the SwiftUI host + Kotlin framework into a self-contained Cupboard.app.
# Output: macosApp/build/Cupboard.app
#
# Xcode does the real work: the "Compile Kotlin Framework" build phase runs
# :macosApp:embedAndSignAppleFrameworkForXcode (link + embed + sign) and
# :cupboard:macosArm64ProcessResources, and "Stage Compose Resources" nests the
# processed tree under Contents/Resources/compose-resources/. This script only
# drives xcodebuild, lifts the product out of DerivedData, and smoke tests it.
set -e
cd "$(dirname "$0")/.."

DERIVED_DATA="macosApp/build/DerivedData"
APP="$PWD/macosApp/build/Cupboard.app"

# 1. Build the Release configuration.
xcodebuild -project macosApp/Cupboard.xcodeproj \
  -scheme Cupboard \
  -configuration Release \
  -derivedDataPath "$DERIVED_DATA" \
  build

# 2. Lift the product to a stable path.
rm -rf "$APP"
cp -R "$DERIVED_DATA/Build/Products/Release/Cupboard.app" "$APP"

# 3. Smoke test: launch from a cwd outside the repo (the bundle must not rely
# on cwd-relative resource fallbacks), check it survives 5 seconds, kill it.
cd /private/tmp
"$APP/Contents/MacOS/Cupboard" &
SMOKE_PID=$!
sleep 5
if ! kill -0 "$SMOKE_PID" 2>/dev/null; then
  echo "Smoke test FAILED: app exited within 5 seconds" >&2
  exit 1
fi
kill "$SMOKE_PID"
wait "$SMOKE_PID" 2>/dev/null || true
echo "Packaged and smoke-tested: $APP"

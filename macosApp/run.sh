#!/bin/sh
# Dev loop: builds Cupboard.app (Debug) and launches it straight from
# DerivedData so stdout/stderr stay attached to this terminal.
set -e
cd "$(dirname "$0")/.."

DERIVED_DATA="macosApp/build/DerivedData"

xcodebuild -project macosApp/Cupboard.xcodeproj \
  -scheme Cupboard \
  -configuration Debug \
  -derivedDataPath "$DERIVED_DATA" \
  build

# Exec the binary rather than `open`ing the bundle: `open` detaches the process
# and the logs go to the system log instead of here.
exec "$DERIVED_DATA/Build/Products/Debug/Cupboard.app/Contents/MacOS/Cupboard"

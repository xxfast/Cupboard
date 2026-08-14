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

# Launch through LaunchServices rather than exec'ing the binary: an exec'd
# process is never activated, SwiftUI holds the window back until the first
# activation, and the delegate's self-activate at launch is not honored from a
# terminal either, so the window only appeared after a dock click. `open`
# activates the app the way Finder does; --stdout/--stderr keep the logs on
# this terminal, -W keeps the script in the foreground, and the trap makes
# Ctrl-C quit the app rather than orphan it.
trap 'pkill -x Cupboard 2>/dev/null || true' INT TERM
APP="$DERIVED_DATA/Build/Products/Debug/Cupboard.app"
# The log path must be one the app's own process can open, so it is the real
# tty device, not /dev/stdout (which would resolve to the app's stdout, not
# ours; LaunchServices fails the whole launch on it with -10810). Without a
# tty (CI, editors), logs go to the system log like any open'd app.
TTY_PATH="$(tty 2>/dev/null || true)"
if [ -n "$TTY_PATH" ] && [ -e "$TTY_PATH" ]; then
  open -n -W --stdout "$TTY_PATH" --stderr "$TTY_PATH" "$APP"
else
  open -n -W "$APP"
fi

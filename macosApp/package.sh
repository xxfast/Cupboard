#!/bin/sh
# Packages the SwiftUI host + Kotlin framework into a self-contained Cupboard.app.
# Output: macosApp/build/Cupboard.app
set -e
cd "$(dirname "$0")/.."

# 1. Link the Kotlin framework and process compose resources for macosArm64.
./gradlew :macosApp:linkDebugFrameworkMacosArm64 :cupboard:macosArm64ProcessResources

FRAMEWORK_DIR="macosApp/build/bin/macosArm64/debugFramework"
APP="$PWD/macosApp/build/Cupboard.app"

rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Frameworks" "$APP/Contents/Resources"

# 2. Compile the SwiftUI host. Unlike run.sh (which rpaths the build dir for
# dev iteration), the bundled binary resolves the framework relative to itself.
swiftc -parse-as-library macosApp/swift-host/HostApp.swift \
  -F "$FRAMEWORK_DIR" \
  -framework CupboardCanvas \
  -Xlinker -rpath -Xlinker "@executable_path/../Frameworks" \
  -o "$APP/Contents/MacOS/Cupboard"

# 3. Bundle the framework. Its install name is already
# @rpath/CupboardCanvas.framework/Versions/A/CupboardCanvas, so the rpath
# above is enough; no install_name_tool surgery needed.
cp -R "$FRAMEWORK_DIR/CupboardCanvas.framework" "$APP/Contents/Frameworks/"

# 4. Compose resources. The macOS ResourceReader
# (components-resources macosMain ResourceReader.macos.kt, getPathOnDisk) looks
# first at NSBundle.mainBundle.resourcePath + "/compose-resources/" + path,
# where path is "composeResources/<packageOfResClass>/<type>/<file>", e.g.
# "composeResources/io.github.xxfast.cupboard.resources/drawable/compose-multiplatform.xml".
# Its fallbacks are cwd-relative src/ paths (what run.sh relies on). The
# processedResources tree already has the composeResources/<package>/ shape,
# so nest it under Contents/Resources/compose-resources/ (final layout:
# Resources/compose-resources/composeResources/<package>/drawable/...).
mkdir -p "$APP/Contents/Resources/compose-resources"
cp -R cupboard/build/processedResources/macosArm64/main/composeResources \
  "$APP/Contents/Resources/compose-resources/"

# 5. Info.plist
cat > "$APP/Contents/Info.plist" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleIdentifier</key>
	<string>io.github.xxfast.cupboard</string>
	<key>CFBundleName</key>
	<string>Cupboard</string>
	<key>CFBundleExecutable</key>
	<string>Cupboard</string>
	<key>CFBundlePackageType</key>
	<string>APPL</string>
	<key>CFBundleShortVersionString</key>
	<string>0.1.0</string>
	<key>CFBundleVersion</key>
	<string>1</string>
	<key>CFBundleInfoDictionaryVersion</key>
	<string>6.0</string>
	<key>NSHighResolutionCapable</key>
	<true/>
	<key>LSMinimumSystemVersion</key>
	<string>14.0</string>
</dict>
</plist>
EOF

# 6. Ad-hoc sign and verify.
codesign --force --deep --sign - "$APP"
codesign --verify --deep --strict "$APP"

# 7. Smoke test: launch from a cwd outside the repo (the bundle must not rely
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

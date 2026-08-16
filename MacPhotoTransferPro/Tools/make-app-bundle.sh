#!/bin/bash
#
# Assembles MacPhotoTransferPro.app around the swift build product.
#
#   ./Tools/make-app-bundle.sh [debug|release] [output-dir]
#
# A SwiftPM executable target produces a bare binary, which has nowhere to carry
# an icon or declare its capabilities, so the Dock shows a generic tile. Wrapping
# the binary in a bundle is what gives it the icon, the app name, and the
# Info.plist that macOS local network privacy requires for Bonjour.
#
# Signing depends on what is available, and the script says which path it took:
#
#   Developer ID + notarization  when Tools/signing.env supplies credentials
#   Developer ID only            when it supplies an identity but no Apple ID
#   ad-hoc                       otherwise, which Gatekeeper rejects on other Macs
#
# See docs/sharing.md.

set -euo pipefail

readonly app_name="MacPhotoTransferPro"
readonly bundle_id="com.agiletech.mac.phototransfer"
readonly short_version="0.1.0"
readonly bundle_version="1"
readonly minimum_system_version="14.0"
readonly bonjour_service="_androidphototransfer._tcp"

readonly configuration="${1:-release}"
readonly package_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly output_dir="${2:-$package_dir/.build}"
readonly app_dir="$output_dir/$app_name.app"
readonly archive="$output_dir/$app_name.zip"
readonly icon_source="$package_dir/Resources/AppIcon.icns"
readonly signing_env="$package_dir/Tools/signing.env"

if [[ "$configuration" != "debug" && "$configuration" != "release" ]]; then
    echo "error: configuration must be debug or release, got '$configuration'" >&2
    exit 1
fi

if [[ ! -f "$icon_source" ]]; then
    echo "error: no icon at $icon_source, run Tools/make-appicon.swift first" >&2
    exit 1
fi

# Release ships both architectures so an Intel Mac can run it; a host-only build
# simply fails to launch there. Debug stays host-only to keep rebuilds quick.
build_flags() {
    if [[ "$configuration" == "release" ]]; then
        echo "-c release --arch arm64 --arch x86_64"
    else
        echo "-c debug"
    fi
}

# Build progress goes to stderr so it does not end up in the captured path.
build_binary() {
    local flags bin_path
    flags="$(build_flags)"
    swift build $flags >&2
    bin_path="$(swift build $flags --show-bin-path 2> /dev/null | tail -1)"
    echo "$bin_path/$app_name"
}

write_info_plist() {
    cat > "$app_dir/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>$app_name</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon</string>
    <key>CFBundleIdentifier</key>
    <string>$bundle_id</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>$app_name</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>$short_version</string>
    <key>CFBundleVersion</key>
    <string>$bundle_version</string>
    <key>LSApplicationCategoryType</key>
    <string>public.app-category.utilities</string>
    <key>LSMinimumSystemVersion</key>
    <string>$minimum_system_version</string>
    <key>NSBonjourServices</key>
    <array>
        <string>$bonjour_service</string>
    </array>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSLocalNetworkUsageDescription</key>
    <string>$app_name receives photos from your phone over the local network.</string>
</dict>
</plist>
PLIST

    plutil -lint "$app_dir/Contents/Info.plist" > /dev/null
}

# No entitlements file: the hardened runtime only restricts JIT, unsigned
# executable memory, dyld environment variables, and library validation, none of
# which this app uses. The network and file entitlements that Electron apps carry
# are App Sandbox entitlements, and this app is not sandboxed.
sign_bundle() {
    local identity="${MAC_SIGNING_IDENTITY:--}"
    local output

    # codesign is chatty about replacing a signature, so its output is held back
    # and only shown when it actually failed, usually a missing identity.
    if ! output="$(codesign --force --timestamp --options runtime \
        --sign "$identity" "$app_dir" 2>&1)"; then
        echo "error: signing with '$identity' failed" >&2
        echo "$output" >&2
        echo "hint: list available identities with" \
            "security find-identity -v -p codesigning" >&2
        return 1
    fi

    if [[ "$identity" == "-" ]]; then
        echo "signed ad-hoc: Gatekeeper will reject this on another Mac"
    else
        echo "signed with: $identity"
    fi
}

archive_bundle() {
    # ditto, not zip: a bundle is a directory tree with symlinks and extended
    # attributes, and zip drops enough of them to invalidate the signature.
    rm -f "$archive"
    ditto -c -k --sequesterRsrc --keepParent "$app_dir" "$archive"
}

notarize_archive() {
    echo "submitting to Apple for notarization, which usually takes a few minutes"
    xcrun notarytool submit "$archive" \
        --apple-id "$APPLE_ID" \
        --team-id "$APPLE_TEAM_ID" \
        --password "$APPLE_APP_SPECIFIC_PASSWORD" \
        --wait

    # Stapling attaches the ticket to the bundle, so a recipient who is offline
    # is not left waiting on Apple to confirm it.
    xcrun stapler staple "$app_dir"
    archive_bundle
    echo "notarized and stapled"
}

can_notarize() {
    [[ -n "${MAC_SIGNING_IDENTITY:-}" && -n "${APPLE_ID:-}" &&
       -n "${APPLE_TEAM_ID:-}" && -n "${APPLE_APP_SPECIFIC_PASSWORD:-}" ]]
}

cd "$package_dir"

if [[ -f "$signing_env" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$signing_env"
    set +a
fi

binary="$(build_binary)"

if [[ ! -x "$binary" ]]; then
    echo "error: no executable at $binary" >&2
    exit 1
fi

rm -rf "$app_dir"
mkdir -p "$app_dir/Contents/MacOS" "$app_dir/Contents/Resources"
cp "$binary" "$app_dir/Contents/MacOS/$app_name"
cp "$icon_source" "$app_dir/Contents/Resources/AppIcon.icns"

write_info_plist
sign_bundle
archive_bundle

if can_notarize; then
    notarize_archive
fi

touch "$app_dir"
echo "built $app_dir ($(lipo -archs "$app_dir/Contents/MacOS/$app_name"))"
echo "run it with: open '$app_dir'"
echo "share $archive (see docs/sharing.md for what the recipient has to do)"

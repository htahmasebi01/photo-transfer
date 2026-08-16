# Sharing the app

This covers handing both halves to someone you know. Neither half is signed by an identity
Apple or Google recognises, so every recipient has to tell their OS to trust it once. That is
the price of skipping a $99/year Apple Developer account and a Play Store listing.

## What to tell every recipient

Traffic between the phone and the Mac is **plain HTTP**. Photo bytes and filenames are readable
by anyone on the same network. Pairing stops strangers from uploading, but it does not encrypt
anything. This is fine at home and not fine on hotel, airport, or conference Wi-Fi.

Pairing is the one step to be careful about: it sends the shared secret in the clear, so someone
capturing that exchange could impersonate either device afterwards. Tell recipients to pair on
their home network, once. Everything after that is protected by the secret, in both directions.

## Mac receiver

### Build the archive

```bash
cd MacPhotoTransferPro
./Tools/make-app-bundle.sh release
```

That leaves `.build/MacPhotoTransferPro.zip`. Send that file, not the `.app` directly: an `.app`
is a directory tree, and most chat clients or `zip` mangle it enough to break the signature.
The script uses `ditto`, which does not.

A release build is universal, `arm64` and `x86_64` in one binary, because a host-only build does
not launch at all on an Intel Mac. The script prints the architectures it produced. Debug builds
stay host-only so rebuilds are quick.

### What the recipient does

macOS marks anything downloaded with a quarantine flag, and because the app is ad-hoc signed
rather than notarized, Gatekeeper refuses it. Two ways through:

Either right-click the app, choose **Open**, and confirm at the prompt. On macOS 15 and later
the confirmation moved to **System Settings → Privacy & Security → Open Anyway** after the first
blocked attempt.

Or, from Terminal:

```bash
xattr -dr com.apple.quarantine /Applications/MacPhotoTransferPro.app
```

Both leave the signature intact. Only the quarantine flag drives the launch block; `spctl`
will keep reporting `rejected` either way, because that assesses notarization, which this
build does not have.

On first run the app asks for local network access, needed for Bonjour, and for access to
whichever folder the user picks as the destination.

### Removing the warning instead

The warning exists because the app is ad-hoc signed. With a paid Apple Developer account you can
sign with a Developer ID and have Apple notarize the result, after which it opens by double-click
like any other app. Copy `Tools/signing.env.sample` to `Tools/signing.env`, which `.gitignore`
excludes, and fill in the four values. The bundle script picks it up automatically and prints
which path it took: ad-hoc, Developer ID, or Developer ID plus notarization.

Notarization is an upload to Apple that takes a few minutes, then `stapler` attaches the
resulting ticket to the bundle so a recipient who is offline is not left waiting on Apple.

The app needs no entitlements file. The hardened runtime, which notarization requires, only
restricts JIT, unsigned executable memory, dyld environment variables, and library validation,
and this app uses none of them. Electron apps carry a long entitlements list because they need
JIT, and their network and file entitlements are App Sandbox keys that do nothing in a
non-sandboxed app. Copying that list here would weaken the hardened runtime for no benefit.

## Android sender

### One-time signing setup

A release APK needs a signing key. Generate one and keep it somewhere backed up outside the
repo, because losing it means never being able to ship an update that installs over the old one:

```bash
keytool -genkeypair -v \
  -keystore ~/keys/phototransfer-release.jks \
  -alias phototransfer -keyalg RSA -keysize 4096 -validity 10000
```

Then create `android/keystore.properties`, which `.gitignore` already excludes:

```properties
storeFile=/Users/you/keys/phototransfer-release.jks
storePassword=…
keyAlias=phototransfer
keyPassword=…
```

`storeFile` may be absolute or relative to the `android/` directory.

### Build the APK

```bash
cd android
./gradlew :app:assembleRelease
```

The result is `app/build/outputs/apk/release/app-release.apk`. If you get
`app-release-unsigned.apk` instead, the properties file was not found, and that APK will not
install. Confirm the signature before sending it:

```bash
"$ANDROID_HOME"/build-tools/36.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

### What the recipient does

Sideloading an APK means allowing the app that delivered it (Files, Drive, Gmail) to install
apps: **Settings → Apps → Special app access → Install unknown apps**. Play Protect will warn
about an unrecognised developer, which is expected for a key Google has never seen.

There is no update channel. A new build means sending a new APK, and it only installs over the
old one if signed with the same key.

## Then, on both devices

1. Open the receiver on the Mac and choose a destination folder.
2. Click **Pair a Device** to get a six-digit code, valid for 3 minutes.
3. On the phone, pick photos and tap **Send**. It asks for the code the first time.
4. Approve the phone on the Mac. The transfer continues on its own.

Pairing is remembered on both sides, so this happens once per phone. The Mac can **Remove** a
device at any time, which revokes its upload access immediately.

## What would make this properly shareable

| Gap | What it takes |
| --- | --- |
| Gatekeeper warning on the Mac | A Developer ID certificate. The script already handles signing and notarization once `Tools/signing.env` exists. |
| Play Protect warning on Android | A Play Store listing, which also needs a privacy policy for photo access |
| No encryption on the wire | TLS, which means replacing FlyingFox since it has no TLS support |
| No update path | Sparkle for the Mac, Play Store or an in-app version check for Android |
| Manual release steps | CI that holds the certificate as a base64 secret and publishes to GitHub Releases |

For a worked example of the last two, [OpenMTP](https://github.com/ganeshrvel/openmtp) notarizes
in an `afterSign` hook, publishes DMGs to GitHub Releases, and ships updates through
`electron-updater` and a Homebrew cask. Its CI passes the signing certificate as a base64-encoded
`.p12` in an environment variable, which is the standard way to get a certificate onto a build
machine you do not own.

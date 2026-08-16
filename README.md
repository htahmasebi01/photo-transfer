# Photo Transfer

Transfer photos from an Android phone to a MacBook over the local Wi-Fi network. No cloud, no cables.

Two apps in one monorepo:

| Directory | App | Stack |
|---|---|---|
| `android/` | Sender | Kotlin, Jetpack Compose, Hilt, Photo Picker, NsdManager, OkHttp |
| `MacPhotoTransferPro/` | Receiver | SwiftUI, FlyingFox HTTP server, Bonjour (Network.framework) |

## How it works

1. The Mac app advertises `_androidphototransfer._tcp` via Bonjour and runs a local HTTP server.
2. The Android app discovers the Mac with Android NSD (mDNS/DNS-SD).
3. You pick photos with the system Photo Picker.
4. Photos stream over HTTP to the Mac, which writes them to a temp file and atomically moves them into your chosen destination folder.

See [docs/protocol.md](docs/protocol.md) for the wire protocol and
[docs/android-architecture.md](docs/android-architecture.md) for the Android
module graph and layering rules.

## Current scope

- Multi-photo selection and batch transfer with per-file progress
- Bonjour discovery, plus manual IP entry as a fallback
- Collision-safe filenames on the Mac (`IMG_1234 (1).jpg`)
- Pairing with a six-digit code plus explicit approval on the Mac
- HMAC-SHA256 request signing, so only paired devices can upload
- The receiver signs its replies too, so the phone can tell it apart from an impostor

Not yet implemented: TLS, checksums, resumable uploads, foreground service, transfer history.

### Security status

Pairing closes the hole that mattered most: without it, anyone on the same Wi-Fi could write
files into the receiver's chosen folder. Uploads are signed with a per-device secret, and a
nonce cache blocks replays, so an eavesdropper cannot forge or resend a transfer.

Signing runs both ways. A `receiverId` is broadcast in cleartext, so being paired with one says
nothing about what is answering on a given address. Before sending anything, the phone makes the
Mac prove it holds the pairing secret, and refuses to continue if it cannot.

Two things are worth being clear about:

Traffic is **plain HTTP**, so photo bytes and filenames are readable on the network. Adding TLS
means replacing the embedded HTTP server, since FlyingFox has no TLS support.

**Pairing itself sends the shared secret in cleartext.** Anyone who captures that one exchange can
impersonate either side from then on, and no later check detects it. So pair on a network you
trust, and treat the result as safe at home, not on public Wi-Fi.
[docs/protocol.md](docs/protocol.md) lists the remaining gaps.

## Pairing a phone with a Mac

1. Start the receiver on the Mac and choose a destination folder.
2. Click **Pair a Device**. A six-digit code appears, valid for 3 minutes.
3. On the phone, select photos and tap **Send**. When the receiver is not yet paired, the app
   asks for the code.
4. Enter the code, then approve the phone on the Mac. The transfer resumes automatically.

The Mac lists paired devices and can **Remove** any of them, which revokes its upload access
immediately. Pairings survive restarts on both sides: the secret lives in the login Keychain on
the Mac and in the Android keystore on the phone.

## Running the Mac receiver

Requirements: macOS with Xcode 26+. The Mac app is a Swift package.

```bash
cd MacPhotoTransferPro
swift run MacPhotoTransferPro
# or: open Package.swift in Xcode and run the MacPhotoTransferPro scheme
```

In the app:

1. Click "Choose Folder" and pick a destination folder.
2. Click "Start Receiving". The window shows the advertised name and port.

Both of those run a bare executable, which has no icon and no `Info.plist`, so the Dock
shows a generic `exec` tile. To run it as a real app instead:

```bash
cd MacPhotoTransferPro
./Tools/make-app-bundle.sh          # or: ./Tools/make-app-bundle.sh debug
open .build/MacPhotoTransferPro.app
```

That wraps the binary in `MacPhotoTransferPro.app` with the icon and an `Info.plist`
declaring the Bonjour service, which macOS local network privacy needs. The bundle is
signed with the hardened runtime, ad-hoc unless `Tools/signing.env` supplies a Developer ID,
so the local network permission grant survives rebuilds. A release build is universal, `arm64`
and `x86_64`, since a host-only build will not launch on an Intel Mac. The script also writes
`MacPhotoTransferPro.zip` next to it, which is the form to send someone else.

## Icons

| App | Asset | Notes |
|---|---|---|
| Android | `android/app/src/main/res/drawable/ic_launcher_*.xml` | Adaptive icon, vector only. `minSdk` is 29, so no PNG density buckets are needed. Includes a monochrome layer for Android 13 themed icons. |
| macOS | `MacPhotoTransferPro/Resources/AppIcon.icns` | Built from `AppIcon.png`, the 1024x1024 master. |

Regenerate the macOS icon from a square source render:

```bash
cd MacPhotoTransferPro
swift Tools/make-appicon.swift path/to/render.png Resources
```

The macOS icon only reaches the Dock through an app bundle, see "Running the Mac receiver".

## Running the Android sender

Requirements: JDK 17+, Android SDK, a device or emulator on the same Wi-Fi as the Mac.

```bash
cd android
./gradlew installDebug
```

In the app:

1. Tap "Select photos" and pick one or more images.
2. Wait for your Mac to appear in the device list (or enter its IP and port manually).
3. Tap the Mac to start the transfer and watch the progress.

## End-to-end test checklist

1. Mac and phone on the same Wi-Fi network.
2. Start the Mac receiver, choose a folder, note the port.
3. Send 3+ photos from the phone.
4. Verify all files appear in the destination folder with correct names and sizes.
5. Send the same photos again. Verify collision-safe names like `IMG_1234 (1).jpg`.

## Giving it to someone else

See [docs/sharing.md](docs/sharing.md). Both halves are signed by keys neither Apple nor
Google recognises, so each recipient has to approve the app once: **Open Anyway** on the Mac,
and allowing installs from unknown sources on Android. Building a signed Android release
needs an `android/keystore.properties` that the repo deliberately does not contain.

## Tests

```bash
# Android unit tests
cd android && ./gradlew test

# Android keystore-backed pairing storage, which needs a device or emulator
cd android && ./gradlew :data:pairing:impl:connectedDebugAndroidTest

# Mac unit tests
cd MacPhotoTransferPro && swift test
```

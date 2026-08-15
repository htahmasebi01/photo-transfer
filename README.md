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

## Current scope (walking skeleton)

- Multi-photo selection and batch transfer with per-file progress
- Bonjour discovery, plus manual IP entry as a fallback
- Collision-safe filenames on the Mac (`IMG_1234 (1).jpg`)
- Plain HTTP on the local network only

Not yet implemented: QR pairing, TLS, checksums, resumable uploads, foreground service, transfer history.

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

## Tests

```bash
# Android unit tests
cd android && ./gradlew test

# Mac unit tests
cd MacPhotoTransferPro && swift test
```

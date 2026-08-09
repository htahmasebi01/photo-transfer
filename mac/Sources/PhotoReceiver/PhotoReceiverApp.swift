import AppKit
import SwiftUI

@main
struct PhotoReceiverApp: App {

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    // Required when launched via `swift run` (no app bundle):
                    // promotes the process to a regular UI app with a window.
                    NSApp.setActivationPolicy(.regular)
                    NSApp.activate(ignoringOtherApps: true)
                }
        }
    }
}

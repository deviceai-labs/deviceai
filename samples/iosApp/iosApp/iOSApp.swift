import SwiftUI
import UIKit
import AVFoundation

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Request microphone permission at the earliest iOS lifecycle point.
        // Handled in Swift because the Kotlin/Native → ObjC callback bridge
        // does not reliably trigger the system dialog.
        AVCaptureDevice.requestAccess(for: .audio) { _ in }
        return true
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

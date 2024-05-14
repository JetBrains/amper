import SwiftUI
import interop

@main
struct iosApp: App {

    var body: some Scene {
        WindowGroup {
            Text(Proxy().proxyHello())
        }
    }

    @MainActor init() {

    }
}
import SwiftUI
import KotlinModules

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
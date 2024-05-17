import SwiftUI
import kotlin

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
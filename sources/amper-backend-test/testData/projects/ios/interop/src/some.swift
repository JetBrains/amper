import SwiftUI
import interopKotlin

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
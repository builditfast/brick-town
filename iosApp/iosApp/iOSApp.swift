import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        MainViewControllerKt.startKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    MainViewControllerKt.deliverLink(url: url.absoluteString)
                }
        }
    }
}

import SwiftUI
import UIKit
import ComposeApp

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea()
                .onOpenURL { url in MainViewControllerKt.handleLink(url: url.absoluteString) }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

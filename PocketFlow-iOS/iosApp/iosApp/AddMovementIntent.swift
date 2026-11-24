import AppIntents
import ComposeApp
import Foundation

/// Shows up in the Shortcuts app as an action. With the Wallet "transaction" automation it
/// receives the amount, merchant and card of an Apple Pay payment and opens PocketFlow with
/// a new movement filled in; the movement is saved only after the user confirms it.
struct AddMovementIntent: AppIntent {
    static let title: LocalizedStringResource = "Add movement to PocketFlow"
    static let description = IntentDescription("Opens PocketFlow with a new movement filled in, ready to confirm.")
    static let openAppWhenRun: Bool = true

    @Parameter(title: "Amount")
    var amount: String

    @Parameter(title: "Merchant")
    var merchant: String?

    @Parameter(title: "Card")
    var card: String?

    @MainActor
    func perform() async throws -> some IntentResult {
        var link = URLComponents()
        link.scheme = "pocketflow"
        link.host = "expense"
        link.queryItems = [
            URLQueryItem(name: "amount", value: amount),
            URLQueryItem(name: "merchant", value: merchant ?? ""),
            URLQueryItem(name: "card", value: card ?? ""),
        ]
        if let url = link.url {
            MainViewControllerKt.handleLink(url: url.absoluteString)
        }
        return .result()
    }
}

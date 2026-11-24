import SwiftUI
import WidgetKit

// The iOS counterparts of the Android widgets: buttons that open PocketFlow straight at
// "new movement", "new standing order" or "new account". They show no data, so they need
// no storage shared with the app; a tap opens a pocketflow://widget/... link.

private let widgetBlue = Color(red: 0x24 / 255.0, green: 0x5A / 255.0, blue: 0xD5 / 255.0)
private let softWhite = Color(red: 0xDC / 255.0, green: 0xE7 / 255.0, blue: 1.0)

struct QuickEntry: TimelineEntry {
    let date: Date
}

struct QuickProvider: TimelineProvider {
    func placeholder(in context: Context) -> QuickEntry { QuickEntry(date: Date()) }
    func getSnapshot(in context: Context, completion: @escaping (QuickEntry) -> Void) { completion(QuickEntry(date: Date())) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<QuickEntry>) -> Void) {
        completion(Timeline(entries: [QuickEntry(date: Date())], policy: .never))
    }
}

extension View {
    /// iOS 17 wants the widget background declared this way; earlier versions take a plain background.
    @ViewBuilder
    func widgetSurface() -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            self.containerBackground(for: .widget) { widgetBlue }
        } else {
            self.frame(maxWidth: .infinity, maxHeight: .infinity).background(widgetBlue)
        }
    }
}

struct AddMovementView: View {
    var body: some View {
        VStack(spacing: 6) {
            Text("+").font(.system(size: 40, weight: .semibold))
            Text("הוספת\nתנועה").font(.system(size: 15, weight: .bold)).multilineTextAlignment(.center)
        }
        .foregroundColor(.white)
        .widgetURL(URL(string: "pocketflow://widget/movement"))
        .widgetSurface()
    }
}

struct AddMovementWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "com.tal.pocketflow.add", provider: QuickProvider()) { _ in AddMovementView() }
            .configurationDisplayName("PocketFlow · הוספת תנועה")
            .description("הוספת תנועה בלחיצה")
            .supportedFamilies([.systemSmall])
    }
}

struct QuickAction: View {
    let label: String
    let link: String
    var body: some View {
        Link(destination: URL(string: link)!) {
            VStack(spacing: 4) {
                Text("+").font(.system(size: 26, weight: .semibold))
                Text(label).font(.system(size: 13, weight: .bold)).multilineTextAlignment(.center).lineLimit(2).minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.white.opacity(0.14))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
    }
}

struct QuickActionsView: View {
    var body: some View {
        VStack(spacing: 8) {
            Text("PocketFlow").font(.system(size: 12, weight: .semibold)).foregroundColor(softWhite)
            HStack(spacing: 8) {
                QuickAction(label: "חשבון/כרטיס", link: "pocketflow://widget/account")
                QuickAction(label: "הוראת קבע", link: "pocketflow://widget/rule")
                QuickAction(label: "הוספת תנועה", link: "pocketflow://widget/movement")
            }
        }
        .foregroundColor(.white)
        .widgetSurface()
    }
}

struct QuickActionsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "com.tal.pocketflow.actions", provider: QuickProvider()) { _ in QuickActionsView() }
            .configurationDisplayName("PocketFlow · פעולות מהירות")
            .description("תנועה, הוראת קבע או חשבון חדש בלחיצה")
            .supportedFamilies([.systemMedium])
    }
}

@main
struct PocketFlowWidgetBundle: WidgetBundle {
    var body: some Widget {
        AddMovementWidget()
        QuickActionsWidget()
    }
}

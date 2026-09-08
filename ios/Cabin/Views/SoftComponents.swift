import SwiftUI

// MARK: - Building blocks from the Cabin Soft design
//
// Each view here maps to one class in the hand-off prototype (.phone, .mark,
// .circ, .card, .tile, .tab, .pill, .bub, .vtag, .otag, .row, .pbtn, .sbtn) so
// screens can be assembled from the same vocabulary the design used.

/// The pale green-to-cream ground behind every screen.
struct SoftBackground: View {
    var body: some View {
        LinearGradient(
            stops: [
                .init(color: .softBackgroundTop, location: 0),
                .init(color: .softBackgroundMid, location: 0.45),
                .init(color: .softBackgroundBottom, location: 1),
            ],
            startPoint: .top, endPoint: .bottom
        )
        .ignoresSafeArea()
    }
}

extension View {
    /// Paints the soft gradient behind a screen and hides the system bar, since
    /// every screen draws its own header.
    func softScreen() -> some View {
        self
            .background(SoftBackground())
            .toolbar(.hidden, for: .navigationBar)
            .foregroundStyle(Color.softText)
    }
}

/// The 44pt black tile with the house glyph (.mark). Sits top-left of every main screen.
struct AppMark: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: SoftRadius.mark, style: .continuous)
                .fill(Color.softInk)
            Image(systemName: "house")
                .font(.system(size: 20, weight: .regular))
                .foregroundStyle(.white)
        }
        .frame(width: 44, height: 44)
    }
}

/// House glyph + "cabin" wordmark from the welcome screen.
struct Wordmark: View {
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "house")
                .font(.system(size: 26, weight: .regular))
                .foregroundStyle(Color.softInk)
            Text("cabin")
                .font(.soft(26, .regular))
                .tracking(-0.26)
                .foregroundStyle(Color.softText)
        }
    }
}

/// Translucent white circle button (.circ) used for back, search, chat, close, settings.
struct CircleButton: View {
    let systemImage: String
    var size: CGFloat = 48
    var filled: Bool = false           // solid white instead of 75% white
    var inverted: Bool = false         // black fill, white glyph (send button)
    var badge: Bool = false            // orange dot top-right
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle().fill(inverted ? Color.softInk : (filled ? Color.white : Color.white.opacity(0.75)))
                Image(systemName: systemImage)
                    .font(.system(size: size * 0.42, weight: .regular))
                    .foregroundStyle(inverted ? Color.white : Color.softTextSoft)
                if badge {
                    Circle().fill(Color.softAccent)
                        .frame(width: 8, height: 8)
                        .offset(x: size * 0.25, y: -size * 0.25)
                }
            }
            .frame(width: size, height: size)
            .softShadow(.circle)
        }
        .buttonStyle(SoftPressStyle())
    }
}

/// Header row used on every main screen: leading mark (or back circle), a title, a trailing action.
struct SoftHeader<Leading: View, Title: View, Trailing: View>: View {
    @ViewBuilder var leading: Leading
    @ViewBuilder var title: Title
    @ViewBuilder var trailing: Trailing

    var body: some View {
        ZStack {
            title
            HStack {
                leading
                Spacer()
                trailing
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 8)
    }
}

/// Black pill button (.pbtn).
struct PrimaryButton: View {
    let title: String
    var systemImage: String? = nil
    var large: Bool = false
    var loading: Bool = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if loading {
                    ProgressView().tint(.white)
                } else {
                    if let systemImage { Image(systemName: systemImage) }
                    Text(title)
                }
            }
            .font(.soft(large ? 17 : 15, .regular))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, large ? 18 : 14)
            .padding(.horizontal, 22)
            .background(Color.softInk, in: Capsule())
        }
        .buttonStyle(SoftPressStyle())
        .disabled(loading)
    }
}

/// White (or tinted) pill button (.sbtn).
struct SecondaryButton: View {
    let title: String
    var systemImage: String? = nil
    var large: Bool = false
    var tint: Color = .white
    var loading: Bool = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if loading {
                    ProgressView().tint(Color.softInk)
                } else {
                    if let systemImage { Image(systemName: systemImage) }
                    Text(title)
                }
            }
            .font(.soft(large ? 16 : 15, .regular))
            .foregroundStyle(Color.softText)
            .frame(maxWidth: .infinity)
            .padding(.vertical, large ? 16 : 14)
            .padding(.horizontal, 22)
            .background(tint, in: Capsule())
        }
        .buttonStyle(SoftPressStyle())
        .disabled(loading)
    }
}

/// Small text-only action ("Send a new code", "Log in").
struct SoftLink: View {
    let title: String
    var muted: Bool = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.soft(14, muted ? .light : .regular))
                .foregroundStyle(muted ? Color.softSecondary : Color.softText)
        }
        .buttonStyle(SoftPressStyle())
    }
}

/// Dims slightly while pressed; no system highlight.
struct SoftPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.7 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

/// Black check tag (.vtag) — the verification badge. `light` is the white
/// variant used for "Featured", which must never read as a trust signal.
struct VTag: View {
    let text: String
    var systemImage: String? = "checkmark"
    var light: Bool = false
    var tint: Color? = nil

    var body: some View {
        HStack(spacing: 5) {
            if let systemImage {
                Image(systemName: systemImage).font(.system(size: 10, weight: .bold))
            }
            Text(text).font(.softTag)
        }
        .foregroundStyle(light ? Color.softText : .white)
        .padding(.horizontal, 11)
        .padding(.vertical, 5)
        .background(tint ?? (light ? Color.white.opacity(0.9) : Color.softInk), in: Capsule())
    }
}

/// Outlined tag (.otag): "Optional", "★ 4.8 · 12", "Pending".
struct OTag: View {
    let text: String
    var filled: Bool = false

    var body: some View {
        Text(text)
            .font(.softTag)
            .foregroundStyle(Color.softLabel)
            .padding(.horizontal, 11)
            .padding(.vertical, 4)
            .background(filled ? Color.white : Color.clear, in: Capsule())
            .overlay(Capsule().stroke(Color.softOutline, lineWidth: 1))
    }
}

/// White rounded card (.card).
struct SoftCard<Content: View>: View {
    var padding: CGFloat = 16
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white, in: RoundedRectangle(cornerRadius: SoftRadius.card, style: .continuous))
            .softShadow(.card)
    }
}

/// Tinted stat tile (.tile): icon, label, big number.
struct SoftTile: View {
    let systemImage: String
    let label: String
    let value: String
    var compact: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: compact ? 18 : 26) {
            Image(systemName: systemImage)
                .font(.system(size: 20, weight: .light))
                .foregroundStyle(Color.softTextSoft.opacity(0.75))
                .frame(height: 22, alignment: .center) // keeps labels level across different glyphs
            VStack(alignment: .leading, spacing: 6) {
                Text(label)
                    .font(.soft(compact ? 14 : 15))
                    .foregroundStyle(Color.softLabel)
                Text(value)
                    .font(.soft(compact ? 30 : 38))
                    .tracking(-0.02 * (compact ? 30 : 38))
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                    .foregroundStyle(Color.softTextSoft)
            }
        }
        .padding(compact ? 14 : 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.softTile, in: RoundedRectangle(cornerRadius: SoftRadius.tile, style: .continuous))
    }
}

/// White list row (.row).
struct SoftRow<Content: View>: View {
    var fill: Color = .white
    var shadow: Bool = true
    @ViewBuilder var content: Content

    var body: some View {
        HStack(spacing: 12) { content }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(fill, in: RoundedRectangle(cornerRadius: SoftRadius.row, style: .continuous))
            .softShadow(shadow ? .row : .circle)
    }
}

/// Pill-shaped white text field (.bub input).
struct SoftField: View {
    let placeholder: String
    @Binding var text: String
    var secure: Bool = false
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType? = nil
    var capitalization: TextInputAutocapitalization = .sentences

    var body: some View {
        Group {
            if secure {
                SecureField("", text: $text, prompt: prompt)
            } else {
                TextField("", text: $text, prompt: prompt)
                    .keyboardType(keyboard)
                    .textInputAutocapitalization(capitalization)
                    .autocorrectionDisabled(keyboard == .emailAddress)
            }
        }
        .textContentType(contentType)
        .font(.soft(16))
        .foregroundStyle(Color.softText)
        .padding(.vertical, 16)
        .padding(.horizontal, 20)
        .background(Color.white, in: Capsule())
        .softShadow(.row)
    }

    private var prompt: Text {
        Text(placeholder).font(.soft(16)).foregroundStyle(Color.softMuted)
    }
}

/// Two-way pill segment ("Private individual" / "Real estate agent").
struct SoftSegment<T: Hashable>: View {
    let options: [(T, String)]
    @Binding var selection: T

    var body: some View {
        HStack(spacing: 0) {
            ForEach(options, id: \.0) { value, label in
                let on = value == selection
                Button {
                    withAnimation(.easeOut(duration: 0.15)) { selection = value }
                } label: {
                    Text(label)
                        .font(.soft(15, .regular))
                        .foregroundStyle(on ? .white : Color.softLabel)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .padding(.horizontal, 10)
                        .background(on ? Color.softInk : Color.clear, in: Capsule())
                }
                .buttonStyle(SoftPressStyle())
            }
        }
        .padding(5)
        .background(Color.white, in: Capsule())
        .softShadow(.row)
    }
}

/// Filter chip: black when selected, white otherwise.
struct SoftChip: View {
    let title: String
    let selected: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.soft(14, .regular))
                .foregroundStyle(selected ? .white : Color.softLabel)
                .padding(.horizontal, 16)
                .padding(.vertical, 9)
                .background(selected ? Color.softInk : Color.white.opacity(0.75), in: Capsule())
        }
        .buttonStyle(SoftPressStyle())
    }
}

/// Initials avatar (.av).
struct SoftAvatar: View {
    let name: String
    var size: CGFloat = 40

    var body: some View {
        ZStack {
            Circle().fill(Color.softAvatar)
            Text(initials)
                .font(.soft(size * 0.34))
                .foregroundStyle(Color.softTextSoft)
        }
        .frame(width: size, height: size)
    }

    private var initials: String {
        name.split(separator: " ").prefix(2).compactMap { $0.first.map(String.init) }.joined().uppercased()
    }
}

/// Black floating stat pill on the map (.pill).
struct SoftPill: View {
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.soft(26, .regular))
                .tracking(-0.26)
                .lineLimit(1)
            Text(label)
                .font(.soft(13))
                .opacity(0.75)
                .lineLimit(1)
        }
        .foregroundStyle(.white)
        .padding(.vertical, 12)
        .padding(.horizontal, 22)
        .background(Color.softInk, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .softShadow(.pill)
    }
}

/// Photo placeholder shown where the design has a drop zone and we have no image.
struct SoftPhotoPlaceholder: View {
    var body: some View {
        ZStack {
            LinearGradient(colors: [Color.softAvatar, Color(red: 169 / 255, green: 196 / 255, blue: 184 / 255)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
            Image(systemName: "house")
                .font(.system(size: 28, weight: .light))
                .foregroundStyle(Color.softText.opacity(0.6))
        }
    }
}

/// Rounded remote photo with the soft placeholder.
struct SoftPhoto: View {
    let url: String?
    var radius: CGFloat = SoftRadius.image

    var body: some View {
        RemoteImage(url: url)
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}

/// Centred empty / error state.
struct SoftEmpty: View {
    let systemImage: String
    let title: String
    let message: String
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 34, weight: .light))
                .foregroundStyle(Color.softMuted)
            Text(title).font(.soft(22)).foregroundStyle(Color.softText)
            Text(message)
                .font(.soft(15))
                .foregroundStyle(Color.softSecondary)
                .multilineTextAlignment(.center)
            if let actionTitle, let action {
                SoftLink(title: actionTitle, action: action).padding(.top, 4)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 32)
        .padding(.top, 60)
    }
}

/// Inline error line under a form.
struct SoftError: View {
    let message: String?

    var body: some View {
        if let message {
            Text(message)
                .font(.soft(14))
                .foregroundStyle(Color.softRed)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 6)
        }
    }
}

// MARK: - Tab bar

enum SoftTab: CaseIterable, Hashable {
    case home, chat, map, viewings, profile

    var systemImage: String {
        switch self {
        case .home:     return "house"
        case .chat:     return "bubble.left"
        case .map:      return "mappin.and.ellipse"
        case .viewings: return "doc.text"
        case .profile:  return "person"
        }
    }

    var label: String {
        switch self {
        case .home:     return "Browse"
        case .chat:     return "Messages"
        case .map:      return "Map"
        case .viewings: return "Viewings"
        case .profile:  return "Profile"
        }
    }
}

/// Screens pushed on top of a tab (detail, chat thread) hide the floating bar
/// by bumping this counter while they are on screen.
@Observable
final class ChromeState {
    var selectedTab: SoftTab = .home
    var hiddenDepth = 0
    var isTabBarHidden: Bool { hiddenDepth > 0 }
}

private struct HidesSoftTabBar: ViewModifier {
    @Environment(ChromeState.self) private var chrome

    func body(content: Content) -> some View {
        content
            .onAppear { chrome.hiddenDepth += 1 }
            .onDisappear { chrome.hiddenDepth = max(0, chrome.hiddenDepth - 1) }
    }
}

extension View {
    func hidesSoftTabBar() -> some View { modifier(HidesSoftTabBar()) }
}

/// The floating white pill with five circular tabs (.tab).
struct SoftTabBar: View {
    @Binding var selected: SoftTab
    var badges: [SoftTab: Int] = [:]

    var body: some View {
        HStack {
            ForEach(SoftTab.allCases, id: \.self) { tab in
                let on = tab == selected
                Button {
                    selected = tab
                } label: {
                    ZStack {
                        Circle().fill(on ? Color.softInk : Color.clear)
                        Image(systemName: tab.systemImage)
                            .font(.system(size: 21, weight: .light))
                            .foregroundStyle(on ? .white : Color.softTextSoft)
                        if let n = badges[tab], n > 0 {
                            Circle().fill(Color.softAccent)
                                .frame(width: 8, height: 8)
                                .offset(x: 14, y: -14)
                        }
                    }
                    .frame(width: 54, height: 54)
                    .contentShape(Circle())
                }
                .buttonStyle(SoftPressStyle())
                .accessibilityLabel(tab.label)
                if tab != .profile { Spacer(minLength: 0) }
            }
        }
        .padding(.horizontal, 18)
        .frame(width: 334, height: 68)
        .background(Color.white, in: Capsule())
        .softShadow(.tab)
    }
}

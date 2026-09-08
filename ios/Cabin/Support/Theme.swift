import SwiftUI

// MARK: - Cabin Soft design tokens
//
// Colours, type, radii and shadows from the "Cabin Soft" hand-off: soft rounded
// surfaces on a pale green-to-cream ground, near-black ink for primary actions,
// and Outfit at light/regular weights. Every screen reads from here so the look
// can be tuned in one place.

extension Color {
    /// Near-black used for primary buttons, the app mark and the verification tag (#111514).
    static let softInk = Color(red: 17 / 255, green: 21 / 255, blue: 20 / 255)
    /// Body text (#171c1a).
    static let softText = Color(red: 23 / 255, green: 28 / 255, blue: 26 / 255)
    /// Icon and subdued heading colour (#2a302d).
    static let softTextSoft = Color(red: 42 / 255, green: 48 / 255, blue: 45 / 255)
    /// Labels inside tiles and the unselected segment text (#3d453f).
    static let softLabel = Color(red: 61 / 255, green: 69 / 255, blue: 63 / 255)
    /// Secondary copy (#5d665f).
    static let softSecondary = Color(red: 93 / 255, green: 102 / 255, blue: 95 / 255)
    /// Placeholder and map label grey (#8d968f).
    static let softMuted = Color(red: 141 / 255, green: 150 / 255, blue: 143 / 255)

    /// Tinted tile fill (#e3eee7).
    static let softTile = Color(red: 227 / 255, green: 238 / 255, blue: 231 / 255)
    /// Very pale row fill (#f4f6f3).
    static let softPale = Color(red: 244 / 255, green: 246 / 255, blue: 243 / 255)
    /// Hover / pressed tint on white (#eef3ef).
    static let softHover = Color(red: 238 / 255, green: 243 / 255, blue: 239 / 255)
    /// Avatar fill (#cfe0d6).
    static let softAvatar = Color(red: 207 / 255, green: 224 / 255, blue: 214 / 255)
    /// Map pin fill (#b9c9c1).
    static let softPin = Color(red: 185 / 255, green: 201 / 255, blue: 193 / 255)

    /// Background gradient stops (#dcebdf → #e9f0e9 → #f4efe3).
    static let softBackgroundTop = Color(red: 220 / 255, green: 235 / 255, blue: 223 / 255)
    static let softBackgroundMid = Color(red: 233 / 255, green: 240 / 255, blue: 233 / 255)
    static let softBackgroundBottom = Color(red: 244 / 255, green: 239 / 255, blue: 227 / 255)

    /// Attention accent: unread dots and "N new" tags (#f0a73a).
    static let softAccent = Color(red: 240 / 255, green: 167 / 255, blue: 58 / 255)
    /// Warning copy, e.g. "add more photos" (#b8652a).
    static let softClay = Color(red: 184 / 255, green: 101 / 255, blue: 42 / 255)
    /// Online / positive dot (#4fc36a).
    static let softGreen = Color(red: 79 / 255, green: 195 / 255, blue: 106 / 255)
    /// Favourite heart (#e0533f).
    static let softRed = Color(red: 224 / 255, green: 83 / 255, blue: 63 / 255)

    /// Hairline divider (rgba(23,28,26,.15)).
    static let softDivider = softText.opacity(0.15)
    /// Outlined tag border (rgba(23,28,26,.25)).
    static let softOutline = softText.opacity(0.25)

    // Legacy names still referenced by a few secondary screens.
    static let cabinForest = softInk
    static let cabinForestDark = softInk
    static let cabinClay = softClay
    static let cabinSand = softBackgroundBottom
}

// MARK: - Type

extension Font {
    enum SoftWeight {
        case light, regular, medium

        var postScriptName: String {
            switch self {
            case .light:   return "Outfit-Light"
            case .regular: return "Outfit-Regular"
            case .medium:  return "Outfit-Medium"
            }
        }
    }

    /// Global multiplier on every design size. The hand-off's sizes felt large
    /// on a real phone, so the whole scale is tightened here in one place.
    static let softScale: CGFloat = 0.88

    /// Outfit at a fixed point size (times `softScale`). Light is the default
    /// because the design sets almost all display and body copy at weight 300.
    static func soft(_ size: CGFloat, _ weight: SoftWeight = .light) -> Font {
        .custom(weight.postScriptName, fixedSize: (size * softScale).rounded())
    }

    /// Named sizes that recur across the design.
    static let softDisplay = soft(44)        // welcome headline
    static let softTitle = soft(36)          // screen headline ("Create your account")
    static let softHeading = soft(28)        // listing title on the hero card
    static let softScreenTitle = soft(24)    // centred header title
    static let softCardTitle = soft(20)      // secondary card title
    static let softBody = soft(17, .regular) // row title
    static let softBodyLight = soft(16)      // bubbles, copy
    static let softSmall = soft(15)          // .sm
    static let softCaption = soft(14)        // row meta
    static let softFootnote = soft(13)       // hints
    static let softTag = soft(12, .regular)  // tag text
}

// MARK: - Radii and shadows

enum SoftRadius {
    static let phone: CGFloat = 46
    static let card: CGFloat = 28
    static let row: CGFloat = 22
    static let bubble: CGFloat = 22
    static let tile: CGFloat = 20
    static let image: CGFloat = 20
    static let codeBox: CGFloat = 18
    static let mark: CGFloat = 14
    static let photo: CGFloat = 12
}

struct SoftShadow: ViewModifier {
    enum Kind { case card, row, tab, pill, circle }
    let kind: Kind

    func body(content: Content) -> some View {
        switch kind {
        case .card:
            content.shadow(color: Color(red: 40 / 255, green: 60 / 255, blue: 55 / 255).opacity(0.08), radius: 15, y: 10)
        case .row:
            content.shadow(color: Color(red: 40 / 255, green: 60 / 255, blue: 55 / 255).opacity(0.06), radius: 9, y: 6)
        case .tab:
            content.shadow(color: Color(red: 30 / 255, green: 50 / 255, blue: 45 / 255).opacity(0.16), radius: 16, y: 12)
        case .pill:
            content.shadow(color: .black.opacity(0.2), radius: 12, y: 10)
        case .circle:
            content.shadow(color: .black.opacity(0.05), radius: 4, y: 2)
        }
    }
}

extension View {
    func softShadow(_ kind: SoftShadow.Kind) -> some View {
        modifier(SoftShadow(kind: kind))
    }
}

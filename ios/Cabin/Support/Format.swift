import Foundation

enum Format {
    private static let currency: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.maximumFractionDigits = 0
        f.currencyCode = "PHP"
        f.locale = Locale(identifier: "en_PH")
        return f
    }()

    /// "₱8,500,000" for sale, "₱22,000/mo" for rent.
    static func price(_ price: Int, listingType: String) -> String {
        let base = currency.string(from: NSNumber(value: price)) ?? "₱\(price)"
        return listingType.lowercased() == "rent" ? "\(base)/mo" : base
    }

    /// A compact form for dense views: "₱8.5M", "₱22K".
    static func compactPrice(_ price: Int) -> String {
        switch price {
        case 1_000_000...:
            return "₱\((Double(price) / 1_000_000).formatted(.number.precision(.fractionLength(0...1))))M"
        case 1_000...:
            return "₱\((Double(price) / 1_000).formatted(.number.precision(.fractionLength(0...1))))K"
        default:
            return "₱\(price)"
        }
    }

    static func beds(_ bedrooms: Int) -> String {
        bedrooms <= 0 ? "Studio" : "\(bedrooms) bd"
    }

    static func baths(_ bathrooms: Double) -> String {
        let n = bathrooms.truncatingRemainder(dividingBy: 1) == 0
            ? String(Int(bathrooms))
            : String(bathrooms)
        return "\(n) ba"
    }

    static func area(_ sqft: Int) -> String {
        sqft <= 0 ? "—" : "\(sqft.formatted()) sqft"
    }

    static func capitalized(_ text: String) -> String {
        guard let first = text.first else { return text }
        return first.uppercased() + text.dropFirst()
    }

    /// Turns a verification flag code ("thin_description") into readable text.
    static func flagLabel(_ code: String) -> String {
        capitalized(code.replacingOccurrences(of: "_", with: " "))
    }

    // MARK: - Dates

    private static let isoFractional: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private static let iso: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    /// Parses the RFC3339 timestamps the API returns, with or without fractional seconds.
    static func date(from raw: String) -> Date? {
        guard !raw.isEmpty else { return nil }
        return isoFractional.date(from: raw) ?? iso.date(from: raw)
    }

    static func timestamp(_ date: Date) -> String {
        iso.string(from: date)
    }

    /// "3 Sep at 2:00 PM"
    static func dateTime(_ raw: String) -> String {
        guard let date = date(from: raw) else { return "—" }
        // The narrow no-break space the formatter puts before AM/PM has no glyph in Outfit.
        return date.formatted(.dateTime.day().month(.abbreviated).hour().minute())
            .replacingOccurrences(of: "\u{202F}", with: " ")
    }

    /// "2h ago", "3d ago" — for chat lists and listing freshness.
    static func relative(_ raw: String) -> String {
        guard let date = date(from: raw) else { return "" }
        let f = RelativeDateTimeFormatter()
        f.unitsStyle = .abbreviated
        return f.localizedString(for: date, relativeTo: Date())
    }

    /// Resolves a relative ("/uploads/x.jpg") or absolute image URL.
    static func imageURL(_ raw: String) -> URL? {
        if raw.hasPrefix("http://") || raw.hasPrefix("https://") {
            return URL(string: raw)
        }
        return URL(string: AppConfig.baseURL.absoluteString + raw)
    }
}

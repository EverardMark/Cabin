import Foundation

enum Format {
    private static let currency: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.maximumFractionDigits = 0
        f.currencyCode = "USD"
        f.locale = Locale(identifier: "en_US")
        return f
    }()

    /// "$525,000" for sale, "$2,800/mo" for rent.
    static func price(_ price: Int, listingType: String) -> String {
        let base = currency.string(from: NSNumber(value: price)) ?? "$\(price)"
        return listingType.lowercased() == "rent" ? "\(base)/mo" : base
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

    private static let iso: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    /// "Updated 3 days ago" style relative time from an RFC3339 string.
    static func relativeTime(_ rfc3339: String) -> String? {
        guard let date = iso.date(from: rfc3339) else { return nil }
        let rel = RelativeDateTimeFormatter()
        rel.unitsStyle = .full
        return rel.localizedString(for: date, relativeTo: Date())
    }

    /// Strips common separators, leaving a leading "+" and digits (mirrors the server).
    static func normalizePhone(_ raw: String) -> String {
        let separators = CharacterSet(charactersIn: " -()./\t")
        return raw.trimmingCharacters(in: .whitespaces)
            .components(separatedBy: separators)
            .joined()
    }

    /// True when the input is a valid E.164 mobile number (+ then 8–15 digits).
    static func isValidPhone(_ raw: String) -> Bool {
        let phone = normalizePhone(raw)
        return phone.range(of: #"^\+[1-9]\d{7,14}$"#, options: .regularExpression) != nil
    }

    static func statusLabel(_ status: String) -> String {
        switch status.lowercased() {
        case "sold": return "Sold"
        case "rented": return "Rented"
        case "pending": return "Pending"
        case "inactive": return "Inactive"
        default: return "Active"
        }
    }

    /// Resolves a relative ("/uploads/x.jpg") or absolute image URL.
    static func imageURL(_ raw: String) -> URL? {
        if raw.hasPrefix("http://") || raw.hasPrefix("https://") {
            return URL(string: raw)
        }
        return URL(string: AppConfig.baseURL.absoluteString + raw)
    }
}

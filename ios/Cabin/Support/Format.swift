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

    /// Resolves a relative ("/uploads/x.jpg") or absolute image URL.
    static func imageURL(_ raw: String) -> URL? {
        if raw.hasPrefix("http://") || raw.hasPrefix("https://") {
            return URL(string: raw)
        }
        return URL(string: AppConfig.baseURL.absoluteString + raw)
    }
}

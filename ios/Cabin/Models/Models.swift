import Foundation

// MARK: - Verification

/// Outcome of the automated review that decides whether a listing or account
/// earns a trust badge. 79% of surveyed users asked to see verified listings
/// only, and 86% called trust "extremely important".
enum VerificationStatus: String, Codable, Hashable {
    case unverified
    case pending
    case verified
    case flagged
    case rejected

    /// Unknown values decode as `.pending` rather than failing the whole payload.
    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = VerificationStatus(rawValue: raw) ?? .pending
    }

    var label: String {
        switch self {
        case .unverified: return "Not verified"
        case .pending:    return "Checking…"
        case .verified:   return "Verified"
        case .flagged:    return "Check details"
        case .rejected:   return "Failed review"
        }
    }

    var symbol: String {
        switch self {
        case .unverified: return "questionmark.circle"
        case .pending:    return "clock"
        case .verified:   return "checkmark.seal.fill"
        case .flagged:    return "exclamationmark.triangle.fill"
        case .rejected:   return "xmark.octagon.fill"
        }
    }
}

// MARK: - Users

struct User: Codable, Identifiable, Hashable {
    let id: String
    let email: String
    let name: String
    var role: String = "user"
    var phone: String = ""
    var bio: String = ""
    var licenseNo: String = ""
    var emailVerified: Bool = false
    var phoneVerified: Bool = false
    var verificationStatus: VerificationStatus = .unverified
    var verificationScore: Int = 0
    var verificationNotes: String = ""
    var ratingAvg: Double = 0
    var ratingCount: Int = 0
    var createdAt: String = ""

    var isAgent: Bool { role == "agent" }
    var isAdmin: Bool { role == "admin" }
    var isVerified: Bool { verificationStatus == .verified }
}

struct UserSummary: Codable, Hashable {
    let id: String
    let name: String
    var email: String = ""
    var role: String = "user"
    var verificationStatus: VerificationStatus = .unverified
    var ratingAvg: Double = 0
    var ratingCount: Int = 0

    var isAgent: Bool { role == "agent" }
    var isVerified: Bool { verificationStatus == .verified }
}

/// Public profile of someone other than the signed-in user.
struct PublicProfile: Codable, Hashable {
    let id: String
    let name: String
    var role: String = "user"
    var bio: String = ""
    var verificationStatus: VerificationStatus = .unverified
    var ratingAvg: Double = 0
    var ratingCount: Int = 0
    var createdAt: String = ""

    var isAgent: Bool { role == "agent" }
}

// MARK: - Listings

struct ListingImage: Codable, Identifiable, Hashable {
    let id: String
    var listingId: String = ""
    let url: String
    var position: Int = 0
    var createdAt: String = ""
}

struct Listing: Codable, Identifiable, Hashable {
    let id: String
    var userId: String = ""
    let title: String
    var description: String = ""
    var price: Int = 0
    var currency: String = "PHP"
    var propertyType: String = "house"
    var listingType: String = "sale"
    var bedrooms: Int = 0
    var bathrooms: Double = 0
    var areaSqft: Int = 0
    var address: String = ""
    var city: String = ""
    var state: String = ""
    var zipCode: String = ""
    var latitude: Double?
    var longitude: Double?
    var status: String = "active"

    var verificationStatus: VerificationStatus = .pending
    var verificationScore: Int = 0
    var verificationSummary: String = ""
    var verificationFlags: [String] = []
    var verifiedAt: String?
    var lastConfirmedAt: String?
    /// When paid promotion expires. Promotion only counts while also verified.
    var featuredUntil: String?
    var reportCount: Int = 0
    var viewCount: Int = 0

    var images: [ListingImage] = []
    var owner: UserSummary?
    var createdAt: String = ""
    var updatedAt: String = ""

    /// True when this listing gets promoted placement.
    ///
    /// Deliberately gated on verification: paying buys reach, never credibility.
    /// A listing that hasn't passed screening is never promoted, whatever its
    /// owner paid.
    var isFeatured: Bool {
        guard verificationStatus == .verified,
              let until = Format.date(from: featuredUntil ?? "") else { return false }
        return until > Date()
    }

    /// True when the owner has not confirmed availability in the last 30 days.
    /// "Outdated listings" was one of the most common complaints in the survey.
    var isStale: Bool {
        let reference = Format.date(from: lastConfirmedAt ?? "") ?? Format.date(from: createdAt)
        guard let reference else { return false }
        return Date().timeIntervalSince(reference) > 30 * 24 * 60 * 60
    }
}

// MARK: - Messaging

struct Message: Codable, Identifiable, Hashable {
    let id: String
    var conversationId: String = ""
    var senderId: String = ""
    var body: String = ""
    var readAt: String?
    var createdAt: String = ""
}

struct Conversation: Codable, Identifiable, Hashable {
    let id: String
    var listingId: String = ""
    var inquirerId: String = ""
    var ownerId: String = ""
    var listing: Listing?
    var counterparty: UserSummary?
    var lastMessage: Message?
    var unreadCount: Int = 0
    var lastMessageAt: String?
    var createdAt: String = ""
}

// MARK: - Viewings

struct ViewingRequest: Codable, Identifiable, Hashable {
    let id: String
    var listingId: String = ""
    var requesterId: String = ""
    var ownerId: String = ""
    var scheduledFor: String = ""
    var status: String = "requested"
    var note: String = ""
    var responseNote: String = ""
    var listing: Listing?
    var requester: UserSummary?
    var owner: UserSummary?
    var createdAt: String = ""
    var updatedAt: String = ""

    var scheduledDate: Date? { Format.date(from: scheduledFor) }
}

// MARK: - Reviews

struct Review: Codable, Identifiable, Hashable {
    let id: String
    var subjectUserId: String = ""
    var authorId: String = ""
    var listingId: String = ""
    var rating: Int = 0
    var comment: String = ""
    var author: UserSummary?
    var createdAt: String = ""
}

// MARK: - Saved searches

struct SavedSearch: Codable, Identifiable, Hashable {
    let id: String
    var userId: String = ""
    var name: String = ""
    var query: String = ""
    var alertsEnabled: Bool = true
    var newMatches: Int = 0
    var lastAlertedAt: String = ""
    var createdAt: String = ""
}

// MARK: - Featured listings

/// A paid promotion package for a single listing.
struct FeaturePlan: Codable, Identifiable, Hashable {
    let id: String
    let label: String
    var days: Int = 0
    var price: Int = 0
}

struct FeaturePlansResponse: Decodable {
    let plans: [FeaturePlan]
    var currency: String = "PHP"
    var note: String = ""
}

struct FeatureResponse: Decodable {
    let listing: Listing
    var plan: FeaturePlan?
    /// False while no payment provider is wired up, so the UI can say so.
    var paid: Bool = false
}

// MARK: - Price comparison

struct PriceComparison: Codable, Hashable {
    var listingId: String = ""
    var price: Int = 0
    var sampleSize: Int = 0
    var median: Int = 0
    var min: Int = 0
    var max: Int = 0
    var pricePerSqft: Double = 0
    var medianPerSqft: Double = 0
    var verdict: String = "insufficient_data"
    var percentDiff: Double = 0
    var comparables: [Listing] = []

    var verdictLabel: String {
        switch verdict {
        case "below_market": return "Below market"
        case "above_market": return "Above market"
        case "at_market":    return "At market"
        default:             return "Not enough comparable listings yet"
        }
    }
}

// MARK: - Browse filters

/// Everything the browse screen can filter on, rendered into a query string.
struct ListingFilters: Equatable {
    var query: String = ""
    var listingType: String? = nil
    var propertyType: String? = nil
    var city: String = ""
    var minPrice: Int? = nil
    var maxPrice: Int? = nil
    var minBedrooms: Int? = nil
    /// Defaults on: the single most requested feature in the survey.
    var verifiedOnly: Bool = true
    var excludeStale: Bool = true
    var sort: String = "recent"

    var queryItems: [URLQueryItem] {
        var items: [URLQueryItem] = []
        if !query.isEmpty { items.append(.init(name: "q", value: query)) }
        if let listingType { items.append(.init(name: "listing_type", value: listingType)) }
        if let propertyType { items.append(.init(name: "property_type", value: propertyType)) }
        if !city.isEmpty { items.append(.init(name: "city", value: city)) }
        if let minPrice { items.append(.init(name: "min_price", value: String(minPrice))) }
        if let maxPrice { items.append(.init(name: "max_price", value: String(maxPrice))) }
        if let minBedrooms { items.append(.init(name: "min_bedrooms", value: String(minBedrooms))) }
        if verifiedOnly { items.append(.init(name: "verified_only", value: "true")) }
        if excludeStale { items.append(.init(name: "exclude_stale", value: "true")) }
        items.append(.init(name: "sort", value: sort))
        return items
    }

    /// The same filters as a query string, for saving as a search.
    var queryString: String {
        var comps = URLComponents()
        comps.queryItems = queryItems
        return comps.percentEncodedQuery ?? ""
    }
}

// MARK: - Requests

struct RegisterRequest: Encodable {
    let email: String
    let password: String
    let name: String
    let phone: String
    let role: String
}

struct LoginRequest: Encodable {
    let email: String
    let password: String
}

struct ListingRequest: Encodable {
    let title: String
    let description: String
    let price: Int
    let propertyType: String
    let listingType: String
    let bedrooms: Int
    let bathrooms: Double
    let areaSqft: Int
    let address: String
    let city: String
    let state: String
    let zipCode: String
    /// A map pin is what makes a listing findable in map search.
    var latitude: Double?
    var longitude: Double?
}

struct ReorderImagesRequest: Encodable {
    let imageIds: [String]
}

struct ProfileRequest: Encodable {
    var name: String?
    var phone: String?
    var bio: String?
    var licenseNo: String?
    var role: String?
}

struct ReportRequest: Encodable {
    let reason: String
    let details: String
}

struct ViewingRequestBody: Encodable {
    let scheduledFor: String
    let note: String
}

struct ViewingUpdateBody: Encodable {
    var status: String?
    var responseNote: String?
    var scheduledFor: String?
}

struct ReviewRequest: Encodable {
    let rating: Int
    let comment: String
    let listingId: String
}

struct SavedSearchRequest: Encodable {
    let name: String
    let query: String
}

// MARK: - Responses

struct AuthResponse: Decodable {
    let token: String
    let user: User
}

struct MeResponse: Decodable {
    let user: User
}

struct ProfileResponse: Decodable {
    let user: PublicProfile
}

struct VerificationResponse: Decodable {
    let user: User
    var verification: VerificationVerdict?
}

struct VerificationVerdict: Decodable, Hashable {
    var status: String = ""
    var score: Int = 0
    var summary: String = ""
    var flags: [String] = []
}

struct ListingsResponse: Decodable {
    let listings: [Listing]
    var total: Int = 0
    var page: Int = 1
    var pageSize: Int = 20
}

struct ConversationsResponse: Decodable {
    let conversations: [Conversation]
}

struct MessagesResponse: Decodable {
    var conversation: Conversation?
    let messages: [Message]
}

struct ViewingsResponse: Decodable {
    let viewings: [ViewingRequest]
}

struct ReviewsResponse: Decodable {
    let reviews: [Review]
}

struct SavedSearchesResponse: Decodable {
    let searches: [SavedSearch]
}

/// Badge counts for the tab bar and home screen.
struct HomeSummary: Decodable, Hashable {
    var unreadMessages: Int = 0
    var upcomingViewings: Int = 0
    var pendingViewingRequests: Int = 0
    var listingsNeedingAttention: Int = 0
}

struct ServerError: Decodable {
    let error: String
}

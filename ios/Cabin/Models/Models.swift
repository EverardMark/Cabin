import Foundation

struct User: Codable, Identifiable, Hashable {
    let id: String
    let email: String
    let name: String
    var role: String = "user"
    var createdAt: String = ""

    var isAgent: Bool { role == "agent" }
}

struct UserSummary: Codable, Hashable {
    let id: String
    let name: String
    var email: String = ""
    var role: String = "user"

    var isAgent: Bool { role == "agent" }
}

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
    var currency: String = "USD"
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
    var images: [ListingImage] = []
    var owner: UserSummary?
    var createdAt: String = ""
    var updatedAt: String = ""
}

// MARK: - Requests

struct RegisterRequest: Encodable {
    let email: String
    let password: String
    let name: String
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
}

// MARK: - Responses

struct AuthResponse: Decodable {
    let token: String
    let user: User
}

struct MeResponse: Decodable {
    let user: User
}

struct ListingsResponse: Decodable {
    let listings: [Listing]
    var total: Int = 0
    var page: Int = 1
    var pageSize: Int = 20
}

struct ServerError: Decodable {
    let error: String
}

import Foundation

enum APIError: LocalizedError {
    case server(String)
    case network
    case decoding

    var errorDescription: String? {
        switch self {
        case .server(let message): return message
        case .network: return "Can't reach the server. Make sure the API is running."
        case .decoding: return "Unexpected server response."
        }
    }
}

/// Type-erased Encodable so we can encode heterogeneous request bodies.
private struct AnyEncodable: Encodable {
    private let encodeClosure: (Encoder) throws -> Void
    init(_ wrapped: Encodable) { encodeClosure = wrapped.encode }
    func encode(to encoder: Encoder) throws { try encodeClosure(encoder) }
}

private extension Data {
    mutating func appendString(_ string: String) {
        if let data = string.data(using: .utf8) { append(data) }
    }
}

final class APIClient {
    private let baseURL: URL
    private let session: SessionStore
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    init(baseURL: URL, session: SessionStore) {
        self.baseURL = baseURL
        self.session = session

        decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase

        encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
    }

    // MARK: Auth

    func register(_ body: RegisterRequest) async throws -> AuthResponse {
        try await send("api/v1/auth/register", method: "POST", body: body)
    }

    func googleSignIn(idToken: String) async throws -> AuthResponse {
        struct Body: Encodable { let idToken: String } // encoded as "id_token"
        return try await send("api/v1/auth/google", method: "POST", body: Body(idToken: idToken))
    }

    func login(_ body: LoginRequest) async throws -> AuthResponse {
        try await send("api/v1/auth/login", method: "POST", body: body)
    }

    func me() async throws -> MeResponse {
        try await send("api/v1/auth/me", authorized: true)
    }

    func summary() async throws -> HomeSummary {
        try await send("api/v1/me/summary", authorized: true)
    }

    // MARK: Profile & verification

    func updateProfile(_ body: ProfileRequest) async throws -> MeResponse {
        try await send("api/v1/me", method: "PATCH", body: body, authorized: true)
    }

    /// Submits the signed-in account for the automated identity review.
    func requestVerification() async throws -> VerificationResponse {
        try await send("api/v1/me/verification", method: "POST", authorized: true)
    }

    /// Texts a one-time code to confirm the user's mobile number.
    func sendPhoneCode(phone: String) async throws -> PhoneCodeResponse {
        try await send("api/v1/me/phone/send-code", method: "POST",
                       body: SendPhoneCodeRequest(phone: phone), authorized: true)
    }

    func verifyPhoneCode(_ code: String) async throws -> MeResponse {
        try await send("api/v1/me/phone/verify", method: "POST",
                       body: VerifyPhoneCodeRequest(code: code), authorized: true)
    }

    func profile(userId: String) async throws -> ProfileResponse {
        try await send("api/v1/users/\(userId)")
    }

    // MARK: Listings

    func listings(filters: ListingFilters, pageSize: Int = 50) async throws -> ListingsResponse {
        var items = filters.queryItems
        items.append(URLQueryItem(name: "page_size", value: String(pageSize)))
        return try await send("api/v1/listings", query: items)
    }

    /// Listings inside a map viewport.
    func listings(inBoundingBox box: (minLat: Double, maxLat: Double, minLng: Double, maxLng: Double),
                  filters: ListingFilters) async throws -> ListingsResponse {
        var items = filters.queryItems
        items.append(contentsOf: [
            URLQueryItem(name: "min_lat", value: String(box.minLat)),
            URLQueryItem(name: "max_lat", value: String(box.maxLat)),
            URLQueryItem(name: "min_lng", value: String(box.minLng)),
            URLQueryItem(name: "max_lng", value: String(box.maxLng)),
            URLQueryItem(name: "page_size", value: "100"),
        ])
        return try await send("api/v1/listings", query: items)
    }

    func listing(id: String) async throws -> Listing {
        try await send("api/v1/listings/\(id)")
    }

    func createListing(_ body: ListingRequest) async throws -> Listing {
        try await send("api/v1/listings", method: "POST", body: body, authorized: true)
    }

    func updateListing(id: String, _ body: ListingRequest) async throws -> Listing {
        try await send("api/v1/listings/\(id)", method: "PUT", body: body, authorized: true)
    }

    func deleteListing(id: String) async throws {
        try await performVoid(makeRequest("api/v1/listings/\(id)", method: "DELETE", authorized: true))
    }

    /// Removes one photo. The server re-screens the listing afterwards, since
    /// photo count feeds the review.
    func deleteImage(listingId: String, imageId: String) async throws -> Listing {
        try await send("api/v1/listings/\(listingId)/images/\(imageId)", method: "DELETE", authorized: true)
    }

    /// Sets photo order — the first photo is the card thumbnail.
    func reorderImages(listingId: String, imageIds: [String]) async throws -> Listing {
        try await send("api/v1/listings/\(listingId)/images/order", method: "PUT",
                       body: ReorderImagesRequest(imageIds: imageIds), authorized: true)
    }

    func myListings() async throws -> ListingsResponse {
        try await send("api/v1/me/listings", authorized: true)
    }

    /// Confirms a listing is still available, clearing the stale warning.
    func confirmListing(id: String) async throws -> Listing {
        try await send("api/v1/listings/\(id)/confirm", method: "POST", authorized: true)
    }

    func reportListing(id: String, reason: String, details: String) async throws {
        let _: EmptyResponse = try await send(
            "api/v1/listings/\(id)/report", method: "POST",
            body: ReportRequest(reason: reason, details: details), authorized: true)
    }

    // MARK: Featured listings

    func featurePlans() async throws -> FeaturePlansResponse {
        try await send("api/v1/feature-plans")
    }

    /// Buys promoted placement for a listing the caller owns.
    func featureListing(id: String, planId: String) async throws -> FeatureResponse {
        struct Body: Encodable { let planId: String } // encoded as "plan_id"
        return try await send("api/v1/listings/\(id)/feature", method: "POST",
                              body: Body(planId: planId), authorized: true)
    }

    func priceComparison(listingId: String) async throws -> PriceComparison {
        try await send("api/v1/listings/\(listingId)/price-comparison")
    }

    @discardableResult
    func uploadImage(listingId: String, data: Data, filename: String, mime: String) async throws -> ListingImage {
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = try makeRequest("api/v1/listings/\(listingId)/images", method: "POST", authorized: true)
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()
        body.appendString("--\(boundary)\r\n")
        body.appendString("Content-Disposition: form-data; name=\"image\"; filename=\"\(filename)\"\r\n")
        body.appendString("Content-Type: \(mime)\r\n\r\n")
        body.append(data)
        body.appendString("\r\n--\(boundary)--\r\n")
        request.httpBody = body

        return try await perform(request)
    }

    // MARK: Messaging

    func startConversation(listingId: String) async throws -> Conversation {
        try await send("api/v1/listings/\(listingId)/conversations", method: "POST", authorized: true)
    }

    func conversations() async throws -> ConversationsResponse {
        try await send("api/v1/conversations", authorized: true)
    }

    func messages(conversationId: String) async throws -> MessagesResponse {
        try await send("api/v1/conversations/\(conversationId)/messages", authorized: true)
    }

    @discardableResult
    func sendMessage(conversationId: String, body: String) async throws -> Message {
        struct Body: Encodable { let body: String }
        return try await send("api/v1/conversations/\(conversationId)/messages",
                              method: "POST", body: Body(body: body), authorized: true)
    }

    // MARK: Viewings

    func requestViewing(listingId: String, at date: Date, note: String) async throws -> ViewingRequest {
        try await send("api/v1/listings/\(listingId)/viewings", method: "POST",
                       body: ViewingRequestBody(scheduledFor: Format.timestamp(date), note: note),
                       authorized: true)
    }

    func viewings() async throws -> ViewingsResponse {
        try await send("api/v1/viewings", authorized: true)
    }

    @discardableResult
    func updateViewing(id: String, body: ViewingUpdateBody) async throws -> ViewingRequest {
        try await send("api/v1/viewings/\(id)", method: "PATCH", body: body, authorized: true)
    }

    // MARK: Reviews

    func reviews(userId: String) async throws -> ReviewsResponse {
        try await send("api/v1/users/\(userId)/reviews")
    }

    @discardableResult
    func createReview(userId: String, rating: Int, comment: String, listingId: String) async throws -> Review {
        try await send("api/v1/users/\(userId)/reviews", method: "POST",
                       body: ReviewRequest(rating: rating, comment: comment, listingId: listingId),
                       authorized: true)
    }

    // MARK: Saved searches

    func savedSearches() async throws -> SavedSearchesResponse {
        try await send("api/v1/me/searches", authorized: true)
    }

    @discardableResult
    func saveSearch(name: String, query: String) async throws -> SavedSearch {
        try await send("api/v1/me/searches", method: "POST",
                       body: SavedSearchRequest(name: name, query: query), authorized: true)
    }

    func deleteSavedSearch(id: String) async throws {
        let request = try makeRequest("api/v1/me/searches/\(id)", method: "DELETE", authorized: true)
        try await performVoid(request)
    }

    func runSavedSearch(id: String) async throws -> ListingsResponse {
        try await send("api/v1/me/searches/\(id)/results", authorized: true)
    }

    // MARK: Core

    private func send<T: Decodable>(
        _ path: String,
        method: String = "GET",
        query: [URLQueryItem]? = nil,
        body: Encodable? = nil,
        authorized: Bool = false
    ) async throws -> T {
        var request = try makeRequest(path, method: method, query: query, authorized: authorized)
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try encoder.encode(AnyEncodable(body))
        }
        return try await perform(request)
    }

    private func makeRequest(
        _ path: String,
        method: String = "GET",
        query: [URLQueryItem]? = nil,
        authorized: Bool = false
    ) throws -> URLRequest {
        guard var components = URLComponents(
            url: baseURL.appendingPathComponent(path),
            resolvingAgainstBaseURL: false
        ) else {
            throw APIError.network
        }
        if let query, !query.isEmpty { components.queryItems = query }
        guard let url = components.url else { throw APIError.network }

        var request = URLRequest(url: url)
        request.httpMethod = method
        if authorized, let token = session.token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return request
    }

    private func perform<T: Decodable>(_ request: URLRequest) async throws -> T {
        let (data, http) = try await run(request)
        try check(http, data)

        // 204 and other empty bodies decode as EmptyResponse.
        if data.isEmpty, let empty = EmptyResponse() as? T { return empty }
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw APIError.decoding
        }
    }

    private func performVoid(_ request: URLRequest) async throws {
        let (data, http) = try await run(request)
        try check(http, data)
    }

    private func run(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw APIError.network }
            return (data, http)
        } catch let error as APIError {
            throw error
        } catch {
            throw APIError.network
        }
    }

    private func check(_ http: HTTPURLResponse, _ data: Data) throws {
        guard (200..<300).contains(http.statusCode) else {
            if let serverError = try? decoder.decode(ServerError.self, from: data) {
                throw APIError.server(serverError.error)
            }
            throw APIError.server("Request failed (\(http.statusCode))")
        }
    }
}

/// Placeholder for endpoints that return no useful body.
struct EmptyResponse: Decodable {}

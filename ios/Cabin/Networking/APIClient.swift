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

    func login(_ body: LoginRequest) async throws -> AuthResponse {
        try await send("api/v1/auth/login", method: "POST", body: body)
    }

    func me() async throws -> MeResponse {
        try await send("api/v1/auth/me", authorized: true)
    }

    // MARK: Listings

    func listings(
        query: String? = nil,
        propertyType: String? = nil,
        listingType: String? = nil,
        sort: String? = "recent"
    ) async throws -> ListingsResponse {
        var items = [URLQueryItem(name: "page_size", value: "50")]
        if let query, !query.isEmpty { items.append(URLQueryItem(name: "q", value: query)) }
        if let propertyType { items.append(URLQueryItem(name: "property_type", value: propertyType)) }
        if let listingType { items.append(URLQueryItem(name: "listing_type", value: listingType)) }
        if let sort { items.append(URLQueryItem(name: "sort", value: sort)) }
        return try await send("api/v1/listings", query: items)
    }

    func listing(id: String) async throws -> Listing {
        try await send("api/v1/listings/\(id)")
    }

    func createListing(_ body: ListingRequest) async throws -> Listing {
        try await send("api/v1/listings", method: "POST", body: body, authorized: true)
    }

    func myListings() async throws -> ListingsResponse {
        try await send("api/v1/me/listings", authorized: true)
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
        if let query { components.queryItems = query }
        guard let url = components.url else { throw APIError.network }

        var request = URLRequest(url: url)
        request.httpMethod = method
        if authorized, let token = session.token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return request
    }

    private func perform<T: Decodable>(_ request: URLRequest) async throws -> T {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw APIError.network
        }

        guard let http = response as? HTTPURLResponse else { throw APIError.network }

        guard (200..<300).contains(http.statusCode) else {
            if let serverError = try? decoder.decode(ServerError.self, from: data) {
                throw APIError.server(serverError.error)
            }
            throw APIError.server("Request failed (\(http.statusCode))")
        }

        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw APIError.decoding
        }
    }
}

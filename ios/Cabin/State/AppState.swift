import Foundation
import Observation

@MainActor
@Observable
final class AppState {
    var currentUser: User?
    let api: APIClient
    private let session: SessionStore

    var isLoggedIn: Bool { currentUser != nil }

    init() {
        let session = SessionStore()
        self.session = session
        self.api = APIClient(baseURL: AppConfig.baseURL, session: session)
        self.currentUser = session.user
    }

    func login(email: String, password: String) async throws {
        let res = try await api.login(
            LoginRequest(email: email.trimmingCharacters(in: .whitespaces), password: password)
        )
        session.save(token: res.token, user: res.user)
        currentUser = res.user
    }

    func register(name: String, email: String, password: String) async throws {
        let res = try await api.register(
            RegisterRequest(
                email: email.trimmingCharacters(in: .whitespaces),
                password: password,
                name: name.trimmingCharacters(in: .whitespaces)
            )
        )
        session.save(token: res.token, user: res.user)
        currentUser = res.user
    }

    func logout() {
        session.clear()
        currentUser = nil
    }
}

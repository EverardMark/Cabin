import Foundation
import Observation

@MainActor
@Observable
final class AppState {
    var currentUser: User?
    /// Badge counts for the tab bar, refreshed on appear and after actions.
    var summary = HomeSummary()
    /// True between registering and finishing the sign-up flow (confirm number,
    /// then "Start browsing"). The root view keeps the onboarding screens up
    /// while this is set even though the account is already signed in.
    var onboarding = false

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
        save(res)
    }

    func register(name: String, email: String, password: String, phone: String, role: String) async throws {
        let res = try await api.register(
            RegisterRequest(
                email: email.trimmingCharacters(in: .whitespaces),
                password: password,
                name: name.trimmingCharacters(in: .whitespaces),
                phone: phone.trimmingCharacters(in: .whitespaces),
                role: role
            )
        )
        save(res)
        onboarding = true
    }

    /// Exchanges a Google ID token for a Cabin session (server verifies + links/creates the account).
    func loginWithGoogle(idToken: String) async throws {
        save(try await api.googleSignIn(idToken: idToken))
    }

    /// Exchanges a Sign in with Apple identity token for a Cabin session.
    func loginWithApple(identityToken: String, nonce: String, name: String) async throws {
        save(try await api.appleSignIn(identityToken: identityToken, nonce: nonce, name: name))
    }

    private func save(_ res: AuthResponse) {
        session.save(token: res.token, user: res.user)
        currentUser = res.user
    }

    /// Re-reads the signed-in account, e.g. after verification or a profile edit.
    func refreshUser() async {
        guard let updated = try? await api.me().user else { return }
        currentUser = updated
        session.save(token: session.token ?? "", user: updated)
    }

    func updateProfile(_ body: ProfileRequest) async throws {
        let res = try await api.updateProfile(body)
        currentUser = res.user
        session.save(token: session.token ?? "", user: res.user)
    }

    /// Submits the account for automated identity review and returns the verdict.
    func requestVerification() async throws -> VerificationVerdict? {
        let res = try await api.requestVerification()
        currentUser = res.user
        session.save(token: session.token ?? "", user: res.user)
        return res.verification
    }

    func refreshSummary() async {
        guard isLoggedIn, let s = try? await api.summary() else { return }
        summary = s
    }

    func logout() {
        session.clear()
        currentUser = nil
        summary = HomeSummary()
        onboarding = false
    }
}

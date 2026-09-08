import AuthenticationServices
import CryptoKit
import UIKit

/// Drives the native Sign in with Apple sheet and hands back what the server
/// needs: the identity token, the raw nonce whose hash is inside it, and the
/// user's name (which Apple only ever provides on the first authorization).
@MainActor
final class AppleSignIn: NSObject {
    struct Result {
        let identityToken: String
        let nonce: String
        let name: String
    }

    enum Failure: LocalizedError {
        case noIdentityToken
        var errorDescription: String? { "Apple didn't return an identity token. Please try again." }
    }

    static let shared = AppleSignIn()

    private var continuation: CheckedContinuation<Result, Error>?
    private var rawNonce = ""

    func signIn() async throws -> Result {
        rawNonce = Self.randomNonce()
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        // Apple embeds SHA-256(nonce) in the token; the server recomputes it from the raw value.
        request.nonce = SHA256.hash(data: Data(rawNonce.utf8)).map { String(format: "%02x", $0) }.joined()

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            controller.performRequests()
        }
    }

    /// True when the user dismissed the sheet, which is not worth an error message.
    static func isCancellation(_ error: Error) -> Bool {
        (error as? ASAuthorizationError)?.code == .canceled
    }

    private static func randomNonce(length: Int = 32) -> String {
        var bytes = [UInt8](repeating: 0, count: length)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return bytes.map { String(format: "%02x", $0) }.joined()
    }
}

extension AppleSignIn: ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let data = credential.identityToken,
              let token = String(data: data, encoding: .utf8) else {
            continuation?.resume(throwing: Failure.noIdentityToken)
            continuation = nil
            return
        }
        let name = [credential.fullName?.givenName, credential.fullName?.familyName]
            .compactMap { $0 }
            .joined(separator: " ")
        continuation?.resume(returning: Result(identityToken: token, nonce: rawNonce, name: name))
        continuation = nil
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        continuation?.resume(throwing: error)
        continuation = nil
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }
}

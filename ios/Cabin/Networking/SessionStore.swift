import Foundation
import Security

/// Minimal Keychain wrapper for a single string value per key.
enum Keychain {
    static func save(_ value: String, for key: String) {
        let data = Data(value.utf8)
        let base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(base as CFDictionary)
        var attributes = base
        attributes[kSecValueData as String] = data
        SecItemAdd(attributes as CFDictionary, nil)
    }

    static func read(_ key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data,
              let value = String(data: data, encoding: .utf8) else {
            return nil
        }
        return value
    }

    static func delete(_ key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

/// Persists the auth token (Keychain) and cached user (UserDefaults).
final class SessionStore {
    private let tokenKey = "com.cabin.token"
    private let userKey = "com.cabin.user"

    private(set) var token: String?
    private(set) var user: User?

    init() {
        token = Keychain.read(tokenKey)
        if let data = UserDefaults.standard.data(forKey: userKey) {
            user = try? JSONDecoder().decode(User.self, from: data)
        }
    }

    func save(token: String, user: User) {
        self.token = token
        self.user = user
        Keychain.save(token, for: tokenKey)
        if let data = try? JSONEncoder().encode(user) {
            UserDefaults.standard.set(data, forKey: userKey)
        }
    }

    /// Updates the cached user (e.g. after verification) without touching the token.
    func updateUser(_ user: User) {
        self.user = user
        if let data = try? JSONEncoder().encode(user) {
            UserDefaults.standard.set(data, forKey: userKey)
        }
    }

    func clear() {
        token = nil
        user = nil
        Keychain.delete(tokenKey)
        UserDefaults.standard.removeObject(forKey: userKey)
    }
}

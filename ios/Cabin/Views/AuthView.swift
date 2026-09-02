import SwiftUI
import UIKit
import GoogleSignIn

struct AuthView: View {
    @Environment(AppState.self) private var appState

    @State private var isRegister = false
    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var phone = ""
    @State private var isAgent = false
    @State private var errorMessage: String?
    @State private var loading = false

    private var canSubmit: Bool {
        !email.isEmpty && password.count >= 6 && (!isRegister || !name.isEmpty)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                logo
                Text("Cabin").font(.largeTitle.bold())
                Text(isRegister ? "Create your account" : "Welcome back")
                    .foregroundStyle(.secondary)
                    .padding(.bottom, 8)

                if isRegister {
                    TextField("Full name", text: $name)
                        .textContentType(.name)
                        .textFieldStyle(.roundedBorder)

                    TextField("Mobile number", text: $phone)
                        .textContentType(.telephoneNumber)
                        .keyboardType(.phonePad)
                        .textFieldStyle(.roundedBorder)

                    Picker("Account type", selection: $isAgent) {
                        Text("Private individual").tag(false)
                        Text("Real estate agent").tag(true)
                    }
                    .pickerStyle(.segmented)
                    // Anyone can post — 36% of surveyed users are owners/sellers,
                    // and nobody wanted an agents-only marketplace.
                    Text(isAgent
                         ? "You'll be asked for your PRC licence number when you verify."
                         : "Owners, buyers and renters can all post and browse listings.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                TextField("Email", text: $email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)
                SecureField("Password", text: $password)
                    .textContentType(isRegister ? .newPassword : .password)
                    .textFieldStyle(.roundedBorder)

                if let errorMessage {
                    Text(errorMessage)
                        .font(.callout)
                        .foregroundStyle(.red)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                Button(action: submit) {
                    if loading {
                        ProgressView().frame(maxWidth: .infinity)
                    } else {
                        Text(isRegister ? "Create account" : "Log in").frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(!canSubmit || loading)

                Button(action: signInWithGoogle) {
                    HStack {
                        Image(systemName: "g.circle.fill")
                        Text("Continue with Google")
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
                .disabled(loading)

                Button(isRegister ? "Already have an account? Log in" : "New here? Create an account") {
                    isRegister.toggle()
                    errorMessage = nil
                }
                .font(.callout)

                Divider().padding(.vertical, 4)

                Text("Demo account").font(.caption).foregroundStyle(.secondary)
                Button("Continue as demo@cabin.app") {
                    isRegister = false
                    email = "demo@cabin.app"
                    password = "password123"
                    submit()
                }
                .font(.callout)
            }
            .padding(24)
        }
    }

    private var logo: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 20)
                .fill(Color.cabinForest.opacity(0.15))
                .frame(width: 72, height: 72)
            Image(systemName: "house.fill")
                .font(.system(size: 34))
                .foregroundStyle(Color.cabinForest)
        }
    }

    private func submit() {
        guard !loading else { return }
        loading = true
        errorMessage = nil
        Task {
            do {
                if isRegister {
                    try await appState.register(name: name, email: email, password: password, phone: phone, role: isAgent ? "agent" : "user")
                } else {
                    try await appState.login(email: email, password: password)
                }
            } catch {
                errorMessage = error.localizedDescription
            }
            loading = false
        }
    }

    private func signInWithGoogle() {
        guard let clientID = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String,
              !clientID.isEmpty else {
            errorMessage = "Google sign-in isn't configured yet."
            return
        }
        guard let presenter = UIApplication.shared.topViewController else {
            errorMessage = "Couldn't present Google sign-in."
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        loading = true
        errorMessage = nil
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            Task { @MainActor in
                if let error {
                    errorMessage = error.localizedDescription
                    loading = false
                    return
                }
                guard let idToken = result?.user.idToken?.tokenString else {
                    errorMessage = "No Google identity token returned."
                    loading = false
                    return
                }
                do {
                    try await appState.loginWithGoogle(idToken: idToken)
                } catch {
                    errorMessage = error.localizedDescription
                }
                loading = false
            }
        }
    }
}

private extension UIApplication {
    /// The topmost presented view controller of the active window scene.
    var topViewController: UIViewController? {
        let keyWindow = connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
        var top = keyWindow?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

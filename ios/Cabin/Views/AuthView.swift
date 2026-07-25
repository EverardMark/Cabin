import SwiftUI

struct AuthView: View {
    @Environment(AppState.self) private var appState

    @State private var isRegister = false
    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
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
                    try await appState.register(name: name, email: email, password: password)
                } else {
                    try await appState.login(email: email, password: password)
                }
            } catch {
                errorMessage = error.localizedDescription
            }
            loading = false
        }
    }
}

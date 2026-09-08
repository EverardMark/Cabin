import SwiftUI
import UIKit
import GoogleSignIn

/// Sign-up and sign-in, as one flow: welcome → create account → confirm number
/// → you're in. Log-in is a sibling of "create" reached from the welcome screen.
struct AuthFlowView: View {
    enum Step { case welcome, login, create, code, done }

    @Environment(AppState.self) private var appState

    @State private var step: Step = .welcome
    @State private var name = ""
    @State private var phone = ""
    @State private var licence = ""
    @State private var email = ""
    @State private var password = ""
    @State private var isAgent = false
    @State private var code = ""
    @State private var sentTo: String?
    @State private var devCode: String?
    @State private var phoneConfirmed = false
    @State private var busy = false
    @State private var errorMessage: String?
    @State private var verificationMessage: String?

    var body: some View {
        ZStack {
            switch step {
            case .welcome: WelcomeScreen(onCreate: { go(.create) }, onLogin: { go(.login) },
                                         onDemo: loginAsDemo, onGoogle: signInWithGoogle, onApple: signInWithApple,
                                         busy: busy, errorMessage: errorMessage)
            case .login:   loginScreen
            case .create:  createScreen
            case .code:    codeScreen
            case .done:    doneScreen
            }
        }
        .background(SoftBackground())
        .foregroundStyle(Color.softText)
        .animation(.easeInOut(duration: 0.25), value: step)
        .alert("Verification", isPresented: .constant(verificationMessage != nil)) {
            Button("OK") {
                verificationMessage = nil
                appState.onboarding = false
            }
        } message: {
            Text(verificationMessage ?? "")
        }
    }

    private func go(_ next: Step) {
        errorMessage = nil
        step = next
    }

    // MARK: - Log in

    private var loginScreen: some View {
        VStack(spacing: 0) {
            stepHeader(back: { go(.welcome) }, label: "")
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("Welcome back")
                        .font(.softTitle).tracking(-0.72)
                    VStack(spacing: 10) {
                        SoftField(placeholder: "Email", text: $email, keyboard: .emailAddress,
                                  contentType: .emailAddress, capitalization: .never)
                        SoftField(placeholder: "Password", text: $password, secure: true, contentType: .password)
                    }
                    SoftError(message: errorMessage)
                    PrimaryButton(title: "Log in", large: true, loading: busy) { Task { await login() } }
                        .opacity(email.isEmpty || password.isEmpty ? 0.45 : 1)
                        .disabled(email.isEmpty || password.isEmpty)
                    HStack(spacing: 10) {
                        ProviderButton(title: "Google", loading: busy, action: signInWithGoogle) { GoogleMark() }
                        ProviderButton(title: "Apple", dark: true, action: signInWithApple) {
                            Image(systemName: "apple.logo").font(.system(size: 18, weight: .medium))
                        }
                    }
                    HStack(spacing: 4) {
                        Text("New here?").font(.soft(14)).foregroundStyle(Color.softSecondary)
                        SoftLink(title: "Create an account") { go(.create) }
                    }
                    .frame(maxWidth: .infinity)
                    SoftLink(title: "Continue as demo@cabin.app", muted: true, action: loginAsDemo)
                        .frame(maxWidth: .infinity)
                }
                .padding(.horizontal, 24)
                .padding(.top, 20)
                .padding(.bottom, 40)
            }
        }
    }

    // MARK: - Create account (step 1 of 2)

    private var createScreen: some View {
        VStack(spacing: 0) {
            stepHeader(back: { go(.welcome) }, label: "Step 1 of 2")
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("Create your account")
                        .font(.softTitle).tracking(-0.72)

                    VStack(alignment: .leading, spacing: 0) {
                        Text("I am a")
                            .font(.soft(13)).foregroundStyle(Color.softSecondary)
                            .padding(.leading, 6).padding(.bottom, 8)
                        SoftSegment(options: [(false, "Private individual"), (true, "Real estate agent")],
                                    selection: $isAgent)
                        // Anyone can post — 36% of surveyed users are owners/sellers,
                        // and nobody wanted an agents-only marketplace.
                        Text(isAgent
                             ? "Agents add a PRC licence number to be verified. \"Agent\" is an occupation label, not a trust signal."
                             : "Owners, buyers and renters can all post and browse listings.")
                            .font(.soft(13)).foregroundStyle(Color.softSecondary)
                            .padding(.horizontal, 6).padding(.top, 10)
                    }

                    VStack(spacing: 10) {
                        SoftField(placeholder: "Full name", text: $name, contentType: .name, capitalization: .words)
                        SoftField(placeholder: "Mobile number", text: $phone, keyboard: .phonePad, contentType: .telephoneNumber)
                        if isAgent {
                            SoftField(placeholder: "PRC licence number", text: $licence, capitalization: .characters)
                        }
                        SoftField(placeholder: "Email", text: $email, keyboard: .emailAddress,
                                  contentType: .emailAddress, capitalization: .never)
                        SoftField(placeholder: "Password (6+ characters)", text: $password, secure: true, contentType: .newPassword)
                    }

                    HStack(alignment: .top, spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 7, style: .continuous).fill(Color.softInk)
                            Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                        }
                        .frame(width: 22, height: 22)
                        Text("Text me a one-time code to confirm this number. Confirming is what earns the account badge.")
                            .font(.soft(13)).foregroundStyle(Color.softSecondary)
                    }
                    .padding(.horizontal, 6)

                    SoftError(message: errorMessage)

                    PrimaryButton(title: "Continue", large: true, loading: busy) { Task { await register() } }
                        .opacity(canRegister ? 1 : 0.45)
                        .disabled(!canRegister)
                        .padding(.top, 6)

                    HStack(spacing: 4) {
                        Text("Already have an account?").font(.soft(14)).foregroundStyle(Color.softSecondary)
                        SoftLink(title: "Log in") { go(.login) }
                    }
                    .frame(maxWidth: .infinity)
                }
                .padding(.horizontal, 24)
                .padding(.top, 20)
                .padding(.bottom, 40)
            }
        }
    }

    private var canRegister: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty && !email.isEmpty && password.count >= 6 && !busy
    }

    // MARK: - Confirm number (step 2 of 2)

    private var codeScreen: some View {
        VStack(spacing: 0) {
            stepHeader(back: { go(.create) }, label: "Step 2 of 2")
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("Confirm your number")
                        .font(.softTitle).tracking(-0.72)

                    (Text("We texted a 6-digit code to ")
                     + Text(sentTo ?? phone).foregroundColor(.softText)
                     + Text(". It expires in 10 minutes."))
                        .font(.soft(16)).foregroundStyle(Color.softSecondary)

                    CodeBoxes(code: code)
                    Keypad(code: $code)

                    HStack {
                        Text("Didn't get it?").font(.soft(14)).foregroundStyle(Color.softSecondary)
                        Spacer()
                        SoftLink(title: "Send a new code") { Task { await sendCode() } }
                    }
                    .padding(.horizontal, 6)

                    if let devCode {
                        Text("Development server: your code is \(devCode).")
                            .font(.soft(13)).foregroundStyle(Color.softClay)
                            .padding(.horizontal, 6)
                    }
                    SoftError(message: errorMessage)

                    PrimaryButton(title: "Confirm", large: true, loading: busy) { Task { await verifyCode() } }
                        .opacity(code.count < 6 ? 0.45 : 1)
                        .disabled(code.count < 6)

                    Text("Codes are hashed, expire in 10 minutes and allow 5 attempts.")
                        .font(.soft(13)).foregroundStyle(Color.softSecondary)
                        .frame(maxWidth: .infinity)

                    SoftLink(title: "Skip for now", muted: true) {
                        phoneConfirmed = false
                        go(.done)
                    }
                    .frame(maxWidth: .infinity)
                }
                .padding(.horizontal, 24)
                .padding(.top, 20)
                .padding(.bottom, 40)
            }
        }
    }

    // MARK: - You're in

    private var doneScreen: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                Circle().fill(Color.softInk)
                Image(systemName: "checkmark").font(.system(size: 28, weight: .medium)).foregroundStyle(.white)
            }
            .frame(width: 72, height: 72)

            Text("You're in, \(firstName).")
                .font(.soft(40)).tracking(-0.8)
                .padding(.top, 28)
            Text(phoneConfirmed
                 ? "Your number is confirmed. One more step earns the badge that shows on every listing you post."
                 : "Confirm your number from your profile whenever you're ready — that's what earns the account badge.")
                .font(.soft(16)).foregroundStyle(Color.softSecondary)
                .padding(.top, 12)

            SoftCard(padding: 12) {
                VStack(spacing: 12) {
                    SoftRow(fill: phoneConfirmed ? .softTile : .softPale, shadow: false) {
                        ZStack {
                            Circle().fill(phoneConfirmed ? Color.softInk : Color.white)
                            Image(systemName: phoneConfirmed ? "checkmark" : "phone")
                                .font(.system(size: 16, weight: .medium))
                                .foregroundStyle(phoneConfirmed ? .white : Color.softTextSoft)
                        }
                        .frame(width: 44, height: 44)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(phoneConfirmed ? "Mobile number confirmed" : "Mobile number not confirmed")
                                .font(.soft(17, .regular))
                            Text(phoneConfirmed ? "\(Self.masked(phone)) · just now" : "You can do this later from your profile")
                                .font(.soft(13)).foregroundStyle(Color.softSecondary)
                        }
                    }
                    SoftRow(fill: .softPale, shadow: false) {
                        ZStack {
                            Circle().fill(Color.white)
                            Image(systemName: "shield")
                                .font(.system(size: 18, weight: .light))
                                .foregroundStyle(Color.softTextSoft)
                        }
                        .frame(width: 44, height: 44)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Identity verification").font(.soft(17, .regular))
                            Text(isAgent ? "Needs your PRC licence number and a short review." : "A short automated review of your account.")
                                .font(.soft(13)).foregroundStyle(Color.softSecondary)
                                .lineLimit(2)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 4)
                        OTag(text: "Optional").layoutPriority(1)
                    }
                }
            }
            .padding(.top, 24)

            Spacer()

            VStack(spacing: 10) {
                PrimaryButton(title: "Start browsing", large: true) { appState.onboarding = false }
                SecondaryButton(title: "Request verification", large: true, loading: busy) {
                    Task { await requestVerification() }
                }
                .disabled(!phoneConfirmed)
                .opacity(phoneConfirmed ? 1 : 0.45)
                Text("86% of people we surveyed said verification decides whether they trust a listing.")
                    .font(.soft(13)).foregroundStyle(Color.softSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, 4)
            }
            .padding(.bottom, 12)
        }
        .padding(.horizontal, 24)
        .padding(.top, 24)
    }

    private var firstName: String {
        let n = appState.currentUser?.name ?? name
        return n.split(separator: " ").first.map(String.init) ?? "there"
    }

    /// "+63 917 555 0134" → "+63 917 ••• 0134".
    static func masked(_ phone: String) -> String {
        let digits = phone.filter(\.isNumber)
        guard digits.count > 7 else { return phone }
        let tail = digits.suffix(4)
        let head = digits.dropLast(7)
        // Philippine mobiles: country code, then the three-digit prefix.
        if head.hasPrefix("63"), head.count == 5 {
            return "+63 \(head.dropFirst(2)) ••• \(tail)"
        }
        if head.hasPrefix("0"), head.count == 4 {
            return "\(head) ••• \(tail)"
        }
        return "+\(head) ••• \(tail)"
    }

    // MARK: - Shared header

    private func stepHeader(back: @escaping () -> Void, label: String) -> some View {
        SoftHeader {
            CircleButton(systemImage: "chevron.left", action: back)
        } title: {
            Text(label).font(.soft(14)).foregroundStyle(Color.softSecondary)
        } trailing: {
            Color.clear.frame(width: 48, height: 48)
        }
    }

    // MARK: - Actions

    private func login() async {
        busy = true; errorMessage = nil
        do {
            try await appState.login(email: email, password: password)
        } catch {
            errorMessage = error.localizedDescription
        }
        busy = false
    }

    private func loginAsDemo() {
        email = "demo@cabin.app"
        password = "password123"
        Task { await login() }
    }

    private func register() async {
        busy = true; errorMessage = nil
        do {
            try await appState.register(name: name, email: email, password: password, phone: phone,
                                        role: isAgent ? "agent" : "user")
            if isAgent, !licence.isEmpty {
                try? await appState.updateProfile(ProfileRequest(name: name, phone: phone, bio: "",
                                                                 licenseNo: licence, role: "agent"))
            }
            if phone.filter(\.isNumber).count >= 10 {
                await sendCode()
                step = .code
            } else {
                phoneConfirmed = false
                step = .done
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        busy = false
    }

    private func sendCode() async {
        do {
            let res = try await appState.api.sendPhoneCode(phone: phone)
            sentTo = res.sentTo
            devCode = res.devCode
            code = ""
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func verifyCode() async {
        busy = true; errorMessage = nil
        do {
            _ = try await appState.api.verifyPhoneCode(code)
            await appState.refreshUser()
            phoneConfirmed = true
            step = .done
        } catch {
            errorMessage = error.localizedDescription
        }
        busy = false
    }

    private func requestVerification() async {
        busy = true
        do {
            let verdict = try await appState.requestVerification()
            verificationMessage = verdict?.summary ?? "We've reviewed your account."
        } catch {
            verificationMessage = error.localizedDescription
        }
        busy = false
    }

    private func signInWithApple() {
        guard !busy else { return }
        busy = true
        errorMessage = nil
        Task {
            defer { busy = false }
            do {
                let result = try await AppleSignIn.shared.signIn()
                try await appState.loginWithApple(identityToken: result.identityToken,
                                                  nonce: result.nonce, name: result.name)
            } catch where AppleSignIn.isCancellation(error) {
                // The user closed the sheet; nothing to report.
            } catch {
                errorMessage = error.localizedDescription
            }
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
        busy = true
        errorMessage = nil
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            Task { @MainActor in
                defer { busy = false }
                if let error {
                    errorMessage = error.localizedDescription
                    return
                }
                guard let idToken = result?.user.idToken?.tokenString else {
                    errorMessage = "No Google identity token returned."
                    return
                }
                do {
                    try await appState.loginWithGoogle(idToken: idToken)
                } catch {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}

// MARK: - Welcome

private struct WelcomeScreen: View {
    @Environment(AppState.self) private var appState
    var onCreate: () -> Void
    var onLogin: () -> Void
    var onDemo: () -> Void
    var onGoogle: () -> Void
    var onApple: () -> Void
    var busy: Bool
    var errorMessage: String?

    @State private var heroURL: String?
    @State private var verifiedCount: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Wordmark()

            Text("A place you can trust,\nbefore you visit.")
                .font(.soft(42)).tracking(-0.84)
                .lineLimit(3)
                .minimumScaleFactor(0.85)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 32)

            Text("Every listing is screened before it earns a badge. Browse verified homes for sale or rent across South Metro Manila, Laguna and Cavite.")
                .font(.soft(16)).foregroundStyle(Color.softSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 14)

            // The photo gives way first on shorter screens so the copy and buttons never do.
            SoftPhoto(url: heroURL, radius: 26)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 100, idealHeight: 180, maxHeight: 180)
                .overlay(alignment: .bottomLeading) {
                    VTag(text: (verifiedCount ?? 0) > 0 ? "\(verifiedCount!) verified nearby" : "Verified homes nearby")
                        .padding(14)
                }
                .padding(.top, 22)

            Spacer(minLength: 16)

            SoftError(message: errorMessage)

            VStack(spacing: 10) {
                PrimaryButton(title: "Create account", large: true, action: onCreate)
                HStack(spacing: 12) {
                    Rectangle().fill(Color.softDivider).frame(height: 1)
                    Text("or continue with").font(.soft(13)).foregroundStyle(Color.softSecondary)
                    Rectangle().fill(Color.softDivider).frame(height: 1)
                }
                .padding(.horizontal, 6).padding(.vertical, 4)
                HStack(spacing: 10) {
                    ProviderButton(title: "Google", loading: busy, action: onGoogle) { GoogleMark() }
                    ProviderButton(title: "Apple", dark: true, action: onApple) {
                        Image(systemName: "apple.logo").font(.system(size: 18, weight: .medium))
                    }
                }
                HStack(spacing: 16) {
                    SoftLink(title: "Log in", action: onLogin)
                    Text("·").font(.soft(14)).foregroundStyle(Color.softSecondary)
                    SoftLink(title: "Continue as demo@cabin.app", muted: true, action: onDemo)
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 6)
            }
            .padding(.bottom, 12)
        }
        .padding(.horizontal, 28)
        .padding(.top, 24)
        .task { await loadHero() }
    }

    /// Borrows the newest verified listing's photo for the hero, and its count for the tag.
    private func loadHero() async {
        var filters = ListingFilters()
        filters.excludeStale = false
        guard let res = try? await appState.api.listings(filters: filters, pageSize: 20) else { return }
        verifiedCount = res.listings.count
        heroURL = res.listings.first { !$0.images.isEmpty }?.images.first?.url
    }
}

// MARK: - Provider buttons

/// Half-width sign-in button: white with the Google mark, or black with the Apple logo.
private struct ProviderButton<Mark: View>: View {
    let title: String
    var dark: Bool = false
    var loading: Bool = false
    var action: () -> Void
    @ViewBuilder var mark: Mark

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if loading {
                    ProgressView().tint(dark ? .white : Color.softInk)
                } else {
                    mark.frame(width: 20, height: 20)
                    Text(title).font(.soft(16, .regular))
                }
            }
            .foregroundStyle(dark ? Color.white : Color.softText)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(dark ? Color.softInk : Color.white, in: Capsule())
        }
        .buttonStyle(SoftPressStyle())
        .disabled(loading)
    }
}

/// Google's four-colour "G", drawn as ring segments plus the bar so no image asset is needed.
struct GoogleMark: View {
    var body: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            let center = CGPoint(x: size / 2, y: size / 2)
            let width = size * 9.5 / 48                // ring thickness from the 48-unit artwork
            let radius = size / 2 - width / 2
            let red = Color(red: 234 / 255, green: 67 / 255, blue: 53 / 255)
            let yellow = Color(red: 251 / 255, green: 188 / 255, blue: 5 / 255)
            let green = Color(red: 52 / 255, green: 168 / 255, blue: 83 / 255)
            let blue = Color(red: 66 / 255, green: 133 / 255, blue: 244 / 255)

            ZStack {
                segment(center, radius, from: -153, to: -48).stroke(red, lineWidth: width)     // top
                segment(center, radius, from: 153, to: 207).stroke(yellow, lineWidth: width)   // left
                segment(center, radius, from: 49.5, to: 153).stroke(green, lineWidth: width)   // bottom
                segment(center, radius, from: 0, to: 49.5).stroke(blue, lineWidth: width)      // right
                Rectangle()                                                                    // the bar
                    .fill(blue)
                    .frame(width: size / 2, height: size * 9 / 48)
                    .position(x: size * 0.75, y: size * 24.5 / 48)
            }
        }
    }

    private func segment(_ center: CGPoint, _ radius: CGFloat, from start: Double, to end: Double) -> Path {
        Path { p in
            p.addArc(center: center, radius: radius,
                     startAngle: .degrees(start), endAngle: .degrees(end), clockwise: false)
        }
    }
}

// MARK: - Code entry

private struct CodeBoxes: View {
    let code: String

    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<6, id: \.self) { i in
                let digit = i < code.count ? String(code[code.index(code.startIndex, offsetBy: i)]) : ""
                ZStack {
                    RoundedRectangle(cornerRadius: SoftRadius.codeBox, style: .continuous).fill(Color.white)
                    if i == code.count {
                        RoundedRectangle(cornerRadius: SoftRadius.codeBox, style: .continuous)
                            .strokeBorder(Color.softInk, lineWidth: 2)
                    }
                    Text(digit).font(.soft(26))
                }
                .frame(height: 60)
                .softShadow(i == code.count ? .circle : .row)
            }
        }
    }
}

private struct Keypad: View {
    @Binding var code: String
    private let keys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"]

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
            ForEach(keys, id: \.self) { key in
                Button {
                    if key == "⌫" { code = String(code.dropLast()) }
                    else if !key.isEmpty, code.count < 6 { code += key }
                } label: {
                    Text(key)
                        .font(.soft(20))
                        .foregroundStyle(Color.softText)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(key.isEmpty ? Color.clear : Color.white.opacity(0.7), in: Capsule())
                }
                .buttonStyle(SoftPressStyle())
                .disabled(key.isEmpty)
            }
        }
        .padding(.top, 6)
    }
}

extension UIApplication {
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

import SwiftUI

/// Confirms a mobile number by SMS.
///
/// Without this the verified badge was hollow: `phone_verified` was never set by
/// anything, so an account could be verified with a number nobody had proven.
struct PhoneVerificationSheet: View {
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var phone = ""
    @State private var code = ""
    @State private var sentTo: String?
    @State private var devCode: String?
    @State private var devNote: String?
    @State private var busy = false
    @State private var errorMessage: String?

    private var canSend: Bool { phone.filter(\.isNumber).count >= 10 && !busy }
    private var canVerify: Bool { code.count == 6 && !busy }

    var body: some View {
        NavigationStack {
            Form {
                if sentTo == nil {
                    Section("Your mobile number") {
                        TextField("e.g. +63 917 555 0134", text: $phone)
                            .textContentType(.telephoneNumber)
                            .keyboardType(.phonePad)
                    }
                    Section {
                        Text("We'll text you a 6-digit code. Confirming your number is what earns the verified badge — people can tell a real poster from a throwaway.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                } else {
                    Section("Enter the code") {
                        TextField("6-digit code", text: $code)
                            .keyboardType(.numberPad)
                            .textContentType(.oneTimeCode)
                            .font(.title2.monospacedDigit())
                        Text("Sent to \(sentTo ?? "")")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    if let devCode {
                        Section("Development") {
                            Text("Code: \(devCode)").font(.body.monospaced())
                            if let devNote {
                                Text(devNote).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                    Section {
                        Button("Send a new code") { Task { await sendCode() } }
                            .disabled(busy)
                    }
                }

                if let errorMessage {
                    Section { Text(errorMessage).font(.footnote).foregroundStyle(.red) }
                }
            }
            .navigationTitle("Confirm your number")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if sentTo == nil {
                        Button("Send code") { Task { await sendCode() } }.disabled(!canSend)
                    } else {
                        Button("Confirm") { Task { await verify() } }.disabled(!canVerify)
                    }
                }
            }
            .onAppear {
                if phone.isEmpty { phone = appState.currentUser?.phone ?? "" }
            }
        }
    }

    private func sendCode() async {
        busy = true
        errorMessage = nil
        do {
            let res = try await appState.api.sendPhoneCode(phone: phone)
            sentTo = res.sentTo
            devCode = res.devCode
            devNote = res.note
            code = ""
        } catch {
            errorMessage = error.localizedDescription
        }
        busy = false
    }

    private func verify() async {
        busy = true
        errorMessage = nil
        do {
            _ = try await appState.api.verifyPhoneCode(code)
            await appState.refreshUser()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
        busy = false
    }
}

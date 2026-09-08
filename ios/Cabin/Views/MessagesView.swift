import SwiftUI

/// In-app chat — the survey's second-most requested feature (59%), and the way
/// to keep deals off the messaging apps where advance-fee scams start.
struct MessagesView: View {
    @Environment(AppState.self) private var appState

    @State private var conversations: [Conversation] = []
    @State private var loading = true
    @State private var errorMessage: String?

    var body: some View {
        VStack(spacing: 0) {
            SoftHeader {
                AppMark()
            } title: {
                Text("Messages").font(.softScreenTitle)
            } trailing: {
                Color.clear.frame(width: 48, height: 48)
            }

            ScrollView {
                LazyVStack(spacing: 12) {
                    if loading && conversations.isEmpty {
                        ProgressView().tint(Color.softInk).padding(.top, 40)
                    } else if conversations.isEmpty {
                        SoftEmpty(systemImage: "bubble.left", title: "No messages yet",
                                  message: errorMessage ?? "Message a poster from any listing and the thread will show up here.")
                    } else {
                        ForEach(conversations) { conv in
                            NavigationLink(value: conv) {
                                ConversationRow(conversation: conv)
                            }
                            .buttonStyle(SoftPressStyle())
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 120)
            }
        }
        .softScreen()
        .navigationDestination(for: Conversation.self) { conv in
            ChatView(conversation: conv)
        }
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        loading = true
        do {
            conversations = try await appState.api.conversations().conversations
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
        await appState.refreshSummary()
    }
}

struct ConversationRow: View {
    let conversation: Conversation

    var body: some View {
        SoftRow {
            SoftAvatar(name: conversation.counterparty?.name ?? "?", size: 48)

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(conversation.counterparty?.name ?? "Conversation")
                        .font(.softBody)
                        .lineLimit(1)
                    if conversation.counterparty?.isVerified == true {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 12))
                            .foregroundStyle(Color.softInk)
                    }
                }
                if let title = conversation.listing?.title {
                    Text(title).font(.soft(13)).foregroundStyle(Color.softSecondary).lineLimit(1)
                }
                if let last = conversation.lastMessage {
                    Text(last.body).font(.soft(14)).foregroundStyle(Color.softLabel).lineLimit(1)
                }
            }

            Spacer(minLength: 4)

            VStack(alignment: .trailing, spacing: 6) {
                if let at = conversation.lastMessageAt {
                    Text(Format.relative(at)).font(.soft(12)).foregroundStyle(Color.softMuted)
                }
                if conversation.unreadCount > 0 {
                    VTag(text: "\(conversation.unreadCount)", systemImage: nil, tint: .softAccent)
                }
            }
            .padding(.trailing, 4)
        }
    }
}

struct ChatView: View {
    @State private var conversation: Conversation
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    init(conversation: Conversation) {
        _conversation = State(initialValue: conversation)
    }

    @State private var messages: [Message] = []
    @State private var draft = ""
    @State private var loading = true
    @State private var sending = false
    @State private var errorMessage: String?

    private var counterpart: UserSummary? { conversation.counterparty }

    var body: some View {
        VStack(spacing: 0) {
            SoftHeader {
                CircleButton(systemImage: "chevron.left") { dismiss() }
            } title: {
                VStack(spacing: 2) {
                    Text(counterpart?.name ?? "Chat")
                        .font(.softScreenTitle).tracking(-0.24)
                        .lineLimit(1)
                    HStack(spacing: 6) {
                        Circle()
                            .fill(counterpart?.isVerified == true ? Color.softGreen : Color.softMuted)
                            .frame(width: 7, height: 7)
                        Text(subtitle).font(.soft(13)).foregroundStyle(Color.softSecondary)
                    }
                }
                .padding(.horizontal, 60)
            } trailing: {
                if let listing = conversation.listing {
                    NavigationLink(value: listing.id) {
                        ZStack {
                            Circle().fill(Color.white.opacity(0.75))
                            Image(systemName: "house").font(.system(size: 20, weight: .regular)).foregroundStyle(Color.softTextSoft)
                        }
                        .frame(width: 48, height: 48)
                        .softShadow(.circle)
                    }
                    .buttonStyle(SoftPressStyle())
                }
            }

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 14) {
                        if let listing = conversation.listing {
                            ListingFactsCard(listing: listing)
                        }
                        if loading { ProgressView().tint(Color.softInk).padding() }
                        ForEach(Array(messages.enumerated()), id: \.element.id) { index, message in
                            let mine = message.senderId == appState.currentUser?.id
                            let previousMine = index > 0 ? messages[index - 1].senderId == appState.currentUser?.id : true
                            MessageBubble(message: message, isMine: mine,
                                          showAvatar: !mine && previousMine,
                                          name: counterpart?.name ?? "")
                                .id(message.id)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 24)
                    .padding(.bottom, 24)
                }
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: messages.count) {
                    if let last = messages.last {
                        withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                    }
                }
            }

            SoftError(message: errorMessage).padding(.horizontal, 20)
            composer
        }
        .softScreen()
        .hidesSoftTabBar()
        .navigationDestination(for: String.self) { id in
            ListingDetailView(listingId: id)
        }
        .task { await load() }
    }

    private var subtitle: String {
        var parts: [String] = []
        parts.append(counterpart?.isVerified == true ? "Verified" : "Unverified")
        parts.append(counterpart?.isAgent == true ? "agent" : "account")
        return parts.joined(separator: " ")
    }

    private var composer: some View {
        VStack(spacing: 8) {
            // A standing reminder, because upfront-payment requests were the
            // most common scam pattern respondents described.
            Text("Never send a deposit before viewing the property in person.")
                .font(.soft(12)).foregroundStyle(Color.softSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 8)

            HStack(spacing: 10) {
                HStack(spacing: 12) {
                    TextField("", text: $draft, prompt: Text("Write a message").font(.soft(17)).foregroundStyle(Color.softMuted), axis: .vertical)
                        .font(.soft(17))
                        .lineLimit(1...4)
                        .onSubmit { Task { await send() } }
                }
                .padding(.horizontal, 20)
                .frame(minHeight: 60)
                .background(Color.white, in: RoundedRectangle(cornerRadius: 34, style: .continuous))
                .softShadow(.tab)

                CircleButton(systemImage: "paperplane", size: 56, inverted: true) {
                    Task { await send() }
                }
                .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || sending)
                .opacity(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.6 : 1)
            }
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
    }

    private func load() async {
        loading = true
        do {
            messages = try await appState.api.messages(conversationId: conversation.id).messages
            // A thread opened from "Message" arrives as bare ids; the list
            // endpoint carries the listing and counterparty the header needs.
            if conversation.listing == nil || conversation.counterparty == nil,
               let full = try? await appState.api.conversations().conversations.first(where: { $0.id == conversation.id }) {
                conversation = full
            }
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
        await appState.refreshSummary()
    }

    private func send() async {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        sending = true
        do {
            let message = try await appState.api.sendMessage(conversationId: conversation.id, body: body)
            messages.append(message)
            draft = ""
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
        sending = false
    }
}

/// The "here is how it checks out" card at the top of a thread: trust score and
/// how recently the owner confirmed the listing.
struct ListingFactsCard: View {
    let listing: Listing

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                SoftPhoto(url: listing.images.first?.url, radius: 12)
                    .frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text(listing.title).font(.soft(16, .regular)).lineLimit(1)
                    Text(Format.price(listing.price, listingType: listing.listingType))
                        .font(.soft(14)).foregroundStyle(Color.softSecondary)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 6).padding(.top, 4)
            HStack(spacing: 10) {
                SoftTile(systemImage: "checkmark.shield", label: "Trust score", value: "\(listing.verificationScore)", compact: true)
                SoftTile(systemImage: "calendar", label: "Confirmed", value: confirmedAgo, compact: true)
            }
        }
        .padding(10)
        .background(Color.white, in: RoundedRectangle(cornerRadius: SoftRadius.bubble, style: .continuous))
        .softShadow(.row)
    }

    private var confirmedAgo: String {
        guard let date = Format.date(from: listing.lastConfirmedAt ?? "") ?? Format.date(from: listing.createdAt) else { return "—" }
        let days = max(0, Calendar.current.dateComponents([.day], from: date, to: Date()).day ?? 0)
        if days == 0 { return "today" }
        if days < 30 { return "\(days)d" }
        return "\(days / 30)mo"
    }
}

struct MessageBubble: View {
    let message: Message
    let isMine: Bool
    var showAvatar: Bool = false
    var name: String = ""

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            if isMine {
                Spacer(minLength: 50)
            } else if showAvatar {
                SoftAvatar(name: name, size: 40)
            } else {
                Color.clear.frame(width: 40, height: 1)
            }
            Text(message.body)
                .font(.soft(16))
                .foregroundStyle(Color.softText)
                .padding(.horizontal, 18).padding(.vertical, 14)
                .background(Color.white, in: RoundedRectangle(cornerRadius: SoftRadius.bubble, style: .continuous))
                .softShadow(.row)
                .frame(maxWidth: 300, alignment: isMine ? .trailing : .leading)
            if !isMine { Spacer(minLength: 0) }
        }
        .frame(maxWidth: .infinity, alignment: isMine ? .trailing : .leading)
        .accessibilityLabel("\(isMine ? "You" : name): \(message.body), \(Format.relative(message.createdAt))")
    }
}

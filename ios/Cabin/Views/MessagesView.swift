import SwiftUI

/// In-app chat — the survey's second-most requested feature (59%), and the way
/// to keep deals off the messaging apps where advance-fee scams start.
struct MessagesView: View {
    @Environment(AppState.self) private var appState

    @State private var conversations: [Conversation] = []
    @State private var loading = true
    @State private var errorMessage: String?

    var body: some View {
        Group {
            if loading && conversations.isEmpty {
                ProgressView()
            } else if conversations.isEmpty {
                ContentUnavailableView {
                    Label("No messages yet", systemImage: "bubble.left.and.bubble.right")
                } description: {
                    Text(errorMessage ?? "Message a poster from any listing and the thread will show up here.")
                }
            } else {
                List(conversations) { conv in
                    NavigationLink {
                        ChatView(conversation: conv)
                    } label: {
                        ConversationRow(conversation: conv)
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Messages")
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
        HStack(spacing: 12) {
            RemoteImage(url: conversation.listing?.images.first?.url)
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(conversation.counterparty?.name ?? "Conversation")
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    if conversation.counterparty?.isVerified == true {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.caption2)
                            .foregroundStyle(Color.cabinForest)
                    }
                }
                if let title = conversation.listing?.title {
                    Text(title).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                if let last = conversation.lastMessage {
                    Text(last.body).font(.footnote).foregroundStyle(.secondary).lineLimit(1)
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 4) {
                if let at = conversation.lastMessageAt {
                    Text(Format.relative(at)).font(.caption2).foregroundStyle(.tertiary)
                }
                if conversation.unreadCount > 0 {
                    Text("\(conversation.unreadCount)")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Color.cabinForest, in: Capsule())
                }
            }
        }
        .padding(.vertical, 4)
    }
}

struct ChatView: View {
    let conversation: Conversation
    @Environment(AppState.self) private var appState

    @State private var messages: [Message] = []
    @State private var draft = ""
    @State private var loading = true
    @State private var sending = false
    @State private var errorMessage: String?

    var body: some View {
        VStack(spacing: 0) {
            if let listing = conversation.listing {
                listingHeader(listing)
                Divider()
            }

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 8) {
                        if loading { ProgressView().padding() }
                        ForEach(messages) { message in
                            MessageBubble(
                                message: message,
                                isMine: message.senderId == appState.currentUser?.id
                            )
                            .id(message.id)
                        }
                    }
                    .padding(12)
                }
                .onChange(of: messages.count) {
                    if let last = messages.last {
                        withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                    }
                }
            }

            if let errorMessage {
                Text(errorMessage).font(.caption).foregroundStyle(.red).padding(.horizontal)
            }

            composer
        }
        .navigationTitle(conversation.counterparty?.name ?? "Chat")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func listingHeader(_ listing: Listing) -> some View {
        NavigationLink { ListingDetailView(listingId: listing.id) } label: {
            HStack(spacing: 10) {
                RemoteImage(url: listing.images.first?.url)
                    .frame(width: 40, height: 40)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                VStack(alignment: .leading, spacing: 1) {
                    Text(listing.title).font(.footnote.weight(.medium)).lineLimit(1)
                    Text(Format.price(listing.price, listingType: listing.listingType))
                        .font(.caption).foregroundStyle(Color.cabinForest)
                }
                Spacer()
                VerificationBadge(status: listing.verificationStatus, compact: true)
                Image(systemName: "chevron.right").font(.caption2).foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
        }
        .buttonStyle(.plain)
    }

    private var composer: some View {
        VStack(spacing: 6) {
            // A standing reminder, because upfront-payment requests were the
            // most common scam pattern respondents described.
            Label("Never send a deposit before viewing the property in person.",
                  systemImage: "exclamationmark.shield")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: 8) {
                TextField("Message…", text: $draft, axis: .vertical)
                    .lineLimit(1...4)
                    .textFieldStyle(.roundedBorder)
                Button {
                    Task { await send() }
                } label: {
                    Image(systemName: "arrow.up.circle.fill").font(.title2)
                }
                .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || sending)
            }
        }
        .padding(12)
        .background(.bar)
    }

    private func load() async {
        loading = true
        do {
            messages = try await appState.api.messages(conversationId: conversation.id).messages
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

struct MessageBubble: View {
    let message: Message
    let isMine: Bool

    var body: some View {
        HStack {
            if isMine { Spacer(minLength: 40) }
            VStack(alignment: isMine ? .trailing : .leading, spacing: 2) {
                Text(message.body)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(isMine ? Color.cabinForest : Color(.secondarySystemBackground),
                                in: RoundedRectangle(cornerRadius: 14))
                    .foregroundStyle(isMine ? .white : .primary)
                Text(Format.relative(message.createdAt))
                    .font(.caption2).foregroundStyle(.tertiary)
            }
            if !isMine { Spacer(minLength: 40) }
        }
    }
}

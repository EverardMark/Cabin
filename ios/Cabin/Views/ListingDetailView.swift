import SwiftUI

struct ListingDetailView: View {
    let listingId: String
    @Environment(AppState.self) private var appState

    @State private var listing: Listing?
    @State private var loading = true
    @State private var errorMessage: String?
    @State private var showContact = false

    var body: some View {
        Group {
            if loading {
                ProgressView()
            } else if let listing {
                content(listing)
            } else {
                ContentUnavailableView(
                    "Couldn't load listing",
                    systemImage: "exclamationmark.triangle",
                    description: Text(errorMessage ?? "")
                )
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    @ViewBuilder
    private func content(_ listing: Listing) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                gallery(listing)

                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Pill(text: listing.listingType == "rent" ? "For rent" : "For sale")
                        Pill(
                            text: Format.capitalized(listing.propertyType),
                            background: Color(.secondarySystemBackground),
                            foreground: .primary
                        )
                    }

                    Text(Format.price(listing.price, listingType: listing.listingType))
                        .font(.title.bold())
                        .foregroundStyle(Color.cabinForest)

                    Text(listing.title).font(.title3.weight(.semibold))

                    let address = fullAddress(listing)
                    if !address.isEmpty {
                        Label(address, systemImage: "mappin.and.ellipse")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    featureCard(listing)

                    if !listing.description.isEmpty {
                        Text("About this property")
                            .font(.headline)
                            .padding(.top, 4)
                        Text(listing.description)
                            .foregroundStyle(.primary.opacity(0.85))
                    }

                    if let owner = listing.owner {
                        ownerCard(owner)
                    }

                    Button {
                        showContact = true
                    } label: {
                        Text("Contact agent").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .padding(.top, 8)
                }
                .padding(16)
            }
        }
        .alert("Contact agent", isPresented: $showContact) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("This is where you'd reach out to \(listing.owner?.name ?? "the agent").")
        }
    }

    private func gallery(_ listing: Listing) -> some View {
        Group {
            if listing.images.isEmpty {
                RemoteImage(url: nil)
                    .frame(height: 280)
                    .frame(maxWidth: .infinity)
            } else {
                TabView {
                    ForEach(listing.images) { image in
                        RemoteImage(url: image.url)
                            .frame(maxWidth: .infinity)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: listing.images.count > 1 ? .automatic : .never))
                .frame(height: 280)
            }
        }
    }

    private func featureCard(_ listing: Listing) -> some View {
        HStack {
            FeatureStat(value: Format.beds(listing.bedrooms), label: "Bedrooms")
            Divider().frame(height: 34)
            FeatureStat(value: Format.baths(listing.bathrooms), label: "Bathrooms")
            Divider().frame(height: 34)
            FeatureStat(value: Format.area(listing.areaSqft), label: "Area")
        }
        .padding(.vertical, 14)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
    }

    private func ownerCard(_ owner: UserSummary) -> some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(Color.cabinForest).frame(width: 44, height: 44)
                Text(owner.name.first.map { String($0).uppercased() } ?? "?")
                    .font(.headline)
                    .foregroundStyle(.white)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text("Listed by").font(.caption).foregroundStyle(.secondary)
                HStack(spacing: 6) {
                    Text(owner.name).font(.headline)
                    if owner.isAgent { AgentBadge() }
                }
            }
            Spacer()
        }
        .padding(14)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
    }

    private func fullAddress(_ listing: Listing) -> String {
        [listing.address, listing.city, listing.state, listing.zipCode]
            .filter { !$0.isEmpty }
            .joined(separator: ", ")
    }

    private func load() async {
        loading = true
        errorMessage = nil
        do {
            listing = try await appState.api.listing(id: listingId)
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }
}

import SwiftUI

/// AsyncImage that resolves relative ("/uploads/...") or absolute URLs.
struct RemoteImage: View {
    let url: String?
    var contentMode: ContentMode = .fill

    var body: some View {
        Group {
            if let url, let resolved = Format.imageURL(url) {
                AsyncImage(url: resolved) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().aspectRatio(contentMode: contentMode)
                    case .failure:
                        placeholder
                    case .empty:
                        ZStack { placeholder; ProgressView() }
                    @unknown default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .clipped()
    }

    private var placeholder: some View {
        Rectangle().fill(Color(.secondarySystemBackground))
    }
}

struct Pill: View {
    let text: String
    var background: Color = Color.cabinForest.opacity(0.14)
    var foreground: Color = .cabinForest

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(background, in: Capsule())
            .foregroundStyle(foreground)
    }
}

/// A labeled statistic used in the listing detail (beds / baths / area).
struct FeatureStat: View {
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: 2) {
            Text(value).font(.headline)
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}

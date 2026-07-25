import SwiftUI

/// A "✓ Verified" badge. Use `compact` for a seal-only variant on tight surfaces.
struct VerifiedBadge: View {
    var compact: Bool = false

    var body: some View {
        if compact {
            Image(systemName: "checkmark.seal.fill")
                .foregroundStyle(Color.cabinForest)
                .font(.subheadline)
        } else {
            HStack(spacing: 3) {
                Image(systemName: "checkmark.seal.fill")
                Text("Verified")
            }
            .font(.caption.weight(.semibold))
            .foregroundStyle(Color.cabinForest)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(Color.cabinForest.opacity(0.12), in: Capsule())
        }
    }
}

/// Compact rating display: ★ 4.7 (12). Shows "No reviews yet" when empty.
struct RatingStars: View {
    let avg: Double
    let count: Int
    var showCount: Bool = true

    var body: some View {
        if count > 0 {
            HStack(spacing: 3) {
                Image(systemName: "star.fill").foregroundStyle(.orange)
                Text(String(format: "%.1f", avg)).fontWeight(.semibold)
                if showCount {
                    Text("(\(count))").foregroundStyle(.secondary)
                }
            }
            .font(.caption)
        } else {
            Text("No reviews yet")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}

/// A colored availability pill (Sold / Rented / Pending / Active).
struct StatusPill: View {
    let status: String

    private var color: Color {
        switch status.lowercased() {
        case "sold", "rented": return .red
        case "pending": return .orange
        case "inactive": return .gray
        default: return .cabinForest
        }
    }

    var body: some View {
        Text(Format.statusLabel(status))
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(color.opacity(0.15), in: Capsule())
            .foregroundStyle(color)
    }
}

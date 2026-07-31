import SwiftUI

/// A small pill indicating the user is a verified real estate agent.
struct AgentBadge: View {
    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: "checkmark.seal.fill")
            Text("Agent")
        }
        .font(.caption2.weight(.semibold))
        .padding(.horizontal, 7)
        .padding(.vertical, 3)
        .background(Color.cabinForest.opacity(0.15), in: Capsule())
        .foregroundStyle(Color.cabinForest)
    }
}

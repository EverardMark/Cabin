package com.cabin.app.data.model

import kotlinx.serialization.Serializable

/**
 * Verification states. The survey's dominant signal: 86% of respondents called
 * trust and verification "extremely important" and 79% wanted verified-only
 * listings, so this drives most of the trust UI.
 */
object Verification {
    const val UNVERIFIED = "unverified"
    const val PENDING = "pending"
    const val VERIFIED = "verified"
    const val FLAGGED = "flagged"
    const val REJECTED = "rejected"

    fun label(status: String): String = when (status) {
        VERIFIED -> "Verified"
        FLAGGED -> "Check details"
        REJECTED -> "Failed review"
        PENDING -> "Checking…"
        else -> "Not verified"
    }
}

@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    val role: String = "user",
    val phone: String = "",
    val bio: String = "",
    val licenseNo: String = "",
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val verificationStatus: String = Verification.UNVERIFIED,
    val verificationScore: Int = 0,
    val verificationNotes: String = "",
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val createdAt: String = "",
) {
    val isAgent: Boolean get() = role == "agent"
    val isAdmin: Boolean get() = role == "admin"
    val isVerified: Boolean get() = verificationStatus == Verification.VERIFIED
}

@Serializable
data class UserSummary(
    val id: String,
    val name: String,
    val email: String = "",
    val role: String = "user",
    val verificationStatus: String = Verification.UNVERIFIED,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
) {
    val isAgent: Boolean get() = role == "agent"
    val isVerified: Boolean get() = verificationStatus == Verification.VERIFIED
}

/** Another user's public profile. */
@Serializable
data class PublicProfile(
    val id: String,
    val name: String,
    val role: String = "user",
    val bio: String = "",
    val verificationStatus: String = Verification.UNVERIFIED,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
    val createdAt: String = "",
) {
    val isAgent: Boolean get() = role == "agent"
    val isVerified: Boolean get() = verificationStatus == Verification.VERIFIED
}

@Serializable
data class ListingImage(
    val id: String,
    val listingId: String = "",
    val url: String,
    val position: Int = 0,
    val createdAt: String = "",
)

@Serializable
data class Listing(
    val id: String,
    val userId: String = "",
    val title: String,
    val description: String = "",
    val price: Long = 0,
    val currency: String = "PHP",
    val propertyType: String = "house",
    val listingType: String = "sale",
    val bedrooms: Int = 0,
    val bathrooms: Double = 0.0,
    val areaSqft: Int = 0,
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: String = "active",
    val verificationStatus: String = Verification.PENDING,
    val verificationScore: Int = 0,
    val verificationSummary: String = "",
    val verificationFlags: List<String> = emptyList(),
    val verifiedAt: String? = null,
    val lastConfirmedAt: String? = null,
    /** When paid promotion expires. Only counts while also verified. */
    val featuredUntil: String? = null,
    val reportCount: Int = 0,
    val viewCount: Int = 0,
    val images: List<ListingImage> = emptyList(),
    val owner: UserSummary? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
) {
    val isVerified: Boolean get() = verificationStatus == Verification.VERIFIED

    /**
     * Whether this listing gets promoted placement.
     *
     * Deliberately gated on verification: paying buys reach, never credibility.
     * A listing that hasn't passed screening is never promoted, whatever its
     * owner paid.
     */
    val isFeatured: Boolean
        get() {
            if (!isVerified) return false
            val until = runCatching { java.time.Instant.parse(featuredUntil) }.getOrNull() ?: return false
            return until.isAfter(java.time.Instant.now())
        }
}

@Serializable
data class Message(
    val id: String,
    val conversationId: String = "",
    val senderId: String = "",
    val body: String = "",
    val readAt: String? = null,
    val createdAt: String = "",
)

@Serializable
data class Conversation(
    val id: String,
    val listingId: String = "",
    val inquirerId: String = "",
    val ownerId: String = "",
    val listing: Listing? = null,
    val counterparty: UserSummary? = null,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val lastMessageAt: String? = null,
    val createdAt: String = "",
)

@Serializable
data class ViewingRequest(
    val id: String,
    val listingId: String = "",
    val requesterId: String = "",
    val ownerId: String = "",
    val scheduledFor: String = "",
    val status: String = "requested",
    val note: String = "",
    val responseNote: String = "",
    val listing: Listing? = null,
    val requester: UserSummary? = null,
    val owner: UserSummary? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class Review(
    val id: String,
    val subjectUserId: String = "",
    val authorId: String = "",
    val listingId: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val author: UserSummary? = null,
    val createdAt: String = "",
)

@Serializable
data class SavedSearch(
    val id: String,
    val userId: String = "",
    val name: String = "",
    val query: String = "",
    val alertsEnabled: Boolean = true,
    val newMatches: Int = 0,
    val lastAlertedAt: String = "",
    val createdAt: String = "",
)

@Serializable
data class PhoneCodeResponse(
    val sentTo: String = "",
    val expiresIn: Int = 0,
    /** Only present when no SMS gateway is configured, and never in production. */
    val devCode: String? = null,
    val note: String? = null,
)

@Serializable
data class SendPhoneCodeRequest(val phone: String)

@Serializable
data class VerifyPhoneCodeRequest(val code: String)

/**
 * A paid promotion package for a single listing. Featured listings were the
 * survey's one unanimous supply-side ask — every agent picked it (6/6), and 58%
 * of owners did.
 */
@Serializable
data class FeaturePlan(
    val id: String,
    val label: String,
    val days: Int = 0,
    val price: Long = 0,
)

@Serializable
data class FeaturePlansResponse(
    val plans: List<FeaturePlan> = emptyList(),
    val currency: String = "PHP",
    val note: String = "",
)

@Serializable
data class FeatureResponse(
    val listing: Listing,
    val plan: FeaturePlan? = null,
    /** False while no payment provider is wired up, so the UI can say so. */
    val paid: Boolean = false,
)

@Serializable
data class FeatureRequest(val planId: String)

/** Price context for a listing — 46% of respondents asked for comparison tools. */
@Serializable
data class PriceComparison(
    val listingId: String = "",
    val price: Long = 0,
    val sampleSize: Int = 0,
    val median: Long = 0,
    val min: Long = 0,
    val max: Long = 0,
    val pricePerSqft: Double = 0.0,
    val medianPerSqft: Double = 0.0,
    val verdict: String = "insufficient_data",
    val percentDiff: Double = 0.0,
    val comparables: List<Listing> = emptyList(),
) {
    val verdictLabel: String
        get() = when (verdict) {
            "below_market" -> "Below market"
            "above_market" -> "Above market"
            "at_market" -> "At market"
            else -> "Not enough comparable listings yet"
        }
}

/** Badge counts for the bottom bar. */
@Serializable
data class HomeSummary(
    val unreadMessages: Int = 0,
    val upcomingViewings: Int = 0,
    val pendingViewingRequests: Int = 0,
    val listingsNeedingAttention: Int = 0,
)

/** Everything the browse screen filters on. */
data class ListingFilters(
    val query: String = "",
    val listingType: String? = null,
    val propertyType: String? = null,
    val city: String = "",
    val minPrice: Long? = null,
    val maxPrice: Long? = null,
    val minBedrooms: Int? = null,
    /** Defaults on: the single most requested feature in the survey. */
    val verifiedOnly: Boolean = true,
    val excludeStale: Boolean = true,
    val sort: String = "recent",
) {
    /** Renders the filters as a query string, for saving as a search. */
    fun toQueryString(): String = buildList {
        if (query.isNotBlank()) add("q=${java.net.URLEncoder.encode(query, "UTF-8")}")
        listingType?.let { add("listing_type=$it") }
        propertyType?.let { add("property_type=$it") }
        if (city.isNotBlank()) add("city=${java.net.URLEncoder.encode(city, "UTF-8")}")
        minPrice?.let { add("min_price=$it") }
        maxPrice?.let { add("max_price=$it") }
        minBedrooms?.let { add("min_bedrooms=$it") }
        if (verifiedOnly) add("verified_only=true")
        if (excludeStale) add("exclude_stale=true")
        add("sort=$sort")
    }.joinToString("&")
}

// MARK: - Requests

@Serializable
data class AuthResponse(val token: String, val user: User)

@Serializable
data class MeResponse(val user: User)

@Serializable
data class ProfileResponse(val user: PublicProfile)

@Serializable
data class VerificationVerdict(
    val status: String = "",
    val score: Int = 0,
    val summary: String = "",
    val flags: List<String> = emptyList(),
)

@Serializable
data class VerificationResponse(val user: User, val verification: VerificationVerdict? = null)

@Serializable
data class ListingsResponse(
    val listings: List<Listing> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
)

@Serializable
data class ConversationsResponse(val conversations: List<Conversation> = emptyList())

@Serializable
data class MessagesResponse(
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
)

@Serializable
data class ViewingsResponse(val viewings: List<ViewingRequest> = emptyList())

@Serializable
data class ReviewsResponse(val reviews: List<Review> = emptyList())

@Serializable
data class SavedSearchesResponse(val searches: List<SavedSearch> = emptyList())

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val phone: String = "",
    val role: String,
)

@Serializable
data class GoogleAuthRequest(val idToken: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class ProfileRequest(
    val name: String? = null,
    val phone: String? = null,
    val bio: String? = null,
    val licenseNo: String? = null,
    val role: String? = null,
)

@Serializable
data class ListingRequest(
    val title: String,
    val description: String,
    val price: Long,
    val propertyType: String,
    val listingType: String,
    val bedrooms: Int,
    val bathrooms: Double,
    val areaSqft: Int,
    val address: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val status: String? = null,
    /** A map pin is what makes a listing findable in map search. */
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class ReorderImagesRequest(val imageIds: List<String>)

@Serializable
data class ReportRequest(val reason: String, val details: String = "")

@Serializable
data class MessageRequest(val body: String)

@Serializable
data class ViewingRequestBody(val scheduledFor: String, val note: String = "")

@Serializable
data class ViewingUpdateBody(
    val status: String? = null,
    val responseNote: String? = null,
    val scheduledFor: String? = null,
)

@Serializable
data class ReviewRequest(val rating: Int, val comment: String = "", val listingId: String = "")

@Serializable
data class SavedSearchRequest(val name: String, val query: String)

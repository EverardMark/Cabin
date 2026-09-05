package com.cabin.app.data

import com.cabin.app.data.model.Conversation
import com.cabin.app.data.model.GoogleAuthRequest
import com.cabin.app.data.model.FeaturePlansResponse
import com.cabin.app.data.model.FeatureRequest
import com.cabin.app.data.model.FeatureResponse
import com.cabin.app.data.model.HomeSummary
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.ListingFilters
import com.cabin.app.data.model.ListingImage
import com.cabin.app.data.model.ListingRequest
import com.cabin.app.data.model.LoginRequest
import com.cabin.app.data.model.Message
import com.cabin.app.data.model.MessageRequest
import com.cabin.app.data.model.MessagesResponse
import com.cabin.app.data.model.PhoneCodeResponse
import com.cabin.app.data.model.PriceComparison
import com.cabin.app.data.model.SendPhoneCodeRequest
import com.cabin.app.data.model.VerifyPhoneCodeRequest
import com.cabin.app.data.model.ProfileRequest
import com.cabin.app.data.model.PublicProfile
import com.cabin.app.data.model.RegisterRequest
import com.cabin.app.data.model.ReorderImagesRequest
import com.cabin.app.data.model.ReportRequest
import com.cabin.app.data.model.Review
import com.cabin.app.data.model.ReviewRequest
import com.cabin.app.data.model.SavedSearch
import com.cabin.app.data.model.SavedSearchRequest
import com.cabin.app.data.model.User
import com.cabin.app.data.model.VerificationResponse
import com.cabin.app.data.model.ViewingRequest
import com.cabin.app.data.model.ViewingRequestBody
import com.cabin.app.data.model.ViewingUpdateBody
import com.cabin.app.data.remote.CabinApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Single source of truth for auth state and all API access. Methods return
 * [Result] so callers can render success/error without try/catch.
 */
class CabinRepository(
    private val api: CabinApi,
    private val session: SessionStore,
) {
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    /** Badge counts for the bottom bar, refreshed after actions. */
    private val _summary = MutableStateFlow(HomeSummary())
    val summary: StateFlow<HomeSummary> = _summary.asStateFlow()

    /** Load any persisted session into memory. Call once at startup. */
    suspend fun bootstrap() {
        session.load()
        _user.value = session.cachedUser
    }

    // --- auth ---

    suspend fun login(email: String, password: String): Result<User> = runCatching {
        persist(api.login(LoginRequest(email.trim(), password)).let { it.token to it.user })
    }

    suspend fun register(
        name: String,
        email: String,
        password: String,
        phone: String,
        role: String,
    ): Result<User> = runCatching {
        val res = api.register(RegisterRequest(email.trim(), password, name.trim(), phone.trim(), role))
        persist(res.token to res.user)
    }

    /** Exchanges a Google ID token for a Cabin session (server verifies it and links/creates the account). */
    suspend fun googleSignIn(idToken: String): Result<User> = runCatching {
        val res = api.googleSignIn(GoogleAuthRequest(idToken))
        persist(res.token to res.user)
    }

    private suspend fun persist(pair: Pair<String, User>): User {
        session.save(pair.first, pair.second)
        _user.value = pair.second
        return pair.second
    }

    suspend fun logout() {
        session.clear()
        _user.value = null
        _summary.value = HomeSummary()
    }

    suspend fun refreshUser(): Result<User> = runCatching {
        val user = api.me().user
        session.save(session.currentToken ?: "", user)
        _user.value = user
        user
    }

    suspend fun refreshSummary() {
        runCatching { api.summary() }.onSuccess { _summary.value = it }
    }

    // --- profile & verification ---

    suspend fun updateProfile(body: ProfileRequest): Result<User> = runCatching {
        val user = api.updateProfile(body).user
        session.save(session.currentToken ?: "", user)
        _user.value = user
        user
    }

    /** Submits the account for automated identity review and applies the result. */
    suspend fun requestVerification(): Result<VerificationResponse> = runCatching {
        val res = api.requestVerification()
        session.save(session.currentToken ?: "", res.user)
        _user.value = res.user
        res
    }

    suspend fun sendPhoneCode(phone: String): Result<PhoneCodeResponse> = runCatching {
        api.sendPhoneCode(SendPhoneCodeRequest(phone.trim()))
    }

    suspend fun verifyPhoneCode(code: String): Result<User> = runCatching {
        val user = api.verifyPhoneCode(VerifyPhoneCodeRequest(code.trim())).user
        session.save(session.currentToken ?: "", user)
        _user.value = user
        user
    }

    suspend fun profile(userId: String): Result<PublicProfile> = runCatching { api.profile(userId).user }

    // --- listings ---

    suspend fun listings(filters: ListingFilters, pageSize: Int = 50): Result<List<Listing>> = runCatching {
        api.listings(filters.toQueryMap(pageSize)).listings
    }

    /** Listings inside a map viewport. */
    suspend fun listingsInBox(
        filters: ListingFilters,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ): Result<List<Listing>> = runCatching {
        val map = filters.toQueryMap(100).toMutableMap()
        map["min_lat"] = minLat.toString()
        map["max_lat"] = maxLat.toString()
        map["min_lng"] = minLng.toString()
        map["max_lng"] = maxLng.toString()
        api.listings(map).listings
    }

    suspend fun listing(id: String): Result<Listing> = runCatching { api.listing(id) }

    suspend fun createListing(request: ListingRequest): Result<Listing> = runCatching {
        api.createListing(request)
    }

    suspend fun updateListing(id: String, request: ListingRequest): Result<Listing> = runCatching {
        api.updateListing(id, request)
    }

    suspend fun deleteListing(id: String): Result<Unit> = runCatching {
        val res = api.deleteListing(id)
        if (!res.isSuccessful) error("Delete failed (${res.code()})")
    }

    suspend fun deleteImage(listingId: String, imageId: String): Result<Listing> = runCatching {
        api.deleteImage(listingId, imageId)
    }

    suspend fun reorderImages(listingId: String, imageIds: List<String>): Result<Listing> = runCatching {
        api.reorderImages(listingId, ReorderImagesRequest(imageIds))
    }

    suspend fun myListings(): Result<List<Listing>> = runCatching { api.myListings().listings }

    suspend fun confirmListing(id: String): Result<Listing> = runCatching { api.confirmListing(id) }

    suspend fun reportListing(id: String, reason: String, details: String): Result<Unit> = runCatching {
        val res = api.reportListing(id, ReportRequest(reason, details))
        if (!res.isSuccessful) error("Report failed (${res.code()})")
    }

    suspend fun featurePlans(): Result<FeaturePlansResponse> = runCatching { api.featurePlans() }

    suspend fun featureListing(id: String, planId: String): Result<FeatureResponse> = runCatching {
        api.featureListing(id, FeatureRequest(planId))
    }

    suspend fun priceComparison(listingId: String): Result<PriceComparison> = runCatching {
        api.priceComparison(listingId)
    }

    suspend fun uploadImage(listingId: String, file: File): Result<ListingImage> = runCatching {
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        val body = file.asRequestBody(mime.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("image", file.name, body)
        api.uploadImage(listingId, part)
    }

    // --- messaging ---

    suspend fun startConversation(listingId: String): Result<Conversation> = runCatching {
        api.startConversation(listingId)
    }

    suspend fun conversations(): Result<List<Conversation>> = runCatching {
        api.conversations().conversations
    }

    suspend fun messages(conversationId: String): Result<MessagesResponse> = runCatching {
        api.messages(conversationId)
    }

    suspend fun sendMessage(conversationId: String, body: String): Result<Message> = runCatching {
        api.sendMessage(conversationId, MessageRequest(body))
    }

    // --- viewings ---

    suspend fun requestViewing(listingId: String, scheduledForIso: String, note: String): Result<ViewingRequest> =
        runCatching { api.requestViewing(listingId, ViewingRequestBody(scheduledForIso, note)) }

    suspend fun viewings(): Result<List<ViewingRequest>> = runCatching { api.viewings().viewings }

    suspend fun updateViewing(id: String, status: String? = null, responseNote: String? = null): Result<ViewingRequest> =
        runCatching { api.updateViewing(id, ViewingUpdateBody(status = status, responseNote = responseNote)) }

    // --- reviews ---

    suspend fun reviews(userId: String): Result<List<Review>> = runCatching { api.reviews(userId).reviews }

    suspend fun createReview(userId: String, rating: Int, comment: String): Result<Review> = runCatching {
        api.createReview(userId, ReviewRequest(rating, comment))
    }

    // --- saved searches ---

    suspend fun savedSearches(): Result<List<SavedSearch>> = runCatching { api.savedSearches().searches }

    suspend fun saveSearch(name: String, query: String): Result<SavedSearch> = runCatching {
        api.saveSearch(SavedSearchRequest(name, query))
    }

    suspend fun deleteSavedSearch(id: String): Result<Unit> = runCatching {
        val res = api.deleteSavedSearch(id)
        if (!res.isSuccessful) error("Delete failed (${res.code()})")
    }

    suspend fun runSavedSearch(id: String): Result<List<Listing>> = runCatching {
        api.runSavedSearch(id).listings
    }
}

/** Renders filters into the query map Retrofit sends. */
private fun ListingFilters.toQueryMap(pageSize: Int): Map<String, String> = buildMap {
    if (query.isNotBlank()) put("q", query.trim())
    listingType?.let { put("listing_type", it) }
    propertyType?.let { put("property_type", it) }
    if (city.isNotBlank()) put("city", city.trim())
    minPrice?.let { put("min_price", it.toString()) }
    maxPrice?.let { put("max_price", it.toString()) }
    minBedrooms?.let { put("min_bedrooms", it.toString()) }
    if (verifiedOnly) put("verified_only", "true")
    if (excludeStale) put("exclude_stale", "true")
    put("sort", sort)
    put("page_size", pageSize.toString())
}

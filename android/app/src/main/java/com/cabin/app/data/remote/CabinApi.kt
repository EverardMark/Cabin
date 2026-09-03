package com.cabin.app.data.remote

import com.cabin.app.data.model.AuthResponse
import com.cabin.app.data.model.Conversation
import com.cabin.app.data.model.ConversationsResponse
import com.cabin.app.data.model.FeaturePlansResponse
import com.cabin.app.data.model.FeatureRequest
import com.cabin.app.data.model.FeatureResponse
import com.cabin.app.data.model.GoogleAuthRequest
import com.cabin.app.data.model.HomeSummary
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.ListingImage
import com.cabin.app.data.model.ListingRequest
import com.cabin.app.data.model.ListingsResponse
import com.cabin.app.data.model.LoginRequest
import com.cabin.app.data.model.MeResponse
import com.cabin.app.data.model.Message
import com.cabin.app.data.model.MessageRequest
import com.cabin.app.data.model.MessagesResponse
import com.cabin.app.data.model.PriceComparison
import com.cabin.app.data.model.ProfileRequest
import com.cabin.app.data.model.ProfileResponse
import com.cabin.app.data.model.RegisterRequest
import com.cabin.app.data.model.ReorderImagesRequest
import com.cabin.app.data.model.ReportRequest
import com.cabin.app.data.model.Review
import com.cabin.app.data.model.ReviewRequest
import com.cabin.app.data.model.ReviewsResponse
import com.cabin.app.data.model.SavedSearch
import com.cabin.app.data.model.SavedSearchRequest
import com.cabin.app.data.model.SavedSearchesResponse
import com.cabin.app.data.model.VerificationResponse
import com.cabin.app.data.model.ViewingRequest
import com.cabin.app.data.model.ViewingRequestBody
import com.cabin.app.data.model.ViewingUpdateBody
import com.cabin.app.data.model.ViewingsResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface CabinApi {

    // --- auth & profile ---

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/v1/auth/google")
    suspend fun googleSignIn(@Body body: GoogleAuthRequest): AuthResponse

    @GET("api/v1/auth/me")
    suspend fun me(): MeResponse

    @GET("api/v1/me/summary")
    suspend fun summary(): HomeSummary

    @PATCH("api/v1/me")
    suspend fun updateProfile(@Body body: ProfileRequest): MeResponse

    /** Submits the signed-in account for automated identity review. */
    @POST("api/v1/me/verification")
    suspend fun requestVerification(): VerificationResponse

    @GET("api/v1/users/{id}")
    suspend fun profile(@Path("id") id: String): ProfileResponse

    // --- listings ---

    /**
     * Browse and search. Filters are passed as a map so the same call serves
     * the list, the map viewport and saved-search replay.
     */
    @GET("api/v1/listings")
    suspend fun listings(@QueryMap filters: Map<String, String>): ListingsResponse

    @GET("api/v1/listings/{id}")
    suspend fun listing(@Path("id") id: String): Listing

    @POST("api/v1/listings")
    suspend fun createListing(@Body body: ListingRequest): Listing

    @PUT("api/v1/listings/{id}")
    suspend fun updateListing(@Path("id") id: String, @Body body: ListingRequest): Listing

    @DELETE("api/v1/listings/{id}")
    suspend fun deleteListing(@Path("id") id: String): Response<Unit>

    @GET("api/v1/me/listings")
    suspend fun myListings(): ListingsResponse

    /** Owner confirms the listing is still available, clearing the stale flag. */
    @POST("api/v1/listings/{id}/confirm")
    suspend fun confirmListing(@Path("id") id: String): Listing

    @POST("api/v1/listings/{id}/report")
    suspend fun reportListing(@Path("id") id: String, @Body body: ReportRequest): Response<Unit>

    /** Promotion packages a poster can buy. */
    @GET("api/v1/feature-plans")
    suspend fun featurePlans(): FeaturePlansResponse

    /** Buys promoted placement for a listing the caller owns. */
    @POST("api/v1/listings/{id}/feature")
    suspend fun featureListing(@Path("id") id: String, @Body body: FeatureRequest): FeatureResponse

    @GET("api/v1/listings/{id}/price-comparison")
    suspend fun priceComparison(@Path("id") id: String): PriceComparison

    /** Removes one photo; the server re-screens the listing afterwards. */
    @DELETE("api/v1/listings/{id}/images/{imageId}")
    suspend fun deleteImage(@Path("id") id: String, @Path("imageId") imageId: String): Listing

    /** Sets photo order — the first photo is the card thumbnail. */
    @PUT("api/v1/listings/{id}/images/order")
    suspend fun reorderImages(@Path("id") id: String, @Body body: ReorderImagesRequest): Listing

    @Multipart
    @POST("api/v1/listings/{id}/images")
    suspend fun uploadImage(@Path("id") id: String, @Part image: MultipartBody.Part): ListingImage

    // --- messaging ---

    @POST("api/v1/listings/{id}/conversations")
    suspend fun startConversation(@Path("id") listingId: String): Conversation

    @GET("api/v1/conversations")
    suspend fun conversations(): ConversationsResponse

    @GET("api/v1/conversations/{id}/messages")
    suspend fun messages(@Path("id") conversationId: String): MessagesResponse

    @POST("api/v1/conversations/{id}/messages")
    suspend fun sendMessage(@Path("id") conversationId: String, @Body body: MessageRequest): Message

    // --- viewings ---

    @POST("api/v1/listings/{id}/viewings")
    suspend fun requestViewing(@Path("id") listingId: String, @Body body: ViewingRequestBody): ViewingRequest

    @GET("api/v1/viewings")
    suspend fun viewings(): ViewingsResponse

    @PATCH("api/v1/viewings/{id}")
    suspend fun updateViewing(@Path("id") id: String, @Body body: ViewingUpdateBody): ViewingRequest

    // --- reviews ---

    @GET("api/v1/users/{id}/reviews")
    suspend fun reviews(@Path("id") userId: String, @Query("limit") limit: Int = 50): ReviewsResponse

    @POST("api/v1/users/{id}/reviews")
    suspend fun createReview(@Path("id") userId: String, @Body body: ReviewRequest): Review

    // --- saved searches ---

    @GET("api/v1/me/searches")
    suspend fun savedSearches(): SavedSearchesResponse

    @POST("api/v1/me/searches")
    suspend fun saveSearch(@Body body: SavedSearchRequest): SavedSearch

    @DELETE("api/v1/me/searches/{id}")
    suspend fun deleteSavedSearch(@Path("id") id: String): Response<Unit>

    @GET("api/v1/me/searches/{id}/results")
    suspend fun runSavedSearch(@Path("id") id: String): ListingsResponse
}

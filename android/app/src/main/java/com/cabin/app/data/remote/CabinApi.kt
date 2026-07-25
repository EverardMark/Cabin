package com.cabin.app.data.remote

import com.cabin.app.data.model.AuthResponse
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.ListingImage
import com.cabin.app.data.model.ListingRequest
import com.cabin.app.data.model.ListingsResponse
import com.cabin.app.data.model.LoginRequest
import com.cabin.app.data.model.MeResponse
import com.cabin.app.data.model.RegisterRequest
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface CabinApi {

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("api/v1/auth/me")
    suspend fun me(): MeResponse

    @GET("api/v1/listings")
    suspend fun listings(
        @Query("q") q: String? = null,
        @Query("city") city: String? = null,
        @Query("property_type") propertyType: String? = null,
        @Query("listing_type") listingType: String? = null,
        @Query("min_price") minPrice: Long? = null,
        @Query("max_price") maxPrice: Long? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
    ): ListingsResponse

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

    @Multipart
    @POST("api/v1/listings/{id}/images")
    suspend fun uploadImage(@Path("id") id: String, @Part image: MultipartBody.Part): ListingImage
}

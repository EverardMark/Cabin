package com.cabin.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    val phone: String = "",
    val verified: Boolean = false,
    val createdAt: String = "",
)

@Serializable
data class UserSummary(
    val id: String,
    val name: String,
    val email: String = "",
    val verified: Boolean = false,
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
)

@Serializable
data class Review(
    val id: String,
    val subjectId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val createdAt: String = "",
)

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
    val currency: String = "USD",
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
    val images: List<ListingImage> = emptyList(),
    val owner: UserSummary? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class AuthResponse(val token: String, val user: User)

@Serializable
data class MeResponse(val user: User)

@Serializable
data class ListingsResponse(
    val listings: List<Listing> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
)

@Serializable
data class RegisterRequest(val email: String, val password: String, val name: String, val phone: String)

@Serializable
data class GoogleAuthRequest(val idToken: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

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
)

@Serializable
data class ReviewsResponse(
    val reviews: List<Review> = emptyList(),
    val ratingAvg: Double = 0.0,
    val ratingCount: Int = 0,
)

@Serializable
data class ReviewRequest(val rating: Int, val comment: String)

@Serializable
data class ReportRequest(val reason: String, val detail: String)

/** Partial listing update carrying only the availability status. */
@Serializable
data class StatusRequest(val status: String)

@Serializable
data class VerificationResponse(val user: User, val status: String)

@Serializable
data class StatusResponse(val status: String)

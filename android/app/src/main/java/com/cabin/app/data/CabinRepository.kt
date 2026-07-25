package com.cabin.app.data

import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.ListingImage
import com.cabin.app.data.model.ListingRequest
import com.cabin.app.data.model.LoginRequest
import com.cabin.app.data.model.RegisterRequest
import com.cabin.app.data.model.User
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

    /** Load any persisted session into memory. Call once at startup. */
    suspend fun bootstrap() {
        session.load()
        _user.value = session.cachedUser
    }

    suspend fun login(email: String, password: String): Result<User> = runCatching {
        val res = api.login(LoginRequest(email.trim(), password))
        session.save(res.token, res.user)
        _user.value = res.user
        res.user
    }

    suspend fun register(name: String, email: String, password: String): Result<User> = runCatching {
        val res = api.register(RegisterRequest(email.trim(), password, name.trim()))
        session.save(res.token, res.user)
        _user.value = res.user
        res.user
    }

    suspend fun logout() {
        session.clear()
        _user.value = null
    }

    suspend fun listings(
        query: String? = null,
        propertyType: String? = null,
        listingType: String? = null,
        sort: String? = null,
    ): Result<List<Listing>> = runCatching {
        api.listings(
            q = query?.trim()?.ifBlank { null },
            propertyType = propertyType,
            listingType = listingType,
            sort = sort,
            pageSize = 50,
        ).listings
    }

    suspend fun listing(id: String): Result<Listing> = runCatching { api.listing(id) }

    suspend fun createListing(request: ListingRequest): Result<Listing> = runCatching {
        api.createListing(request)
    }

    suspend fun myListings(): Result<List<Listing>> = runCatching { api.myListings().listings }

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
}

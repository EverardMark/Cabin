package com.cabin.app.ui.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cabin.app.data.ServiceLocator
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.ListingImage
import com.cabin.app.data.model.ListingRequest
import com.cabin.app.util.userMessage
import kotlinx.coroutines.launch
import java.io.File

/**
 * Backs both posting and editing.
 *
 * Editing matters because screening tells owners *why* a listing was flagged —
 * without a way to act on that, the verification loop is a dead end.
 */
class CreateListingViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Photos already saved on the listing being edited. */
    var existingImages by mutableStateOf<List<ListingImage>>(emptyList())
        private set

    fun startEditing(listing: Listing) {
        existingImages = listing.images
    }

    /** Creates when [listingId] is null, otherwise saves changes. */
    fun submit(
        listingId: String?,
        request: ListingRequest,
        images: List<File>,
        onSaved: (String) -> Unit,
    ) {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            val result = if (listingId == null) {
                repo.createListing(request)
            } else {
                repo.updateListing(listingId, request)
            }
            result.fold(
                onSuccess = { listing ->
                    // Best-effort image uploads; a failed image shouldn't discard the listing.
                    images.forEach { file -> repo.uploadImage(listing.id, file) }
                    loading = false
                    onSaved(listing.id)
                },
                onFailure = { e ->
                    loading = false
                    error = e.userMessage()
                },
            )
        }
    }

    fun deleteListing(id: String, onDeleted: () -> Unit) {
        if (loading) return
        loading = true
        viewModelScope.launch {
            repo.deleteListing(id).fold(
                onSuccess = { loading = false; onDeleted() },
                onFailure = { e -> loading = false; error = e.userMessage() },
            )
        }
    }

    fun deletePhoto(listingId: String, imageId: String) {
        viewModelScope.launch {
            repo.deleteImage(listingId, imageId).fold(
                onSuccess = { existingImages = it.images },
                onFailure = { e -> error = e.userMessage() },
            )
        }
    }

    /** Moves a photo to the front, which is what buyers see on the card. */
    fun promotePhoto(listingId: String, imageId: String) {
        val order = listOf(imageId) + existingImages.map { it.id }.filter { it != imageId }
        viewModelScope.launch {
            repo.reorderImages(listingId, order).fold(
                onSuccess = { existingImages = it.images },
                onFailure = { e -> error = e.userMessage() },
            )
        }
    }
}

package com.cabin.app.ui.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cabin.app.data.ServiceLocator
import com.cabin.app.data.model.ListingRequest
import com.cabin.app.util.userMessage
import kotlinx.coroutines.launch
import java.io.File

class CreateListingViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun submit(request: ListingRequest, images: List<File>, onCreated: (String) -> Unit) {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            repo.createListing(request).fold(
                onSuccess = { listing ->
                    // Best-effort image uploads; a failed image shouldn't discard the listing.
                    images.forEach { file -> repo.uploadImage(listing.id, file) }
                    loading = false
                    onCreated(listing.id)
                },
                onFailure = { e ->
                    loading = false
                    error = e.userMessage()
                },
            )
        }
    }
}

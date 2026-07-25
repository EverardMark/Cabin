package com.cabin.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cabin.app.data.ServiceLocator
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.Review
import com.cabin.app.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val listing: Listing? = null,
    val reviews: List<Review> = emptyList(),
    val currentUserId: String? = null,
    val error: String? = null,
    val actionError: String? = null,
)

class ListingDetailViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var listingId: String = ""

    fun load(id: String) {
        listingId = id
        if (id.isBlank()) {
            _state.update { it.copy(loading = false, error = "Listing not found") }
            return
        }
        _state.update { it.copy(loading = it.listing == null, error = null, currentUserId = repo.user.value?.id) }
        viewModelScope.launch { refresh() }
    }

    /** Loads the listing and — once we know the owner — their reviews. */
    private suspend fun refresh() {
        repo.listing(listingId).fold(
            onSuccess = { listing ->
                _state.update { it.copy(loading = false, listing = listing) }
                listing.owner?.id?.let { ownerId ->
                    repo.reviews(ownerId).onSuccess { res ->
                        _state.update { it.copy(reviews = res.reviews) }
                    }
                }
            },
            onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
        )
    }

    /** Returns null on success, or a user-facing error message. Refreshes on success. */
    suspend fun submitReview(ownerId: String, rating: Int, comment: String): String? =
        repo.addReview(ownerId, rating, comment).fold(
            onSuccess = { refresh(); null },
            onFailure = { it.userMessage() },
        )

    /** Returns null on success, or a user-facing error message. */
    suspend fun submitReport(reason: String, detail: String): String? {
        val id = _state.value.listing?.id ?: return "Listing not found"
        return repo.reportListing(id, reason, detail).fold(
            onSuccess = { null },
            onFailure = { it.userMessage() },
        )
    }

    fun changeStatus(status: String) {
        val id = _state.value.listing?.id ?: return
        viewModelScope.launch {
            repo.updateListingStatus(id, status).fold(
                onSuccess = { updated -> _state.update { it.copy(listing = updated) } },
                onFailure = { e -> _state.update { it.copy(actionError = e.userMessage()) } },
            )
        }
    }

    fun clearActionError() {
        _state.update { it.copy(actionError = null) }
    }
}

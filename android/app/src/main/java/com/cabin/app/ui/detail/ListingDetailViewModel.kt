package com.cabin.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cabin.app.data.ServiceLocator
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.FeaturePlan
import com.cabin.app.data.model.PriceComparison
import com.cabin.app.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class DetailUiState(
    val loading: Boolean = true,
    val listing: Listing? = null,
    val comparison: PriceComparison? = null,
    val error: String? = null,
    /** Non-null once a chat thread is opened, so the screen can navigate to it. */
    val openConversationId: String? = null,
    val actionMessage: String? = null,
    val busy: Boolean = false,
    val featurePlans: List<FeaturePlan> = emptyList(),
    val featureNote: String = "",
)

class ListingDetailViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    val currentUserId: String? get() = repo.user.value?.id

    fun load(id: String) {
        if (id.isBlank()) {
            _state.update { it.copy(loading = false, error = "Listing not found") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.listing(id).fold(
                onSuccess = { listing -> _state.update { it.copy(loading = false, listing = listing) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
            // Price context is a nice-to-have; never fail the screen over it.
            repo.priceComparison(id).onSuccess { pc -> _state.update { it.copy(comparison = pc) } }
        }
    }

    /** Opens (or reuses) a chat thread with the poster. */
    fun startConversation() {
        val id = _state.value.listing?.id ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.startConversation(id).fold(
                onSuccess = { c -> _state.update { it.copy(busy = false, openConversationId = c.id) } },
                onFailure = { e -> _state.update { it.copy(busy = false, actionMessage = e.userMessage()) } },
            )
        }
    }

    fun conversationOpened() = _state.update { it.copy(openConversationId = null) }

    /** Books a viewing [daysFromNow] days out at [hour] local time. */
    fun requestViewing(daysFromNow: Long, hour: Int, note: String) {
        val id = _state.value.listing?.id ?: return
        val at = java.time.LocalDate.now()
            .plusDays(daysFromNow)
            .atTime(hour, 0)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.requestViewing(id, at.toString(), note).fold(
                onSuccess = {
                    _state.update {
                        it.copy(busy = false, actionMessage = "Viewing requested — the poster has to accept it.")
                    }
                },
                onFailure = { e -> _state.update { it.copy(busy = false, actionMessage = e.userMessage()) } },
            )
        }
    }

    /** Files a scam / misleading-listing report. */
    fun report(reason: String, details: String) {
        val id = _state.value.listing?.id ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.reportListing(id, reason, details).fold(
                onSuccess = {
                    _state.update { it.copy(busy = false, actionMessage = "Thanks — our moderators will look at this.") }
                    load(id)
                },
                onFailure = { e -> _state.update { it.copy(busy = false, actionMessage = e.userMessage()) } },
            )
        }
    }

    /** Owner action: confirm the listing is still available. */
    fun confirmAvailability() {
        val id = _state.value.listing?.id ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.confirmListing(id).fold(
                onSuccess = { l ->
                    _state.update { it.copy(busy = false, listing = l, actionMessage = "Marked as still available.") }
                },
                onFailure = { e -> _state.update { it.copy(busy = false, actionMessage = e.userMessage()) } },
            )
        }
    }

    /** Loads the promotion packages, for the "Feature this listing" dialog. */
    fun loadFeaturePlans() {
        if (_state.value.featurePlans.isNotEmpty()) return
        viewModelScope.launch {
            repo.featurePlans().onSuccess { res ->
                _state.update { it.copy(featurePlans = res.plans, featureNote = res.note) }
            }
        }
    }

    /**
     * Buys promoted placement. The server refuses this on a listing that has not
     * passed screening — paying buys reach, never credibility.
     */
    fun featureListing(planId: String) {
        val id = _state.value.listing?.id ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.featureListing(id, planId).fold(
                onSuccess = { res ->
                    _state.update {
                        it.copy(
                            busy = false,
                            listing = res.listing,
                            // Be honest rather than implying money changed hands.
                            actionMessage = if (res.paid) {
                                "Your listing is now featured."
                            } else {
                                "Your listing is now featured. No payment was taken — checkout isn't connected yet."
                            },
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(busy = false, actionMessage = e.userMessage()) } },
            )
        }
    }

    fun clearActionMessage() = _state.update { it.copy(actionMessage = null) }
}

/** Kept for readability at call sites that stamp "now". */
internal fun nowIso(): String = Instant.now().toString()

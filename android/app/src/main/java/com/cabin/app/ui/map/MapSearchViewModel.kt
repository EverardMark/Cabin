package com.cabin.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cabin.app.data.ServiceLocator
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.ListingFilters
import com.cabin.app.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val loading: Boolean = false,
    val listings: List<Listing> = emptyList(),
    val selected: Listing? = null,
    val error: String? = null,
    val filters: ListingFilters = ListingFilters(),
)

class MapSearchViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    /** Loads listings inside the viewport the map is currently showing. */
    fun load(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.listingsInBox(_state.value.filters, minLat, maxLat, minLng, maxLng).fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, listings = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }

    fun select(listing: Listing) = _state.update { it.copy(selected = listing) }

    fun clearSelection() = _state.update { it.copy(selected = null) }
}

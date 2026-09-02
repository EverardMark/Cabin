package com.cabin.app.ui.listings

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

data class ListingsUiState(
    val loading: Boolean = false,
    val listings: List<Listing> = emptyList(),
    val error: String? = null,
    val filters: ListingFilters = ListingFilters(),
    val savedMessage: String? = null,
)

class ListingsViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    private val _state = MutableStateFlow(ListingsUiState())
    val state: StateFlow<ListingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(filters = it.filters.copy(query = query)) }
    }

    fun setPropertyType(type: String?) = update { it.copy(propertyType = type) }

    fun setListingType(type: String?) = update { it.copy(listingType = type) }

    fun setSort(sort: String) = update { it.copy(sort = sort) }

    /** The survey's single most requested feature: verified listings only (79%). */
    fun setVerifiedOnly(value: Boolean) = update { it.copy(verifiedOnly = value) }

    fun setExcludeStale(value: Boolean) = update { it.copy(excludeStale = value) }

    private fun update(transform: (ListingFilters) -> ListingFilters) {
        _state.update { it.copy(filters = transform(it.filters)) }
        refresh()
    }

    /** Saves the current filters so the user is told about new matches later. */
    fun saveCurrentSearch(name: String) {
        val filters = _state.value.filters
        viewModelScope.launch {
            repo.saveSearch(name, filters.toQueryString()).fold(
                onSuccess = { s ->
                    _state.update { it.copy(savedMessage = "We'll flag new matches for “${s.name}”.") }
                },
                onFailure = { e -> _state.update { it.copy(savedMessage = e.userMessage()) } },
            )
        }
    }

    fun clearSavedMessage() = _state.update { it.copy(savedMessage = null) }

    /** A readable default name for a saved search, from the active filters. */
    fun defaultSearchName(): String {
        val f = _state.value.filters
        val parts = buildList {
            if (f.city.isNotBlank()) add(f.city)
            if (f.query.isNotBlank()) add(f.query)
            f.propertyType?.let { add(it.replaceFirstChar { c -> c.uppercase() }) }
            f.listingType?.let { add(if (it == "rent") "for rent" else "for sale") }
        }
        return if (parts.isEmpty()) "My search" else parts.joinToString(" · ")
    }

    fun refresh() {
        val filters = _state.value.filters
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.listings(filters).fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, listings = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }
}

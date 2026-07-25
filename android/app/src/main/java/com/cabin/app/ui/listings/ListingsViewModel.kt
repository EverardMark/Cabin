package com.cabin.app.ui.listings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cabin.app.data.ServiceLocator
import com.cabin.app.data.model.Listing
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
    val query: String = "",
    val propertyType: String? = null,   // null = any
    val listingType: String? = null,    // null = any, "sale" or "rent"
)

class ListingsViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    private val _state = MutableStateFlow(ListingsUiState())
    val state: StateFlow<ListingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun setPropertyType(type: String?) {
        _state.update { it.copy(propertyType = type) }
        refresh()
    }

    fun setListingType(type: String?) {
        _state.update { it.copy(listingType = type) }
        refresh()
    }

    fun refresh() {
        val s = _state.value
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.listings(
                query = s.query,
                propertyType = s.propertyType,
                listingType = s.listingType,
                sort = "recent",
            ).fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, listings = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }
}

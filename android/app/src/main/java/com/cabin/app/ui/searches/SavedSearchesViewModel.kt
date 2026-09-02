package com.cabin.app.ui.searches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cabin.app.data.ServiceLocator
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.SavedSearch
import com.cabin.app.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavedSearchesUiState(
    val loading: Boolean = true,
    val searches: List<SavedSearch> = emptyList(),
    val error: String? = null,
    val openSearch: SavedSearch? = null,
    val results: List<Listing> = emptyList(),
    val resultsLoading: Boolean = false,
)

/** Saved searches with a new-match count, from the survey's free-text asks. */
class SavedSearchesViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    private val _state = MutableStateFlow(SavedSearchesUiState())
    val state: StateFlow<SavedSearchesUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.savedSearches().fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, searches = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }

    fun open(search: SavedSearch) {
        _state.update { it.copy(openSearch = search, resultsLoading = true, results = emptyList()) }
        viewModelScope.launch {
            repo.runSavedSearch(search.id).fold(
                onSuccess = { list -> _state.update { it.copy(resultsLoading = false, results = list) } },
                onFailure = { e -> _state.update { it.copy(resultsLoading = false, error = e.userMessage()) } },
            )
            refresh()
        }
    }

    fun closeResults() = _state.update { it.copy(openSearch = null, results = emptyList()) }

    fun delete(search: SavedSearch) {
        viewModelScope.launch {
            repo.deleteSavedSearch(search.id)
            refresh()
        }
    }
}

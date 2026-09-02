package com.cabin.app.ui.viewings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cabin.app.data.ServiceLocator
import com.cabin.app.data.model.ViewingRequest
import com.cabin.app.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ViewingsUiState(
    val loading: Boolean = true,
    val viewings: List<ViewingRequest> = emptyList(),
    val error: String? = null,
)

/**
 * Viewing appointments — 57% of respondents asked for in-app scheduling, and
 * "scheduling viewings" was the fourth-biggest cause of transaction delays.
 */
class ViewingsViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    private val _state = MutableStateFlow(ViewingsUiState())
    val state: StateFlow<ViewingsUiState> = _state.asStateFlow()

    val currentUserId: String? get() = repo.user.value?.id

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.viewings().fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, viewings = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
            repo.refreshSummary()
        }
    }

    fun update(id: String, status: String) {
        viewModelScope.launch {
            repo.updateViewing(id, status = status).fold(
                onSuccess = { refresh() },
                onFailure = { e -> _state.update { it.copy(error = e.userMessage()) } },
            )
        }
    }
}

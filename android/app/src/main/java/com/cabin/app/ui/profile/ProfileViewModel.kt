package com.cabin.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cabin.app.data.ServiceLocator
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.User
import com.cabin.app.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val listings: List<Listing> = emptyList(),
    val error: String? = null,
    val verifying: Boolean = false,
    val verifyError: String? = null,
)

class ProfileViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    val user: StateFlow<User?> = repo.user

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.myListings().fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, listings = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }

    /** Requests verification for the current user; the badge appears via [user] on success. */
    fun verify() {
        _state.update { it.copy(verifying = true, verifyError = null) }
        viewModelScope.launch {
            repo.requestVerification().fold(
                onSuccess = { _state.update { it.copy(verifying = false) } },
                onFailure = { e -> _state.update { it.copy(verifying = false, verifyError = e.userMessage()) } },
            )
        }
    }

    fun logout() {
        viewModelScope.launch { repo.logout() }
    }
}

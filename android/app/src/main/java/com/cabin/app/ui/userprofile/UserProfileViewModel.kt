package com.cabin.app.ui.userprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cabin.app.data.ServiceLocator
import com.cabin.app.data.model.PublicProfile
import com.cabin.app.data.model.Review
import com.cabin.app.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val loading: Boolean = true,
    val profile: PublicProfile? = null,
    val reviews: List<Review> = emptyList(),
    val error: String? = null,
    val submitting: Boolean = false,
    val reviewMessage: String? = null,
)

/** Public profile with reviews — 39% of respondents asked for agent ratings. */
class UserProfileViewModel(private val userId: String) : ViewModel() {
    private val repo = ServiceLocator.repository

    private val _state = MutableStateFlow(UserProfileUiState())
    val state: StateFlow<UserProfileUiState> = _state.asStateFlow()

    val isSelf: Boolean get() = repo.user.value?.id == userId

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.profile(userId).fold(
                onSuccess = { p -> _state.update { it.copy(loading = false, profile = p) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
            repo.reviews(userId).onSuccess { list -> _state.update { it.copy(reviews = list) } }
        }
    }

    fun submitReview(rating: Int, comment: String) {
        _state.update { it.copy(submitting = true, reviewMessage = null) }
        viewModelScope.launch {
            repo.createReview(userId, rating, comment).fold(
                onSuccess = {
                    _state.update { it.copy(submitting = false, reviewMessage = "Thanks — your review is posted.") }
                    refresh()
                },
                onFailure = { e ->
                    _state.update { it.copy(submitting = false, reviewMessage = e.userMessage()) }
                },
            )
        }
    }

    fun clearReviewMessage() = _state.update { it.copy(reviewMessage = null) }
}

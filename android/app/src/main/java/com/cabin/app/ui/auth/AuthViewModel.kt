package com.cabin.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cabin.app.data.ServiceLocator
import com.cabin.app.data.model.User
import com.cabin.app.util.userMessage
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun clearError() {
        error = null
    }

    fun login(email: String, password: String) = submit { repo.login(email, password) }

    fun register(name: String, email: String, password: String) =
        submit { repo.register(name, email, password) }

    // On success the repository's user flow emits and CabinRoot swaps to the main UI.
    private fun submit(block: suspend () -> Result<User>) {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            val result = block()
            loading = false
            result.onFailure { error = it.userMessage() }
        }
    }
}

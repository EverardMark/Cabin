package com.cabin.app.ui.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cabin.app.BuildConfig
import com.cabin.app.data.ServiceLocator
import com.cabin.app.data.model.User
import com.cabin.app.util.userMessage
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
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

    fun register(name: String, email: String, password: String, role: String) =
        submit { repo.register(name, email, password, role) }

    /**
     * Runs the Credential Manager "Sign in with Google" flow, then exchanges the
     * returned ID token for a Cabin session. Needs an Activity [context].
     */
    fun signInWithGoogle(context: Context) {
        if (loading) return
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            error = "Google sign-in isn't configured yet."
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val idToken = googleIdToken(context)
                repo.googleSignIn(idToken).onFailure { error = it.userMessage() }
            } catch (e: GetCredentialCancellationException) {
                // User dismissed the Google sheet — not an error.
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }

    private suspend fun googleIdToken(context: Context): String {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = CredentialManager.create(context).getCredential(context, request)
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected credential type from Google")
    }

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

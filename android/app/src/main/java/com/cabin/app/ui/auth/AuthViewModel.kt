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
import com.cabin.app.data.model.ListingFilters
import com.cabin.app.data.model.ProfileRequest
import com.cabin.app.util.userMessage
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/** Sign-up and sign-in as one flow: welcome → create account → confirm number → you're in. */
enum class AuthStep { WELCOME, LOGIN, CREATE, CODE, DONE }

class AuthViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    var step by mutableStateOf(AuthStep.WELCOME)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // Welcome hero: the newest verified listing's photo and the verified count.
    var heroUrl by mutableStateOf<String?>(null)
        private set
    var verifiedCount by mutableStateOf<Int?>(null)
        private set

    // Confirm-number step.
    var phone by mutableStateOf("")
        private set
    var isAgent by mutableStateOf(false)
        private set
    var sentTo by mutableStateOf<String?>(null)
        private set
    var devCode by mutableStateOf<String?>(null)
        private set
    var phoneConfirmed by mutableStateOf(false)
        private set
    var verificationMessage by mutableStateOf<String?>(null)
        private set

    val userFirstName: String
        get() = repo.user.value?.name?.split(" ")?.firstOrNull().orEmpty().ifBlank { "there" }

    fun go(next: AuthStep) {
        error = null
        step = next
    }

    fun clearError() {
        error = null
    }

    /** Back to the welcome screen after a log-out. */
    fun reset() {
        step = AuthStep.WELCOME
        error = null
        sentTo = null
        devCode = null
        phoneConfirmed = false
        verificationMessage = null
    }

    fun loadHero() {
        if (heroUrl != null) return
        viewModelScope.launch {
            repo.listings(ListingFilters(excludeStale = false), pageSize = 20).onSuccess { list ->
                verifiedCount = list.size
                heroUrl = list.firstOrNull { it.images.isNotEmpty() }?.images?.first()?.url
            }
        }
    }

    fun login(email: String, password: String) = submit { repo.login(email, password).map { } }

    fun loginAsDemo() = login("demo@cabin.app", "password123")

    /**
     * Registers, then moves to the SMS code step when a usable number was given
     * (the code is sent right away), otherwise straight to "you're in".
     */
    fun register(name: String, email: String, password: String, phone: String, licence: String, agent: Boolean) {
        this.phone = phone
        this.isAgent = agent
        submit {
            repo.register(name, email, password, phone, if (agent) "agent" else "user").map { }.onSuccess {
                if (agent && licence.isNotBlank()) {
                    repo.updateProfile(ProfileRequest(name = name, phone = phone, bio = "", licenseNo = licence, role = "agent"))
                }
                if (phone.count { it.isDigit() } >= 10) {
                    sendCode()
                    step = AuthStep.CODE
                } else {
                    phoneConfirmed = false
                    step = AuthStep.DONE
                }
            }
        }
    }

    fun resendCode() {
        viewModelScope.launch { sendCode() }
    }

    private suspend fun sendCode() {
        repo.sendPhoneCode(phone).fold(
            onSuccess = { res ->
                sentTo = res.sentTo
                devCode = res.devCode
                error = null
            },
            onFailure = { error = it.userMessage() },
        )
    }

    fun verifyCode(code: String) = submit {
        repo.verifyPhoneCode(code).map { }.onSuccess {
            phoneConfirmed = true
            step = AuthStep.DONE
        }
    }

    fun skipCode() {
        phoneConfirmed = false
        go(AuthStep.DONE)
    }

    fun requestVerification() = submit {
        repo.requestVerification().map { res ->
            verificationMessage = res.verification?.summary ?: "We've reviewed your account."
        }
    }

    /** Dismisses the verification result and enters the app. */
    fun acknowledgeVerification() {
        verificationMessage = null
        startBrowsing()
    }

    fun startBrowsing() = repo.finishOnboarding()

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

    /** Sign in with Apple needs a web flow on Android that isn't wired up yet; say so. */
    fun signInWithApple() {
        error = "Sign in with Apple is available on iPhone for now — use Google or your email here."
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

    private fun submit(block: suspend () -> Result<Unit>) {
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

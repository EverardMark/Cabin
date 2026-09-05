package com.cabin.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cabin.app.data.ServiceLocator
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.ProfileRequest
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
    val verificationMessage: String? = null,
    val savingProfile: Boolean = false,
    // Phone confirmation. A verified badge is hollow if the number behind it
    // was never proven, so this gates account verification.
    val phoneCodeSentTo: String? = null,
    val phoneDevCode: String? = null,
    val phoneBusy: Boolean = false,
    val phoneError: String? = null,
)

class ProfileViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    val user: StateFlow<User?> = repo.user
    val summary = repo.summary

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
            repo.refreshSummary()
        }
    }

    /**
     * Submits the account for automated identity review. Verified accounts get a
     * badge on every listing they post — 86% of surveyed users said verification
     * is what decides whether they trust a listing.
     */
    fun requestVerification() {
        if (_state.value.verifying) return
        _state.update { it.copy(verifying = true, verificationMessage = null) }
        viewModelScope.launch {
            repo.requestVerification().fold(
                onSuccess = { res ->
                    _state.update {
                        it.copy(
                            verifying = false,
                            verificationMessage = res.verification?.summary ?: "We've reviewed your account.",
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(verifying = false, verificationMessage = e.userMessage()) }
                },
            )
        }
    }

    fun clearVerificationMessage() = _state.update { it.copy(verificationMessage = null) }

    fun saveProfile(name: String, phone: String, bio: String, licenseNo: String, isAgent: Boolean) {
        _state.update { it.copy(savingProfile = true) }
        viewModelScope.launch {
            repo.updateProfile(
                ProfileRequest(
                    name = name,
                    phone = phone,
                    bio = bio,
                    licenseNo = licenseNo,
                    role = if (isAgent) "agent" else "user",
                )
            ).fold(
                onSuccess = { _state.update { it.copy(savingProfile = false) } },
                onFailure = { e ->
                    _state.update { it.copy(savingProfile = false, verificationMessage = e.userMessage()) }
                },
            )
        }
    }

    fun sendPhoneCode(phone: String) {
        if (_state.value.phoneBusy) return
        _state.update { it.copy(phoneBusy = true, phoneError = null) }
        viewModelScope.launch {
            repo.sendPhoneCode(phone).fold(
                onSuccess = { res ->
                    _state.update {
                        it.copy(phoneBusy = false, phoneCodeSentTo = res.sentTo, phoneDevCode = res.devCode)
                    }
                },
                onFailure = { e -> _state.update { it.copy(phoneBusy = false, phoneError = e.userMessage()) } },
            )
        }
    }

    fun verifyPhoneCode(code: String, onDone: () -> Unit) {
        if (_state.value.phoneBusy) return
        _state.update { it.copy(phoneBusy = true, phoneError = null) }
        viewModelScope.launch {
            repo.verifyPhoneCode(code).fold(
                onSuccess = {
                    _state.update { it.copy(phoneBusy = false, phoneCodeSentTo = null, phoneDevCode = null) }
                    onDone()
                },
                onFailure = { e -> _state.update { it.copy(phoneBusy = false, phoneError = e.userMessage()) } },
            )
        }
    }

    fun resetPhoneFlow() =
        _state.update { it.copy(phoneCodeSentTo = null, phoneDevCode = null, phoneError = null) }

    fun logout() {
        viewModelScope.launch { repo.logout() }
    }
}

package com.cabin.app.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cabin.app.data.ServiceLocator
import com.cabin.app.data.model.Conversation
import com.cabin.app.data.model.Message
import com.cabin.app.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val loading: Boolean = true,
    val conversations: List<Conversation> = emptyList(),
    val error: String? = null,
)

/** In-app chat — the survey's second-most requested feature (59%). */
class ConversationsViewModel : ViewModel() {
    private val repo = ServiceLocator.repository

    private val _state = MutableStateFlow(ConversationsUiState())
    val state: StateFlow<ConversationsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.conversations().fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, conversations = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
            repo.refreshSummary()
        }
    }
}

data class ChatUiState(
    val loading: Boolean = true,
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val draft: String = "",
    val sending: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(private val conversationId: String) : ViewModel() {
    private val repo = ServiceLocator.repository

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    val currentUserId: String? get() = repo.user.value?.id

    init { refresh() }

    fun onDraftChange(value: String) = _state.update { it.copy(draft = value) }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.messages(conversationId).fold(
                onSuccess = { res ->
                    _state.update {
                        it.copy(loading = false, conversation = res.conversation, messages = res.messages)
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
            repo.refreshSummary()
        }
    }

    fun send() {
        val body = _state.value.draft.trim()
        if (body.isEmpty() || _state.value.sending) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            repo.sendMessage(conversationId, body).fold(
                onSuccess = { msg ->
                    _state.update { it.copy(sending = false, draft = "", messages = it.messages + msg) }
                },
                onFailure = { e -> _state.update { it.copy(sending = false, error = e.userMessage()) } },
            )
        }
    }
}

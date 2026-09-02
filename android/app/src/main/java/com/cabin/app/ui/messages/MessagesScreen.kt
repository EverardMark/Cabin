package com.cabin.app.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.data.model.Conversation
import com.cabin.app.data.model.Message
import com.cabin.app.ui.common.FullScreenLoading
import com.cabin.app.ui.common.FullScreenMessage
import com.cabin.app.ui.common.NetworkImage
import com.cabin.app.ui.common.VerificationBadge
import com.cabin.app.util.Format

@Composable
fun MessagesScreen(
    onOpenConversation: (String) -> Unit,
    viewModel: ConversationsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.loading && state.conversations.isEmpty() -> FullScreenLoading()
        state.conversations.isEmpty() -> FullScreenMessage(
            title = "No messages yet",
            message = state.error
                ?: "Message a poster from any listing and the thread will show up here.",
            actionLabel = "Refresh",
            onAction = viewModel::refresh,
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.conversations, key = { it.id }) { conversation ->
                ConversationRow(conversation) { onOpenConversation(conversation.id) }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        NetworkImage(
            url = conversation.listing?.images?.firstOrNull()?.url,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.counterparty?.name ?: "Conversation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (conversation.counterparty?.isVerified == true) {
                    Spacer(Modifier.width(6.dp))
                    VerificationBadge(conversation.counterparty.verificationStatus, compact = true)
                }
            }
            conversation.listing?.title?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            conversation.lastMessage?.let {
                Text(
                    it.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Format.relative(conversation.lastMessageAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (conversation.unreadCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    conversation.unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun ChatScreen(
    conversationId: String,
    onOpenListing: (String) -> Unit,
    viewModel: ChatViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        state.conversation?.listing?.let { listing ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                NetworkImage(
                    url = listing.images.firstOrNull()?.url,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(listing.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Text(
                        Format.price(listing.price, listing.listingType),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                VerificationBadge(listing.verificationStatus, compact = true)
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (state.loading && state.messages.isEmpty()) {
                FullScreenLoading()
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(message, message.senderId == viewModel.currentUserId)
                    }
                }
            }
        }

        state.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Column(modifier = Modifier.padding(12.dp)) {
            // A standing reminder: upfront-payment requests were the most common
            // scam pattern respondents described.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    " Never send a deposit before viewing the property in person.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChange,
                    placeholder = { Text("Message…") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::send, enabled = state.draft.isNotBlank() && !state.sending) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isMine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
            Text(
                message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isMine) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Text(
                Format.relative(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

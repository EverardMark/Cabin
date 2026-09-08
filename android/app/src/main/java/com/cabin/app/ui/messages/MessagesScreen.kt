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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.data.model.Conversation
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.Message
import com.cabin.app.ui.common.AppMark
import com.cabin.app.ui.common.CircleButton
import com.cabin.app.ui.common.SoftAvatarView
import com.cabin.app.ui.common.SoftEmpty
import com.cabin.app.ui.common.SoftError
import com.cabin.app.ui.common.SoftHeader
import com.cabin.app.ui.common.SoftLoading
import com.cabin.app.ui.common.SoftPhoto
import com.cabin.app.ui.common.SoftRadius
import com.cabin.app.ui.common.SoftRow
import com.cabin.app.ui.common.SoftTile
import com.cabin.app.ui.common.VTag
import com.cabin.app.ui.common.softShadowRow
import com.cabin.app.ui.common.softShadowTab
import com.cabin.app.ui.theme.SoftAccent
import com.cabin.app.ui.theme.SoftGreen
import com.cabin.app.ui.theme.SoftInk
import com.cabin.app.ui.theme.SoftLabel
import com.cabin.app.ui.theme.SoftMuted
import com.cabin.app.ui.theme.SoftSecondary
import com.cabin.app.ui.theme.SoftText
import com.cabin.app.ui.theme.SoftType
import com.cabin.app.ui.theme.soft
import com.cabin.app.util.Format

/** In-app chat — the survey's second-most requested feature (59%). */
@Composable
fun MessagesScreen(
    onOpenConversation: (String) -> Unit,
    viewModel: ConversationsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SoftHeader(leading = { AppMark() }, title = { Text("Messages", style = SoftType.screenTitle) })
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading && state.conversations.isEmpty() -> item { SoftLoading() }
                state.conversations.isEmpty() -> item {
                    SoftEmpty(
                        Icons.Outlined.ChatBubbleOutline, "No messages yet",
                        state.error ?: "Message a poster from any listing and the thread will show up here.",
                        actionLabel = "Refresh", onAction = viewModel::refresh,
                    )
                }
                else -> items(state.conversations, key = { it.id }) { conversation ->
                    ConversationRow(conversation) { onOpenConversation(conversation.id) }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    SoftRow(onClick = onClick) {
        SoftAvatarView(conversation.counterparty?.name ?: "?", size = 48.dp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(conversation.counterparty?.name ?: "Conversation", style = SoftType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (conversation.counterparty?.isVerified == true) {
                    Icon(Icons.Outlined.Verified, contentDescription = "Verified", tint = SoftInk, modifier = Modifier.size(14.dp))
                }
            }
            conversation.listing?.title?.let { Text(it, style = SoftType.footnote, color = SoftSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            conversation.lastMessage?.let { Text(it.body, style = SoftType.caption, color = SoftLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 4.dp)) {
            Text(Format.relative(conversation.lastMessageAt), style = soft(12), color = SoftMuted)
            if (conversation.unreadCount > 0) VTag(conversation.unreadCount.toString(), icon = null, tint = SoftAccent)
        }
    }
}

@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
    viewModel: ChatViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val counterpart = state.conversation?.counterparty

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex + 1)
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        SoftHeader(
            leading = { CircleButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Back", onClick = onBack) },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(counterpart?.name ?: "Chat", style = SoftType.screenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (counterpart?.isVerified == true) SoftGreen else SoftMuted))
                        Text(
                            (if (counterpart?.isVerified == true) "Verified " else "Unverified ") + (if (counterpart?.isAgent == true) "agent" else "account"),
                            style = SoftType.footnote, color = SoftSecondary,
                        )
                    }
                }
            },
            trailing = {
                state.conversation?.listing?.let { listing ->
                    CircleButton(Icons.Outlined.Home, "Open listing", onClick = { onOpenListing(listing.id) })
                } ?: Spacer(Modifier.size(48.dp))
            },
        )

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.conversation?.listing?.let { listing -> item(key = "facts") { ListingFactsCard(listing) } }
                if (state.loading && state.messages.isEmpty()) item { SoftLoading() }
                items(state.messages, key = { it.id }) { message ->
                    val index = state.messages.indexOf(message)
                    val mine = message.senderId == viewModel.currentUserId
                    val previousMine = index <= 0 || state.messages[index - 1].senderId == viewModel.currentUserId
                    MessageBubble(message, mine, showAvatar = !mine && previousMine, name = counterpart?.name.orEmpty())
                }
            }
        }

        SoftError(state.error, modifier = Modifier.padding(horizontal = 20.dp))
        Composer(state.draft, viewModel::onDraftChange, viewModel::send, sending = state.sending)
    }
}

@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit, sending: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        // A standing reminder: upfront-payment requests were the most common scam pattern respondents described.
        Text("Never send a deposit before viewing the property in person.", style = soft(12), color = SoftSecondary, modifier = Modifier.padding(horizontal = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val shape = RoundedCornerShape(34.dp)
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .softShadowTab(shape)
                    .clip(shape)
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                if (draft.isEmpty()) Text("Write a message", style = soft(17), color = SoftMuted)
                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    textStyle = soft(17),
                    maxLines = 4,
                    cursorBrush = SolidColor(SoftInk),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CircleButton(
                Icons.AutoMirrored.Outlined.Send, "Send", onClick = onSend, size = 56.dp, inverted = true,
                enabled = draft.isNotBlank() && !sending,
            )
        }
    }
}

/** The "here is how it checks out" card at the top of a thread. */
@Composable
private fun ListingFactsCard(listing: Listing) {
    val shape = RoundedCornerShape(SoftRadius.bubble)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().softShadowRow(shape).clip(shape).background(Color.White).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 6.dp).padding(top = 4.dp)) {
            SoftPhoto(url = listing.images.firstOrNull()?.url, radius = 12.dp, modifier = Modifier.size(44.dp))
            Column {
                Text(listing.title, style = soft(16, FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Format.price(listing.price, listing.listingType), style = SoftType.caption, color = SoftSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftTile(Icons.Outlined.VerifiedUser, "Trust score", listing.verificationScore.toString(), compact = true, modifier = Modifier.weight(1f))
            SoftTile(Icons.Outlined.CalendarMonth, "Confirmed", Format.confirmedAgo(listing.lastConfirmedAt, listing.createdAt), compact = true, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isMine: Boolean, showAvatar: Boolean, name: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!isMine) {
            if (showAvatar) SoftAvatarView(name, size = 40.dp) else Spacer(Modifier.width(40.dp))
        }
        val shape = RoundedCornerShape(SoftRadius.bubble)
        Text(
            message.body,
            style = SoftType.bodyLight,
            color = SoftText,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .softShadowRow(shape)
                .clip(shape)
                .background(Color.White)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        )
    }
}

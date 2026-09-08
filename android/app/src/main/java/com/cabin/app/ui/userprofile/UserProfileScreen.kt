package com.cabin.app.ui.userprofile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cabin.app.data.model.PublicProfile
import com.cabin.app.data.model.Review
import com.cabin.app.data.model.Verification
import com.cabin.app.ui.common.CircleButton
import com.cabin.app.ui.common.MessageDialog
import com.cabin.app.ui.common.OTag
import com.cabin.app.ui.common.SoftAvatarView
import com.cabin.app.ui.common.SoftCard
import com.cabin.app.ui.common.SoftEmpty
import com.cabin.app.ui.common.SoftHeader
import com.cabin.app.ui.common.SoftLink
import com.cabin.app.ui.common.SoftLoading
import com.cabin.app.ui.common.SoftRow
import com.cabin.app.ui.common.VTag
import com.cabin.app.ui.theme.SoftClay
import com.cabin.app.ui.theme.SoftMuted
import com.cabin.app.ui.theme.SoftSecondary
import com.cabin.app.ui.theme.SoftTextSoft
import com.cabin.app.ui.theme.SoftType
import com.cabin.app.ui.theme.soft
import com.cabin.app.util.Format
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Someone else's public profile: who they are, whether they're verified, and what people said. */
@Composable
fun UserProfileScreen(onBack: () -> Unit, viewModel: UserProfileViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showReviewDialog by remember { mutableStateOf(false) }
    val profile = state.profile

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        SoftHeader(
            leading = { CircleButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Back", onClick = onBack) },
            title = { Text(profile?.name ?: "Profile", style = SoftType.screenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading && profile == null -> item { SoftLoading() }
                profile == null -> item { SoftEmpty(Icons.Outlined.PersonOff, "Couldn't load profile", state.error ?: "", actionLabel = "Retry", onAction = viewModel::refresh) }
                else -> {
                    item { ProfileHeader(profile) }
                    if (profile.bio.isNotBlank()) {
                        item { SoftCard { Text(profile.bio, style = SoftType.bodyLight, color = SoftTextSoft) } }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp).padding(top = 6.dp)) {
                            Text("Reviews", style = SoftType.small, color = SoftSecondary, modifier = Modifier.weight(1f))
                            if (!viewModel.isSelf) SoftLink("Write a review", onClick = { showReviewDialog = true })
                        }
                    }
                    if (state.reviews.isEmpty()) {
                        item {
                            Text(
                                "No reviews yet. Reviews can only be written by someone who completed a viewing with this person.",
                                style = SoftType.caption, color = SoftSecondary, modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    } else {
                        items(state.reviews, key = { it.id }) { ReviewRow(it) }
                    }
                }
            }
        }
    }

    if (showReviewDialog) {
        LeaveReviewDialog(
            userName = profile?.name ?: "",
            submitting = state.submitting,
            onDismiss = { showReviewDialog = false },
            onSubmit = { rating, comment -> viewModel.submitReview(rating, comment); showReviewDialog = false },
        )
    }
    state.reviewMessage?.let { MessageDialog("Review", it, viewModel::clearReviewMessage) }
}

@Composable
private fun ProfileHeader(profile: PublicProfile) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SoftAvatarView(profile.name, size = 72.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(profile.name, style = SoftType.screenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (profile.isAgent) "Real estate agent" else "Member since ${memberSince(profile.createdAt)}",
                        style = SoftType.small, color = SoftSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (profile.verificationStatus == Verification.VERIFIED) VTag("Account verified") else OTag("Not verified")
                        if (profile.ratingCount > 0) OTag("★ %.1f · %d".format(profile.ratingAvg, profile.ratingCount))
                    }
                }
            }
            if (profile.verificationStatus != Verification.VERIFIED) {
                Text("This account hasn't completed identity verification. View in person before paying anything.", style = SoftType.footnote, color = SoftClay)
            }
        }
    }
}

private fun memberSince(raw: String): String =
    Format.instant(raw)?.atZone(ZoneId.systemDefault())?.let { DateTimeFormatter.ofPattern("MMM yyyy").format(it) } ?: "—"

@Composable
private fun ReviewRow(review: Review) {
    SoftRow {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).padding(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("★".repeat(review.rating.coerceIn(1, 5)), style = soft(14, androidx.compose.ui.text.font.FontWeight.Normal), modifier = Modifier.weight(1f))
                Text(Format.relative(review.createdAt), style = soft(12), color = SoftMuted)
            }
            if (review.comment.isNotBlank()) Text(review.comment, style = SoftType.small, color = SoftTextSoft)
            review.author?.let { Text("— ${it.name}", style = SoftType.footnote, color = SoftSecondary) }
        }
    }
}

@Composable
private fun LeaveReviewDialog(userName: String, submitting: Boolean, onDismiss: () -> Unit, onSubmit: (Int, String) -> Unit) {
    var rating by remember { mutableFloatStateOf(5f) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Write a review") },
        text = {
            Column {
                Text("Rating: ${rating.toInt()} / 5", style = MaterialTheme.typography.labelLarge)
                Slider(value = rating, onValueChange = { rating = it }, valueRange = 1f..5f, steps = 3)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("What was it like dealing with $userName?") }, minLines = 2)
                Spacer(Modifier.height(8.dp))
                Text("You can only review someone after completing a viewing with them, which is what keeps these ratings meaningful.", style = MaterialTheme.typography.labelSmall, color = SoftSecondary)
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(rating.toInt(), comment) }, enabled = !submitting) { Text("Post") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

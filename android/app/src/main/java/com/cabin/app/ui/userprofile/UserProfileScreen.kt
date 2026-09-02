package com.cabin.app.ui.userprofile

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cabin.app.data.model.PublicProfile
import com.cabin.app.data.model.Review
import com.cabin.app.data.model.Verification
import com.cabin.app.ui.common.AgentBadge
import com.cabin.app.ui.common.FullScreenLoading
import com.cabin.app.ui.common.FullScreenMessage
import com.cabin.app.ui.common.RatingStars
import com.cabin.app.ui.common.VerificationBadge
import com.cabin.app.util.Format

@Composable
fun UserProfileScreen(viewModel: UserProfileViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showReviewDialog by remember { mutableStateOf(false) }

    when {
        state.loading && state.profile == null -> FullScreenLoading()
        state.profile == null -> FullScreenMessage(
            title = "Couldn't load profile",
            message = state.error,
            actionLabel = "Retry",
            onAction = viewModel::refresh,
        )
        else -> {
            val profile = state.profile!!
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { ProfileHeader(profile) }

                if (profile.bio.isNotBlank()) {
                    item { Text(profile.bio, style = MaterialTheme.typography.bodyMedium) }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Reviews",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (!viewModel.isSelf) {
                            TextButton(onClick = { showReviewDialog = true }) { Text("Write a review") }
                        }
                    }
                }

                if (state.reviews.isEmpty()) {
                    item {
                        Text(
                            "No reviews yet. Reviews can only be written by someone who completed a viewing with this person.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.reviews, key = { it.id }) { ReviewCard(it) }
                }
            }
        }
    }

    if (showReviewDialog) {
        LeaveReviewDialog(
            userName = state.profile?.name ?: "",
            submitting = state.submitting,
            onDismiss = { showReviewDialog = false },
            onSubmit = { rating, comment ->
                viewModel.submitReview(rating, comment)
                showReviewDialog = false
            },
        )
    }

    state.reviewMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearReviewMessage,
            title = { Text("Review") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearReviewMessage) { Text("OK") } },
        )
    }
}

@Composable
private fun ProfileHeader(profile: PublicProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            ) {
                Text(
                    profile.name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString(""),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (profile.isAgent) {
                        Spacer(Modifier.width(6.dp))
                        AgentBadge()
                    }
                }
                VerificationBadge(profile.verificationStatus)
                RatingStars(profile.ratingAvg, profile.ratingCount)
            }
        }
        if (profile.verificationStatus != Verification.VERIFIED) {
            Text(
                "This account hasn't completed identity verification.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun ReviewCard(review: Review) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RatingStars(review.rating.toDouble(), 1, modifier = Modifier.weight(1f))
            Text(
                Format.relative(review.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (review.comment.isNotBlank()) {
            Text(review.comment, style = MaterialTheme.typography.bodySmall)
        }
        review.author?.let {
            Text(
                "— ${it.name}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LeaveReviewDialog(
    userName: String,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit,
) {
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
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("What was it like dealing with $userName?") },
                    minLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "You can only review someone after completing a viewing with them, which is what keeps these ratings meaningful.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(rating.toInt(), comment) },
                enabled = !submitting,
            ) { Text("Post") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

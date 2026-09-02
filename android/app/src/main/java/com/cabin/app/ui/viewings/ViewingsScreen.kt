package com.cabin.app.ui.viewings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.data.model.ViewingRequest
import com.cabin.app.ui.common.FullScreenLoading
import com.cabin.app.ui.common.FullScreenMessage
import com.cabin.app.ui.common.NetworkImage
import com.cabin.app.ui.common.StatusChip
import com.cabin.app.util.Format

@Composable
fun ViewingsScreen(
    onOpenListing: (String) -> Unit,
    viewModel: ViewingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.loading && state.viewings.isEmpty() -> FullScreenLoading()
        state.viewings.isEmpty() -> FullScreenMessage(
            title = "No viewings booked",
            message = state.error
                ?: "Request a viewing from any listing and it will show up here.",
            actionLabel = "Refresh",
            onAction = viewModel::refresh,
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.viewings, key = { it.id }) { viewing ->
                ViewingRow(
                    viewing = viewing,
                    isOwner = viewing.ownerId == viewModel.currentUserId,
                    onOpenListing = { onOpenListing(viewing.listingId) },
                    onUpdate = { status -> viewModel.update(viewing.id, status) },
                )
            }
        }
    }
}

@Composable
private fun ViewingRow(
    viewing: ViewingRequest,
    isOwner: Boolean,
    onOpenListing: () -> Unit,
    onUpdate: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(
                url = viewing.listing?.images?.firstOrNull()?.url,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    viewing.listing?.title ?: "Listing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    Format.dateTime(viewing.scheduledFor),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (isOwner) "Requested by ${viewing.requester?.name ?: "a buyer"}"
                    else "With ${viewing.owner?.name ?: "the poster"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            StatusChip(viewing.status)
        }

        if (viewing.note.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "“${viewing.note}”",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (viewing.responseNote.isNotBlank()) {
            Text(
                "Reply: ${viewing.responseNote}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The owner drives the lifecycle; either side can cancel.
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isOwner && viewing.status == "requested") {
                OutlinedButton(onClick = { onUpdate("confirmed") }) { Text("Accept") }
                OutlinedButton(onClick = { onUpdate("declined") }) { Text("Decline") }
            }
            if (isOwner && viewing.status == "confirmed") {
                OutlinedButton(onClick = { onUpdate("completed") }) { Text("Mark completed") }
            }
            if (viewing.status == "requested" || viewing.status == "confirmed") {
                OutlinedButton(onClick = { onUpdate("cancelled") }) { Text("Cancel") }
            }
            OutlinedButton(onClick = onOpenListing) { Text("View listing") }
        }
    }
}

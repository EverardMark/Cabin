package com.cabin.app.ui.viewings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.data.model.ViewingRequest
import com.cabin.app.ui.common.AppMark
import com.cabin.app.ui.common.OTag
import com.cabin.app.ui.common.SoftEmpty
import com.cabin.app.ui.common.SoftHeader
import com.cabin.app.ui.common.SoftLoading
import com.cabin.app.ui.common.SoftRow
import com.cabin.app.ui.common.SoftSmallButton
import com.cabin.app.ui.common.VTag
import com.cabin.app.ui.common.softClick
import com.cabin.app.ui.theme.SoftSecondary
import com.cabin.app.ui.theme.SoftTile
import com.cabin.app.ui.theme.SoftType
import com.cabin.app.ui.theme.soft
import com.cabin.app.util.Format
import java.time.Instant

/** Viewing appointments — 57% of respondents wanted in-app scheduling. */
@Composable
fun ViewingsScreen(
    onOpenListing: (String) -> Unit,
    viewModel: ViewingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = Instant.now()
    val (upcoming, past) = state.viewings.partition { v ->
        val at = Format.instant(v.scheduledFor)
        at != null && at.isAfter(now) && v.status != "cancelled" && v.status != "declined"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SoftHeader(leading = { AppMark() }, title = { Text("Viewings", style = SoftType.screenTitle) })
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading && state.viewings.isEmpty() -> item { SoftLoading() }
                state.viewings.isEmpty() -> item {
                    SoftEmpty(
                        Icons.Outlined.CalendarMonth, "No viewings booked",
                        state.error ?: "Request a viewing from any listing and it will show up here.",
                        actionLabel = "Refresh", onAction = viewModel::refresh,
                    )
                }
                else -> {
                    if (upcoming.isNotEmpty()) {
                        item { SectionLabel("Upcoming") }
                        items(upcoming, key = { it.id }) { v -> ViewingRow(v, viewModel, onOpenListing) }
                    }
                    if (past.isNotEmpty()) {
                        item { SectionLabel("Past", modifier = Modifier.padding(top = 8.dp)) }
                        items(past, key = { it.id }) { v -> ViewingRow(v, viewModel, onOpenListing) }
                    }
                    item {
                        Text(
                            "Viewings auto-complete 24 hours after their slot; a completed viewing unlocks leaving a review.",
                            style = SoftType.footnote, color = SoftSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp).padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = SoftType.small, color = SoftSecondary, modifier = modifier.padding(horizontal = 8.dp))
}

/** Date tile, title, time · who, inline Accept / Decline for owners, status tag. */
@Composable
private fun ViewingRow(viewing: ViewingRequest, viewModel: ViewingsViewModel, onOpenListing: (String) -> Unit) {
    val isOwner = viewing.ownerId == viewModel.currentUserId
    val (day, month) = Format.dayAndMonth(viewing.scheduledFor)
    val who = if (isOwner) "${viewing.requester?.name ?: "A buyer"} asked" else "with ${viewing.owner?.name ?: "the poster"}"

    SoftRow {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(SoftTile),
        ) {
            Text(day, style = soft(24))
            Text(month, style = soft(12), color = SoftSecondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(
                viewing.listing?.title ?: "Listing", style = SoftType.body, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.softClick { onOpenListing(viewing.listingId) },
            )
            Text("${Format.time(viewing.scheduledFor)} · $who", style = SoftType.caption, color = SoftSecondary, maxLines = 2)
            if (viewing.note.isNotBlank()) Text("“${viewing.note}”", style = SoftType.footnote, color = SoftSecondary, maxLines = 2)
            if (viewing.responseNote.isNotBlank()) Text("Reply: ${viewing.responseNote}", style = SoftType.footnote, color = SoftSecondary, maxLines = 2)

            // The owner drives the lifecycle; either side can cancel.
            val actions: List<Pair<String, Pair<Boolean, String>>> = when {
                isOwner && viewing.status == "requested" -> listOf("Accept" to (true to "confirmed"), "Decline" to (false to "declined"))
                isOwner && viewing.status == "confirmed" -> listOf("Mark completed" to (true to "completed"), "Cancel" to (false to "cancelled"))
                viewing.status == "requested" || viewing.status == "confirmed" -> listOf("Cancel" to (false to "cancelled"))
                else -> emptyList()
            }
            if (actions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    actions.forEach { (label, spec) ->
                        SoftSmallButton(label, onClick = { viewModel.update(viewing.id, spec.second) }, primary = spec.first)
                    }
                }
            }
        }
        when (viewing.status) {
            "confirmed" -> VTag("Confirmed")
            "completed" -> VTag("Completed")
            "requested" -> OTag("Pending")
            "declined" -> OTag("Declined")
            "cancelled" -> OTag("Cancelled")
            else -> OTag(Format.capitalize(viewing.status))
        }
    }
}

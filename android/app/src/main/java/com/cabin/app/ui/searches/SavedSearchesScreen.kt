package com.cabin.app.ui.searches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.data.model.SavedSearch
import com.cabin.app.ui.common.CircleButton
import com.cabin.app.ui.common.SoftChevron
import com.cabin.app.ui.common.SoftEmpty
import com.cabin.app.ui.common.SoftHeader
import com.cabin.app.ui.common.SoftLoading
import com.cabin.app.ui.common.SoftRow
import com.cabin.app.ui.common.VTag
import com.cabin.app.ui.listings.CompactListingCard
import com.cabin.app.ui.theme.SoftAccent
import com.cabin.app.ui.theme.SoftSecondary
import com.cabin.app.ui.theme.SoftType
import com.cabin.app.util.Format

/** Saved searches with a new-match count — the "alerts" ask from the survey's free-text answers. */
@Composable
fun SavedSearchesScreen(
    onOpenListing: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SavedSearchesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open = state.openSearch

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        SoftHeader(
            leading = {
                CircleButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Back", onClick = { if (open != null) viewModel.closeResults() else onBack() })
            },
            title = { Text(open?.name ?: "Saved searches", style = SoftType.screenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(if (open != null) 16.dp else 12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (open != null) {
                when {
                    state.resultsLoading -> item { SoftLoading() }
                    state.results.isEmpty() -> item { SoftEmpty(Icons.Outlined.Search, "Nothing matches right now", "We'll keep counting new matches for you.") }
                    else -> items(state.results, key = { it.id }) { listing -> CompactListingCard(listing, onClick = { onOpenListing(listing.id) }) }
                }
            } else {
                when {
                    state.loading && state.searches.isEmpty() -> item { SoftLoading() }
                    state.searches.isEmpty() -> item {
                        SoftEmpty(
                            Icons.Outlined.NotificationsOff, "No saved searches",
                            state.error ?: "Open the search panel on Browse, set your filters, then tap “Save this search”.",
                            actionLabel = "Refresh", onAction = viewModel::refresh,
                        )
                    }
                    else -> {
                        items(state.searches, key = { it.id }) { search ->
                            SavedSearchRow(search, onOpen = { viewModel.open(search) }, onDelete = { viewModel.delete(search) })
                        }
                        item { Text("Tap the bin on a search to delete it.", style = SoftType.footnote, color = SoftSecondary, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedSearchRow(search: SavedSearch, onOpen: () -> Unit, onDelete: () -> Unit) {
    SoftRow(onClick = onOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(search.name, style = SoftType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(readableQuery(search.query), style = SoftType.caption, color = SoftSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (search.newMatches > 0) VTag("${search.newMatches} new", icon = null, tint = SoftAccent)
        CircleButton(Icons.Outlined.Delete, "Delete saved search", onClick = onDelete, size = 36.dp)
        SoftChevron()
    }
}

/** Renders "city=Muntinlupa&verified_only=true" as something a person reads. */
private fun readableQuery(query: String): String =
    query.split("&").mapNotNull { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val (key, raw) = parts
        val value = java.net.URLDecoder.decode(raw, "UTF-8")
        when (key) {
            "q" -> "“$value”"
            "city" -> value
            "listing_type" -> if (value == "rent") "for rent" else "for sale"
            "property_type" -> Format.capitalize(value)
            "min_price" -> value.toLongOrNull()?.let { "from ${Format.compactPrice(it)}" }
            "max_price" -> value.toLongOrNull()?.let { "up to ${Format.compactPrice(it)}" }
            "min_bedrooms" -> "$value+ beds"
            "verified_only" -> if (value == "true") "verified only" else null
            "exclude_stale" -> if (value == "true") "recently confirmed" else null
            else -> null
        }
    }.joinToString(" · ")

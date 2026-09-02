package com.cabin.app.ui.searches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.data.model.SavedSearch
import com.cabin.app.ui.common.FullScreenLoading
import com.cabin.app.ui.common.FullScreenMessage
import com.cabin.app.ui.listings.ListingCard
import com.cabin.app.util.Format

@Composable
fun SavedSearchesScreen(
    onOpenListing: (String) -> Unit,
    viewModel: SavedSearchesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val open = state.openSearch
    if (open != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(open.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::closeResults) { Text("Back") }
            }
            when {
                state.resultsLoading -> FullScreenLoading()
                state.results.isEmpty() -> FullScreenMessage(
                    title = "Nothing matches right now",
                    message = "We'll keep counting new matches for you.",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.results, key = { it.id }) { listing ->
                        ListingCard(listing = listing, onClick = { onOpenListing(listing.id) })
                    }
                }
            }
        }
        return
    }

    when {
        state.loading && state.searches.isEmpty() -> FullScreenLoading()
        state.searches.isEmpty() -> FullScreenMessage(
            title = "No saved searches",
            message = state.error
                ?: "Set your filters when browsing, then use the menu to save the search.",
            actionLabel = "Refresh",
            onAction = viewModel::refresh,
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.searches, key = { it.id }) { search ->
                SavedSearchRow(
                    search = search,
                    onOpen = { viewModel.open(search) },
                    onDelete = { viewModel.delete(search) },
                )
            }
        }
    }
}

@Composable
private fun SavedSearchRow(search: SavedSearch, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(search.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                readableQuery(search.query),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        if (search.newMatches > 0) {
            Text(
                "${search.newMatches} new",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        TextButton(onClick = onOpen) { Text("Open") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete saved search")
        }
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

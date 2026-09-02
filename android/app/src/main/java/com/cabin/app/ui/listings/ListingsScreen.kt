package com.cabin.app.ui.listings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.data.model.Listing
import com.cabin.app.ui.common.FullScreenLoading
import com.cabin.app.ui.common.FullScreenMessage
import com.cabin.app.ui.common.NetworkImage
import com.cabin.app.ui.common.Pill
import com.cabin.app.ui.common.RatingStars
import com.cabin.app.ui.common.VerificationBadge
import com.cabin.app.util.Format

private data class PropertyFilter(val label: String, val value: String?)

private val listingTypeFilters = listOf(
    PropertyFilter("All", null),
    PropertyFilter("Buy", "sale"),
    PropertyFilter("Rent", "rent"),
)

private val propertyTypeFilters = listOf(
    PropertyFilter("Any type", null),
    PropertyFilter("House", "house"),
    PropertyFilter("Apartment", "apartment"),
    PropertyFilter("Condo", "condo"),
    PropertyFilter("Townhouse", "townhouse"),
    PropertyFilter("Land", "land"),
)

private val sortOptions = listOf(
    "recent" to "Newest",
    "trusted" to "Most trusted",
    "price_asc" to "Price: low to high",
    "price_desc" to "Price: high to low",
)

@Composable
fun ListingsScreen(
    onOpenListing: (String) -> Unit,
    onOpenMap: () -> Unit,
    viewModel: ListingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var searchName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Find your place",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenMap) {
                    Icon(Icons.Outlined.Map, contentDescription = "Map view")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Sort and filter options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        sortOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(if (state.filters.sort == value) "✓ $label" else label) },
                                onClick = {
                                    viewModel.setSort(value)
                                    menuOpen = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (state.filters.excludeStale) "✓ Hide unconfirmed listings"
                                    else "Hide unconfirmed listings"
                                )
                            },
                            onClick = {
                                viewModel.setExcludeStale(!state.filters.excludeStale)
                                menuOpen = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Save this search") },
                            onClick = {
                                searchName = viewModel.defaultSearchName()
                                showSaveDialog = true
                                menuOpen = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.filters.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search city, title, address…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            VerifiedOnlyToggle(
                checked = state.filters.verifiedOnly,
                onChange = viewModel::setVerifiedOnly,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(listingTypeFilters) { filter ->
                FilterChip(
                    selected = state.filters.listingType == filter.value,
                    onClick = { viewModel.setListingType(filter.value) },
                    label = { Text(filter.label) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(propertyTypeFilters) { filter ->
                FilterChip(
                    selected = state.filters.propertyType == filter.value,
                    onClick = { viewModel.setPropertyType(filter.value) },
                    label = { Text(filter.label) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.loading && state.listings.isEmpty() -> FullScreenLoading()
                state.error != null && state.listings.isEmpty() -> FullScreenMessage(
                    title = "Couldn't load listings",
                    message = state.error,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )
                state.listings.isEmpty() -> FullScreenMessage(
                    title = "No listings found",
                    message = if (state.filters.verifiedOnly) {
                        "Nothing verified matches these filters yet. Turn off “Verified only” to include listings still being screened."
                    } else {
                        "Try adjusting your search or filters."
                    },
                    actionLabel = if (state.filters.verifiedOnly) "Include unverified" else null,
                    onAction = if (state.filters.verifiedOnly) {
                        { viewModel.setVerifiedOnly(false) }
                    } else null,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.listings, key = { it.id }) { listing ->
                        ListingCard(listing = listing, onClick = { onOpenListing(listing.id) })
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save this search") },
            text = {
                Column {
                    Text("We'll show you how many new listings match when you come back.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchName,
                        onValueChange = { searchName = it },
                        singleLine = true,
                        label = { Text("Name") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveCurrentSearch(searchName)
                    showSaveDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            },
        )
    }

    state.savedMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearSavedMessage,
            title = { Text("Saved") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearSavedMessage) { Text("OK") } },
        )
    }
}

/**
 * The verified-only switch sits above every other filter: it is the single most
 * requested feature in the survey (79%), and it defaults to on.
 */
@Composable
private fun VerifiedOnlyToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Verified listings only",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Hides listings that haven't passed screening",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ListingCard(listing: Listing, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            NetworkImage(
                url = listing.images.firstOrNull()?.url,
                contentDescription = listing.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f),
            )
            // The trust badge leads, because trust is what buyers said they
            // decide on first.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                VerificationBadge(listing.verificationStatus)
            }
            Pill(
                text = Format.price(listing.price, listing.listingType),
                background = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            )
            Pill(
                text = if (listing.listingType == "rent") "For rent" else "For sale",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            )
        }
        Column(modifier = Modifier.padding(14.dp)) {
            Text(listing.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                listOfNotNull(listing.city.ifBlank { null }, listing.state.ifBlank { null }).joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FeatureText(Format.beds(listing.bedrooms))
                Dot()
                FeatureText(Format.baths(listing.bathrooms))
                Dot()
                FeatureText(Format.area(listing.areaSqft))
            }
            if (Format.isStale(listing.lastConfirmedAt, listing.createdAt)) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.height(14.dp),
                    )
                    Text(
                        " Not confirmed recently",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            listing.owner?.let { owner ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        owner.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(0.dp))
                    Text("  ")
                    RatingStars(owner.ratingAvg, owner.ratingCount)
                }
            }
        }
    }
}

@Composable
private fun FeatureText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
}

@Composable
private fun Dot() {
    Text(
        "•",
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

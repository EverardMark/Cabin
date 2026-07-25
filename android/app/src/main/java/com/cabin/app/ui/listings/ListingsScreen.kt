package com.cabin.app.ui.listings

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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun ListingsScreen(
    onOpenListing: (String) -> Unit,
    viewModel: ListingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Find your place",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search city, title, address…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(listingTypeFilters) { filter ->
                FilterChip(
                    selected = state.listingType == filter.value,
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
                    selected = state.propertyType == filter.value,
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
                    message = "Try adjusting your search or filters.",
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
            Text(
                listing.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
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

package com.cabin.app.ui.listings

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.Verification
import com.cabin.app.ui.common.AppMark
import com.cabin.app.ui.common.BookViewingDialog
import com.cabin.app.ui.common.CircleButton
import com.cabin.app.ui.common.MessageDialog
import com.cabin.app.ui.common.OTag
import com.cabin.app.ui.common.SoftCard
import com.cabin.app.ui.common.SoftChip
import com.cabin.app.ui.common.SoftEmpty
import com.cabin.app.ui.common.SoftField
import com.cabin.app.ui.common.SoftHeader
import com.cabin.app.ui.common.SoftLink
import com.cabin.app.ui.common.SoftLoading
import com.cabin.app.ui.common.SoftPhoto
import com.cabin.app.ui.common.SoftPrimaryButton
import com.cabin.app.ui.common.SoftRow
import com.cabin.app.ui.common.SoftSecondaryButton
import com.cabin.app.ui.common.SoftTile
import com.cabin.app.ui.common.VTag
import com.cabin.app.ui.common.softClick
import com.cabin.app.ui.theme.SoftClay
import com.cabin.app.ui.theme.SoftDivider
import com.cabin.app.ui.theme.SoftInk
import com.cabin.app.ui.theme.SoftLabel
import com.cabin.app.ui.theme.SoftRed
import com.cabin.app.ui.theme.SoftSecondary
import com.cabin.app.ui.theme.SoftText
import com.cabin.app.ui.theme.SoftTextSoft
import com.cabin.app.ui.theme.SoftTile
import com.cabin.app.ui.theme.SoftType
import com.cabin.app.ui.theme.soft
import com.cabin.app.util.Format

private val listingTypeFilters = listOf("All" to null, "Buy" to "sale", "Rent" to "rent")
private val propertyTypeFilters = listOf(
    "Any type" to null, "House" to "house", "Apartment" to "apartment",
    "Condo" to "condo", "Townhouse" to "townhouse", "Land" to "land",
)
private val sortOptions = listOf("recent" to "Newest", "trusted" to "Most trusted", "price_asc" to "Price ↑", "price_desc" to "Price ↓")

/**
 * Home: the verified feed. The first listing gets the big card with price and
 * trust-score tiles; the rest are compact photo cards.
 */
@Composable
fun ListingsScreen(
    onOpenListing: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenMessages: () -> Unit,
    viewModel: ListingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary by com.cabin.app.data.ServiceLocator.repository.summary.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var searchName by remember { mutableStateOf("") }
    var bookingListing by remember { mutableStateOf<Listing?>(null) }

    LaunchedEffect(state.openConversationId) {
        state.openConversationId?.let {
            onOpenConversation(it)
            viewModel.conversationOpened()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SoftHeader(
            leading = { AppMark() },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircleButton(Icons.Outlined.Search, "Search and filters", onClick = { showFilters = !showFilters }, filled = showFilters)
                    CircleButton(Icons.Outlined.ChatBubbleOutline, "Messages", onClick = onOpenMessages, badge = summary.unreadMessages > 0)
                }
            },
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (showFilters) {
                item(key = "filters") {
                    FilterPanel(
                        state = state,
                        viewModel = viewModel,
                        onSave = {
                            searchName = viewModel.defaultSearchName()
                            showSaveDialog = true
                        },
                    )
                }
            }
            when {
                state.loading && state.listings.isEmpty() -> item { SoftLoading() }
                state.error != null && state.listings.isEmpty() -> item {
                    SoftEmpty(Icons.Outlined.WifiOff, "Couldn't load listings", state.error!!, actionLabel = "Retry", onAction = viewModel::refresh)
                }
                state.listings.isEmpty() -> item {
                    SoftEmpty(
                        Icons.Outlined.Search, "No listings found",
                        if (state.filters.verifiedOnly) "Nothing verified matches these filters yet. Turn off “Verified only” to include listings still being screened."
                        else "Try adjusting your search or filters.",
                        actionLabel = if (state.filters.verifiedOnly) "Include unverified" else null,
                        onAction = { viewModel.setVerifiedOnly(false) },
                    )
                }
                else -> itemsIndexed(state.listings, key = { _, l -> l.id }) { index, listing ->
                    if (index == 0) {
                        HeroListingCard(
                            listing = listing,
                            isMine = listing.userId == viewModel.currentUserId,
                            onOpen = { onOpenListing(listing.id) },
                            onMessage = { viewModel.startConversation(listing) },
                            onBook = { bookingListing = listing },
                        )
                    } else {
                        CompactListingCard(listing = listing, onClick = { onOpenListing(listing.id) })
                    }
                }
            }
        }
    }

    bookingListing?.let { listing ->
        BookViewingDialog(
            onDismiss = { bookingListing = null },
            onSubmit = { days, hour, note ->
                viewModel.requestViewing(listing, days, hour, note)
                bookingListing = null
            },
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save this search") },
            text = {
                Column {
                    Text("We'll show you how many new listings match when you come back.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = searchName, onValueChange = { searchName = it }, singleLine = true, label = { Text("Name") })
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveCurrentSearch(searchName); showSaveDialog = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } },
        )
    }

    state.savedMessage?.let { MessageDialog("Saved", it, viewModel::clearSavedMessage) }
    state.actionMessage?.let { MessageDialog("Cabin", it, viewModel::clearActionMessage) }
}

// MARK: Search & filters

@Composable
private fun FilterPanel(state: ListingsUiState, viewModel: ListingsViewModel, onSave: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SoftField(
            value = state.filters.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = "Search city, title, address",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.refresh() }),
            leading = { Icon(Icons.Outlined.Search, contentDescription = null, tint = SoftLabel, modifier = Modifier.size(20.dp)) },
        )

        // The verified-only switch is the survey's most requested feature (79%); it defaults to on.
        SoftRow(fill = SoftTile, shadow = false) {
            Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = SoftTextSoft, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Verified listings only", style = soft(16, FontWeight.Normal))
                Text("Hides listings that haven't passed screening", style = SoftType.footnote, color = SoftSecondary)
            }
            Switch(
                checked = state.filters.verifiedOnly,
                onCheckedChange = viewModel::setVerifiedOnly,
                colors = SwitchDefaults.colors(checkedTrackColor = SoftInk, checkedThumbColor = Color.White),
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listingTypeFilters) { (label, value) ->
                SoftChip(label, selected = state.filters.listingType == value, onClick = { viewModel.setListingType(value) })
            }
            item { Box(Modifier.width(1.dp).height(36.dp).background(SoftDivider).padding(horizontal = 2.dp)) }
            items(propertyTypeFilters) { (label, value) ->
                SoftChip(label, selected = state.filters.propertyType == value, onClick = { viewModel.setPropertyType(value) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sortOptions) { (value, label) ->
                SoftChip(label, selected = state.filters.sort == value, onClick = { viewModel.setSort(value) })
            }
            item {
                SoftChip("Recently confirmed", selected = state.filters.excludeStale, onClick = { viewModel.setExcludeStale(!state.filters.excludeStale) })
            }
        }
        SoftLink("Save this search", onClick = onSave, modifier = Modifier.padding(start = 6.dp))
    }
}

// MARK: Cards

/** Badges on a listing photo: verification first, promotion second and in white so it never reads as trust. */
@Composable
fun PhotoTags(listing: Listing, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        when (listing.verificationStatus) {
            Verification.VERIFIED -> VTag("Verified")
            Verification.FLAGGED -> VTag("Check details", icon = Icons.Outlined.Sell, tint = SoftClay)
            Verification.REJECTED -> VTag("Failed screening", icon = null, tint = SoftRed)
            Verification.PENDING -> VTag("Being screened", icon = null, light = true)
            else -> VTag("Not screened", icon = null, light = true)
        }
        if (listing.isFeatured) VTag("Featured", icon = null, light = true)
    }
}

/** "Muntinlupa, Metro Manila · 2 bd · 2 ba · 820 sqft" */
fun metaLine(listing: Listing): String {
    val place = listOf(listing.city, listing.state).filter { it.isNotBlank() }.joinToString(", ")
    return buildList {
        if (place.isNotBlank()) add(place)
        add(Format.beds(listing.bedrooms))
        add(Format.baths(listing.bathrooms))
        if (listing.areaSqft > 0) add(Format.area(listing.areaSqft))
    }.joinToString(" · ")
}

/** The big first card: photo, title, meta, price + trust tiles, two actions. */
@Composable
fun HeroListingCard(
    listing: Listing,
    isMine: Boolean,
    onOpen: () -> Unit,
    onMessage: () -> Unit,
    onBook: () -> Unit,
) {
    SoftCard {
        Column(modifier = Modifier.softClick(onClick = onOpen)) {
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                SoftPhoto(url = listing.images.firstOrNull()?.url, modifier = Modifier.fillMaxSize())
                PhotoTags(listing, modifier = Modifier.padding(14.dp))
            }
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 22.dp)) {
                Text(listing.title, style = SoftType.heading, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                OTag(if (listing.listingType == "rent") "For rent" else "For sale", filled = true, modifier = Modifier.padding(top = 6.dp))
            }
            Text(metaLine(listing), style = SoftType.small, color = SoftSecondary, modifier = Modifier.padding(top = 8.dp))
            if (Format.isStale(listing.lastConfirmedAt, listing.createdAt)) {
                Text("Not confirmed recently", style = SoftType.caption, color = SoftClay, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 20.dp)) {
            SoftTile(
                Icons.Outlined.Sell, "Asking price",
                Format.compactPrice(listing.price) + if (listing.listingType == "rent") "/mo" else "",
                modifier = Modifier.weight(1f),
            )
            SoftTile(Icons.Outlined.VerifiedUser, "Trust score", listing.verificationScore.toString(), modifier = Modifier.weight(1f))
        }

        if (isMine) {
            // Owners can't message or book themselves; send them to the listing's tools instead.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).softClick(onClick = onOpen).padding(horizontal = 6.dp),
            ) {
                OTag("Your listing", filled = true)
                Spacer(Modifier.weight(1f))
                Text("Manage", style = SoftType.button, color = SoftText)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = SoftText, modifier = Modifier.size(18.dp))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
                SoftPrimaryButton(
                    "Message ${listing.owner?.name?.split(" ")?.firstOrNull() ?: "poster"}",
                    onClick = onMessage, modifier = Modifier.weight(1f),
                )
                SoftSecondaryButton("Book a viewing", onClick = onBook, tint = SoftTile, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Compact card: photo with badge, then title and price. */
@Composable
fun CompactListingCard(listing: Listing, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SoftCard(padding = 12.dp, modifier = modifier.softClick(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            SoftPhoto(url = listing.images.firstOrNull()?.url, modifier = Modifier.fillMaxSize())
            PhotoTags(listing, modifier = Modifier.padding(12.dp))
        }
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(horizontal = 8.dp).padding(top = 14.dp, bottom = 6.dp),
        ) {
            Text(listing.title, style = SoftType.cardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            PriceText(listing.price, listing.listingType)
        }
        if (Format.isStale(listing.lastConfirmedAt, listing.createdAt)) {
            Text("Not confirmed recently", style = SoftType.footnote, color = SoftClay, modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp))
        }
    }
}

/** "₱22K" with a small "/mo" for rentals. */
@Composable
fun PriceText(price: Long, listingType: String, size: Int = 18) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(Format.compactPrice(price), style = soft(size, FontWeight.Normal), maxLines = 1)
        if (listingType == "rent") Text("/mo", style = soft((size * 0.83f).toInt()), color = SoftSecondary)
    }
}

/** Kept for screens that list plain cards (saved-search results, profile). */
@Composable
fun ListingCard(listing: Listing, onClick: () -> Unit, modifier: Modifier = Modifier) =
    CompactListingCard(listing, onClick, modifier)

@Suppress("unused")
private val keep = CircleShape to 0.sp

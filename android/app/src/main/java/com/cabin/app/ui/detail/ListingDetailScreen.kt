package com.cabin.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.PriceComparison
import com.cabin.app.data.model.Verification
import com.cabin.app.ui.common.FullScreenLoading
import com.cabin.app.ui.common.FullScreenMessage
import com.cabin.app.ui.common.NetworkImage
import com.cabin.app.ui.common.Pill
import com.cabin.app.ui.common.PosterCard
import com.cabin.app.ui.common.PrimaryButton
import com.cabin.app.ui.common.StaleWarning
import com.cabin.app.ui.common.TrustPanel
import com.cabin.app.util.Format

private val reportReasons = listOf(
    "fake_listing" to "Fake or doesn't exist",
    "scam" to "Scam — asks for money upfront",
    "wrong_price" to "Price is wrong or misleading",
    "already_taken" to "Already sold or rented",
    "misleading_photos" to "Photos aren't of this property",
    "duplicate" to "Duplicate listing",
    "offensive" to "Offensive content",
    "other" to "Something else",
)

@Composable
fun ListingDetailScreen(
    listingId: String,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: ListingDetailViewModel = viewModel(),
) {
    LaunchedEffect(listingId) { viewModel.load(listingId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Opening a thread navigates once, then clears so back doesn't re-trigger it.
    LaunchedEffect(state.openConversationId) {
        state.openConversationId?.let {
            onOpenConversation(it)
            viewModel.conversationOpened()
        }
    }

    var showReport by remember { mutableStateOf(false) }
    var showBooking by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> FullScreenLoading()
            state.listing == null -> FullScreenMessage(
                title = "Couldn't load listing",
                message = state.error,
                actionLabel = "Back",
                onAction = onBack,
            )
            else -> ListingDetailContent(
                listing = state.listing!!,
                comparison = state.comparison,
                isMine = state.listing!!.userId == viewModel.currentUserId,
                busy = state.busy,
                onBack = onBack,
                onMessage = viewModel::startConversation,
                onBook = { showBooking = true },
                onReport = { showReport = true },
                onConfirm = viewModel::confirmAvailability,
                onOpenProfile = onOpenProfile,
            )
        }
    }

    if (showReport) {
        ReportDialog(
            onDismiss = { showReport = false },
            onSubmit = { reason, details ->
                viewModel.report(reason, details)
                showReport = false
            },
        )
    }

    if (showBooking) {
        BookViewingDialog(
            onDismiss = { showBooking = false },
            onSubmit = { days, hour, note ->
                viewModel.requestViewing(days, hour, note)
                showBooking = false
            },
        )
    }

    state.actionMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearActionMessage,
            title = { Text("Cabin") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearActionMessage) { Text("OK") } },
        )
    }
}

@Composable
private fun ListingDetailContent(
    listing: Listing,
    comparison: PriceComparison?,
    isMine: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onBook: () -> Unit,
    onReport: () -> Unit,
    onConfirm: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box {
            if (listing.images.isNotEmpty()) {
                val pagerState = rememberPagerState(pageCount = { listing.images.size })
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                ) { page ->
                    NetworkImage(
                        url = listing.images[page].url,
                        contentDescription = listing.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (listing.images.size > 1) {
                    Pill(
                        text = "${pagerState.currentPage + 1} / ${listing.images.size}",
                        background = Color.Black.copy(alpha = 0.55f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                    )
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                ) {
                    NetworkImage(url = null, contentDescription = null, modifier = Modifier.fillMaxSize())
                    Text(
                        "No photos yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape),
                onClick = onBack,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(text = if (listing.listingType == "rent") "For rent" else "For sale")
                Spacer(Modifier.size(8.dp))
                Pill(
                    text = Format.capitalize(listing.propertyType),
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                Format.price(listing.price, listing.listingType),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(listing.title, style = MaterialTheme.typography.titleLarge)

            val address = listOfNotNull(
                listing.address.ifBlank { null },
                listing.city.ifBlank { null },
                listing.state.ifBlank { null },
                listing.zipCode.ifBlank { null },
            ).joinToString(", ")
            if (address.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            // Trust comes before the sales copy.
            Spacer(Modifier.height(16.dp))
            TrustPanel(listing)
            if (Format.isStale(listing.lastConfirmedAt, listing.createdAt)) {
                Spacer(Modifier.height(8.dp))
                StaleWarning()
            }

            Spacer(Modifier.height(16.dp))
            FeatureRow(listing)

            if (comparison != null && comparison.sampleSize >= 3) {
                Spacer(Modifier.height(16.dp))
                PriceComparisonCard(comparison)
            }

            if (listing.description.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                Text("About this property", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    listing.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
            }

            listing.owner?.let { owner ->
                Spacer(Modifier.height(20.dp))
                Text("Listed by", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                PosterCard(owner = owner, onClick = { onOpenProfile(owner.id) })
            }

            Spacer(Modifier.height(24.dp))
            if (isMine) {
                if (Format.isStale(listing.lastConfirmedAt, listing.createdAt)) {
                    Text(
                        "Buyers see a warning on listings that haven't been confirmed recently.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = onConfirm,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Confirm it's still available") }
            } else {
                PrimaryButton(
                    text = "Message the poster",
                    onClick = onMessage,
                    loading = busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onBook,
                    enabled = !busy && listing.verificationStatus != Verification.REJECTED,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Request a viewing") }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onReport, modifier = Modifier.fillMaxWidth()) {
                    Text("Report this listing", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Answers "is this price reasonable?" — 46% of respondents asked for this. */
@Composable
private fun PriceComparisonCard(comparison: PriceComparison) {
    val tint = when (comparison.verdict) {
        "below_market" -> MaterialTheme.colorScheme.primary
        "above_market" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val difference = when {
        kotlin.math.abs(comparison.percentDiff) < 1 -> "right at"
        comparison.percentDiff > 0 -> "%.0f%% above".format(comparison.percentDiff)
        else -> "%.0f%% below".format(-comparison.percentDiff)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Price check", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                comparison.verdictLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tint.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Text(
            "Compared with ${comparison.sampleSize} similar listings nearby, this is $difference the median of ${Format.compactPrice(comparison.median)}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            Text(
                Format.compactPrice(comparison.min),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
            Text(
                Format.compactPrice(comparison.max),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** Reporting flow — 57% of respondents had hit a scam with nowhere to say so. */
@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var reason by remember { mutableStateOf(reportReasons.first().first) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report listing") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                reportReasons.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(selected = reason == value, onClick = { reason = value })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Optional details") },
                    minLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Reports go to our moderators. Three open reports send a listing back for re-screening automatically.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(reason, details) }) { Text("Submit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Booking a viewing — 57% asked for in-app scheduling. */
@Composable
private fun BookViewingDialog(onDismiss: () -> Unit, onSubmit: (Long, Int, String) -> Unit) {
    var days by remember { mutableFloatStateOf(1f) }
    var hour by remember { mutableFloatStateOf(14f) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request a viewing") },
        text = {
            Column {
                Text("In ${days.toInt()} day(s), at ${hour.toInt()}:00", style = MaterialTheme.typography.labelLarge)
                Slider(value = days, onValueChange = { days = it }, valueRange = 1f..30f)
                Slider(value = hour, onValueChange = { hour = it }, valueRange = 7f..20f)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note for the poster") },
                    minLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "The poster has to accept before it's confirmed. Never pay anything before you've seen the property.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(days.toLong(), hour.toInt(), note) }) { Text("Request") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FeatureRow(listing: Listing) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Feature(value = Format.beds(listing.bedrooms), label = "Bedrooms")
            Feature(value = Format.baths(listing.bathrooms), label = "Bathrooms")
            Feature(value = Format.area(listing.areaSqft), label = "Area")
        }
    }
}

@Composable
private fun Feature(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

package com.cabin.app.ui.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.Review
import com.cabin.app.data.model.UserSummary
import com.cabin.app.ui.common.FullScreenLoading
import com.cabin.app.ui.common.FullScreenMessage
import com.cabin.app.ui.common.NetworkImage
import com.cabin.app.ui.common.Pill
import com.cabin.app.ui.common.PrimaryButton
import com.cabin.app.ui.common.RatingGold
import com.cabin.app.ui.common.RatingStars
import com.cabin.app.ui.common.StatusPill
import com.cabin.app.ui.common.VerifiedBadge
import com.cabin.app.util.Format
import kotlinx.coroutines.launch

@Composable
fun ListingDetailScreen(
    listingId: String,
    onBack: () -> Unit,
    viewModel: ListingDetailViewModel = viewModel(),
) {
    LaunchedEffect(listingId) { viewModel.load(listingId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Surface status-change failures as a transient toast (content stays visible).
    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearActionError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> FullScreenLoading()
            state.error != null -> FullScreenMessage(
                title = "Couldn't load listing",
                message = state.error,
                actionLabel = "Back",
                onAction = onBack,
            )
            state.listing != null -> ListingDetailContent(state, viewModel, onBack)
        }
    }
}

@Composable
private fun ListingDetailContent(
    state: DetailUiState,
    viewModel: ListingDetailViewModel,
    onBack: () -> Unit,
) {
    val listing = state.listing ?: return
    val context = LocalContext.current
    val isOwner = state.currentUserId != null && listing.userId == state.currentUserId

    var showReview by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }

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
                NetworkImage(
                    url = null,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                )
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
                if (!listing.status.equals("active", ignoreCase = true)) {
                    Spacer(Modifier.size(8.dp))
                    StatusPill(listing.status)
                }
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

            Format.relativeTime(listing.updatedAt)?.let { rel ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "Updated $rel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            FeatureRow(listing)

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
                OwnerCard(owner)
            }

            Spacer(Modifier.height(20.dp))
            ReviewsSection(
                reviews = state.reviews,
                canWrite = !isOwner && listing.owner != null,
                onWriteReview = { showReview = true },
            )

            Spacer(Modifier.height(20.dp))
            if (isOwner) {
                AvailabilitySection(currentStatus = listing.status, onSelect = viewModel::changeStatus)
            } else {
                PrimaryButton(
                    text = "Contact agent",
                    onClick = {
                        Toast.makeText(context, "Contacting ${listing.owner?.name ?: "the agent"}…", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showReport = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Report listing")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    val owner = listing.owner
    if (showReview && owner != null) {
        WriteReviewDialog(
            ownerName = owner.name,
            onDismiss = { showReview = false },
            onSubmit = { rating, comment -> viewModel.submitReview(owner.id, rating, comment) },
        )
    }
    if (showReport) {
        ReportDialog(
            onDismiss = { showReport = false },
            onSubmit = { reason, detail -> viewModel.submitReport(reason, detail) },
        )
    }
}

@Composable
private fun OwnerCard(owner: UserSummary) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        owner.name.firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text("Listed by", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(owner.name, style = MaterialTheme.typography.titleMedium)
                    if (owner.verified) {
                        Spacer(Modifier.size(6.dp))
                        VerifiedBadge()
                    }
                }
                Spacer(Modifier.height(2.dp))
                RatingStars(avg = owner.ratingAvg, count = owner.ratingCount)
            }
        }
    }
}

@Composable
private fun ReviewsSection(reviews: List<Review>, canWrite: Boolean, onWriteReview: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Reviews", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (reviews.isEmpty()) {
            Text(
                "No reviews yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        } else {
            reviews.take(5).forEach { review ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            review.authorName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        Row {
                            repeat(review.rating.coerceIn(0, 5)) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = RatingGold, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                    if (review.comment.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            review.comment,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                )
            }
        }
        if (canWrite) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onWriteReview, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Icon(Icons.Outlined.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Write a review")
            }
        }
    }
}

@Composable
private fun AvailabilitySection(currentStatus: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Manage availability", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Keep your listing honest — mark it Sold or Rented when it's gone so buyers don't chase ghost listings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(8.dp))
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Status: ${Format.statusLabel(currentStatus)}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.UnfoldMore, contentDescription = "Change status")
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf("active", "pending", "sold", "rented", "inactive").forEach { s ->
                    DropdownMenuItem(
                        text = { Text(Format.statusLabel(s)) },
                        onClick = {
                            expanded = false
                            onSelect(s)
                        },
                    )
                }
            }
        }
    }
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

@Composable
private fun WriteReviewDialog(
    ownerName: String,
    onDismiss: () -> Unit,
    onSubmit: suspend (Int, String) -> String?,
) {
    var rating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Review $ownerName", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your rating",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    (1..5).forEach { i ->
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "$i star",
                            tint = if (i <= rating) RatingGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { rating = i }
                                .padding(3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("Share your experience") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { if (!busy) onDismiss() }) { Text("Cancel") }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                val err = onSubmit(rating, comment)
                                busy = false
                                if (err == null) onDismiss() else error = err
                            }
                        },
                        enabled = !busy,
                    ) { Text("Submit") }
                }
            }
        }
    }
}

private val reportReasons = listOf(
    "scam" to "Scam",
    "fake_or_misleading" to "Fake or misleading",
    "already_unavailable" to "Already unavailable",
    "wrong_price" to "Wrong price",
    "duplicate" to "Duplicate",
    "offensive" to "Offensive",
    "other" to "Other",
)

@Composable
private fun ReportDialog(
    onDismiss: () -> Unit,
    onSubmit: suspend (String, String) -> String?,
) {
    var reason by remember { mutableStateOf("scam") }
    var detail by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Text("Report listing", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Help keep Cabin safe from scams and fake posts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(12.dp))
                reportReasons.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { reason = value }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == value, onClick = { reason = value })
                        Spacer(Modifier.size(4.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    placeholder = { Text("Details (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { if (!busy) onDismiss() }) { Text("Cancel") }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                val err = onSubmit(reason, detail)
                                busy = false
                                if (err == null) onDismiss() else error = err
                            }
                        },
                        enabled = !busy,
                    ) { Text("Submit") }
                }
            }
        }
    }
}

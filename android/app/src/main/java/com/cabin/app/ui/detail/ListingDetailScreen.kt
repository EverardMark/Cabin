package com.cabin.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Bathtub
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.data.model.FeaturePlan
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.PriceComparison
import com.cabin.app.data.model.UserSummary
import com.cabin.app.data.model.Verification
import com.cabin.app.ui.common.BookViewingDialog
import com.cabin.app.ui.common.CircleButton
import com.cabin.app.ui.common.MessageDialog
import com.cabin.app.ui.common.NetworkImage
import com.cabin.app.ui.common.OTag
import com.cabin.app.ui.common.SoftAvatarView
import com.cabin.app.ui.common.SoftCard
import com.cabin.app.ui.common.SoftChevron
import com.cabin.app.ui.common.SoftEmpty
import com.cabin.app.ui.common.SoftHeader
import com.cabin.app.ui.common.SoftLink
import com.cabin.app.ui.common.SoftLoading
import com.cabin.app.ui.common.SoftPhotoPlaceholder
import com.cabin.app.ui.common.SoftPrimaryButton
import com.cabin.app.ui.common.SoftRadius
import com.cabin.app.ui.common.SoftRow
import com.cabin.app.ui.common.SoftSecondaryButton
import com.cabin.app.ui.common.SoftTile
import com.cabin.app.ui.common.VTag
import com.cabin.app.ui.common.softClick
import com.cabin.app.ui.listings.PhotoTags
import com.cabin.app.ui.listings.metaLine
import com.cabin.app.ui.theme.SoftClay
import com.cabin.app.ui.theme.SoftInk
import com.cabin.app.ui.theme.SoftRed
import com.cabin.app.ui.theme.SoftSecondary
import com.cabin.app.ui.theme.SoftText
import com.cabin.app.ui.theme.SoftTextSoft
import com.cabin.app.ui.theme.SoftTile
import com.cabin.app.ui.theme.SoftType
import com.cabin.app.ui.theme.soft
import com.cabin.app.util.Format
import kotlin.math.abs

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
    onEdit: (String) -> Unit,
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
    var showPromote by remember { mutableStateOf(false) }
    val listing = state.listing
    val isMine = listing != null && listing.userId == viewModel.currentUserId

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        SoftHeader(
            leading = { CircleButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Back", onClick = onBack) },
            trailing = {
                if (listing != null) {
                    if (isMine) CircleButton(Icons.Outlined.Edit, "Edit listing", onClick = { onEdit(listingId) })
                    else CircleButton(Icons.Outlined.Flag, "Report listing", onClick = { showReport = true })
                } else Spacer(Modifier.size(48.dp))
            },
        )
        when {
            state.loading -> SoftLoading()
            listing == null -> SoftEmpty(Icons.Outlined.Warning, "Couldn't load listing", state.error ?: "", actionLabel = "Back", onAction = onBack)
            else -> ListingDetailContent(
                listing = listing,
                comparison = state.comparison,
                isMine = isMine,
                busy = state.busy,
                onMessage = viewModel::startConversation,
                onBook = { showBooking = true },
                onReport = { showReport = true },
                onConfirm = viewModel::confirmAvailability,
                onPromote = { viewModel.loadFeaturePlans(); showPromote = true },
                onEdit = { onEdit(listingId) },
                onOpenProfile = onOpenProfile,
            )
        }
    }

    if (showReport) {
        ReportDialog(onDismiss = { showReport = false }, onSubmit = { reason, details -> viewModel.report(reason, details); showReport = false })
    }
    if (showPromote) {
        PromoteDialog(
            plans = state.featurePlans, note = state.featureNote, busy = state.busy,
            onDismiss = { showPromote = false },
            onSubmit = { planId -> viewModel.featureListing(planId); showPromote = false },
        )
    }
    if (showBooking) {
        BookViewingDialog(onDismiss = { showBooking = false }, onSubmit = { days, hour, note -> viewModel.requestViewing(days, hour, note); showBooking = false })
    }
    state.actionMessage?.let { MessageDialog("Cabin", it, viewModel::clearActionMessage) }
}

@Composable
private fun ListingDetailContent(
    listing: Listing,
    comparison: PriceComparison?,
    isMine: Boolean,
    busy: Boolean,
    onMessage: () -> Unit,
    onBook: () -> Unit,
    onReport: () -> Unit,
    onConfirm: () -> Unit,
    onPromote: () -> Unit,
    onEdit: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 22.dp, bottom = 48.dp),
    ) {
        HeroCard(listing, isMine, busy, onMessage, onBook, onEdit, onPromote)
        TrustCard(listing)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SoftTile(Icons.Outlined.Bed, "Bedrooms", Format.beds(listing.bedrooms), compact = true, modifier = Modifier.weight(1f))
            SoftTile(Icons.Outlined.Bathtub, "Bathrooms", Format.baths(listing.bathrooms), compact = true, modifier = Modifier.weight(1f))
            SoftTile(Icons.Outlined.CropSquare, "Area · sqft", if (listing.areaSqft > 0) "%,d".format(listing.areaSqft) else "—", compact = true, modifier = Modifier.weight(1f))
        }

        if (comparison != null && comparison.sampleSize >= 3) PriceComparisonCard(comparison)

        if (listing.description.isNotBlank()) {
            SoftCard {
                Text("About this property", style = SoftType.cardTitle)
                Text(listing.description, style = SoftType.bodyLight, color = SoftTextSoft, modifier = Modifier.padding(top = 10.dp))
            }
        }

        listing.owner?.let { owner ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Listed by", style = SoftType.small, color = SoftSecondary, modifier = Modifier.padding(start = 8.dp))
                PosterRow(owner, onClick = { onOpenProfile(owner.id) })
            }
        }

        if (isMine) {
            OwnerTools(listing, busy, onConfirm)
        } else {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                SoftLink("Report this listing", onClick = onReport, muted = true)
            }
        }
    }
}

@Composable
private fun HeroCard(
    listing: Listing, isMine: Boolean, busy: Boolean,
    onMessage: () -> Unit, onBook: () -> Unit, onEdit: () -> Unit, onPromote: () -> Unit,
) {
    SoftCard {
        Box(modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(SoftRadius.image))) {
            if (listing.images.isEmpty()) {
                SoftPhotoPlaceholder(Modifier.fillMaxSize())
                Text("No photos yet", style = SoftType.caption, color = SoftText.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center).padding(top = 48.dp))
            } else {
                val pagerState = rememberPagerState(pageCount = { listing.images.size })
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    NetworkImage(url = listing.images[page].url, contentDescription = listing.title, modifier = Modifier.fillMaxSize())
                }
                if (listing.images.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    ) {
                        repeat(listing.images.size) { i ->
                            Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == pagerState.currentPage) 1f else 0.5f)))
                        }
                    }
                }
            }
            PhotoTags(listing, modifier = Modifier.padding(14.dp))
        }

        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 22.dp)) {
            Text(listing.title, style = SoftType.heading, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            OTag(if (listing.listingType == "rent") "For rent" else "For sale", filled = true, modifier = Modifier.padding(top = 6.dp))
        }
        Text(metaLine(listing), style = SoftType.small, color = SoftSecondary, modifier = Modifier.padding(top = 8.dp))

        val address = listOf(listing.address, listing.city, listing.state, listing.zipCode).filter { it.isNotBlank() }.joinToString(", ")
        if (address.isNotBlank()) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.Outlined.Place, contentDescription = null, tint = SoftSecondary, modifier = Modifier.size(16.dp))
                Text(address, style = SoftType.caption, color = SoftSecondary)
            }
        }
        if (Format.isStale(listing.lastConfirmedAt, listing.createdAt)) {
            Text("The owner hasn't confirmed this is still available in over a month.", style = SoftType.caption, color = SoftClay, modifier = Modifier.padding(top = 8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 20.dp)) {
            SoftTile(Icons.Outlined.Sell, "Asking price", Format.compactPrice(listing.price) + if (listing.listingType == "rent") "/mo" else "", modifier = Modifier.weight(1f))
            SoftTile(Icons.Outlined.VerifiedUser, "Trust score", listing.verificationScore.toString(), modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
            if (isMine) {
                SoftPrimaryButton("Edit listing", onClick = onEdit, icon = Icons.Outlined.Edit, modifier = Modifier.weight(1f))
                SoftSecondaryButton(
                    if (listing.isFeatured) "Extend featuring" else "Feature it",
                    onClick = onPromote, icon = Icons.Outlined.Star, tint = SoftTile,
                    enabled = listing.verificationStatus == Verification.VERIFIED && !busy,
                    modifier = Modifier.weight(1f),
                )
            } else {
                SoftPrimaryButton(
                    "Message ${listing.owner?.name?.split(" ")?.firstOrNull() ?: "poster"}",
                    onClick = onMessage, loading = busy, modifier = Modifier.weight(1f),
                )
                SoftSecondaryButton("Book a viewing", onClick = onBook, tint = SoftTile, enabled = listing.verificationStatus != Verification.REJECTED, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The trust panel: what the review concluded, why, and what it does not promise. */
@Composable
private fun TrustCard(listing: Listing) {
    val status = listing.verificationStatus
    val headline = when (status) {
        Verification.VERIFIED -> "Screened and verified"
        Verification.FLAGGED -> "Verified with warnings"
        Verification.REJECTED -> "Failed screening"
        Verification.PENDING -> "Being screened now"
        else -> "Not screened yet"
    }
    val fill = when (status) {
        Verification.VERIFIED -> SoftInk
        Verification.FLAGGED -> SoftClay
        Verification.REJECTED -> SoftRed
        else -> SoftTile
    }
    val glyphTint = if (fill == SoftTile) SoftTextSoft else Color.White
    val glyph = when (status) {
        Verification.VERIFIED -> Icons.Outlined.Check
        Verification.FLAGGED, Verification.REJECTED -> Icons.Outlined.Warning
        else -> Icons.Outlined.Schedule
    }

    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).clip(CircleShape).background(fill)) {
                    Icon(glyph, contentDescription = null, tint = glyphTint, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(headline, style = SoftType.cardTitle)
                    Text("Trust score ${listing.verificationScore}/100 · screened", style = SoftType.footnote, color = SoftSecondary)
                }
            }
            if (listing.verificationSummary.isNotBlank()) {
                Text(listing.verificationSummary, style = SoftType.small, color = SoftTextSoft)
            }
            if (listing.verificationFlags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listing.verificationFlags.take(3).forEach { OTag(Format.flagLabel(it)) }
                }
            }
            if (listing.reportCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Flag, contentDescription = null, tint = SoftRed, modifier = Modifier.size(14.dp))
                    Text("${listing.reportCount} user report${if (listing.reportCount == 1) "" else "s"} on this listing", style = soft(14, FontWeight.Normal), color = SoftRed)
                }
            }
            // Say plainly what the badge does and does not mean.
            Text(
                "Screened automatically for scam and quality signals. A badge is not proof of ownership — view in person before paying anything.",
                style = SoftType.footnote, color = SoftSecondary,
            )
        }
    }
}

@Composable
private fun OwnerTools(listing: Listing, busy: Boolean, onConfirm: () -> Unit) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Keep it fresh", style = SoftType.cardTitle)
            if (listing.isFeatured) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Star, contentDescription = null, tint = SoftTextSoft, modifier = Modifier.size(14.dp))
                    Text("Featured until ${Format.dateTime(listing.featuredUntil)}", style = soft(14, FontWeight.Normal), color = SoftTextSoft)
                }
            }
            if (listing.verificationStatus != Verification.VERIFIED) {
                Text("Only verified listings can be featured. Featuring buys placement, not a badge — so a listing has to pass screening first.", style = SoftType.footnote, color = SoftSecondary)
            }
            Text(
                if (Format.isStale(listing.lastConfirmedAt, listing.createdAt)) "Buyers are shown a warning on listings that haven't been confirmed recently."
                else "Confirming availability keeps the stale warning off your listing.",
                style = SoftType.footnote, color = SoftSecondary,
            )
            SoftSecondaryButton("Confirm it's still available", onClick = onConfirm, icon = Icons.Outlined.Check, tint = SoftTile, loading = busy, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Owner / agent row on the listing detail screen. */
@Composable
fun PosterRow(owner: UserSummary, onClick: () -> Unit) {
    SoftRow(onClick = onClick) {
        SoftAvatarView(owner.name, size = 44.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(owner.name, style = SoftType.body)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (owner.isVerified) VTag("Verified") else OTag("Not verified")
                if (owner.isAgent) OTag("Agent")
                if (owner.ratingCount > 0) OTag("★ %.1f · %d".format(owner.ratingAvg, owner.ratingCount))
            }
        }
        SoftChevron()
    }
}

/** Answers "is this price reasonable?" — 46% of respondents asked for exactly this. */
@Composable
private fun PriceComparisonCard(comparison: PriceComparison) {
    val difference = when {
        abs(comparison.percentDiff) < 1 -> "right at"
        comparison.percentDiff > 0 -> "%.0f%% above".format(comparison.percentDiff)
        else -> "%.0f%% below".format(-comparison.percentDiff)
    }
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Price check", style = SoftType.cardTitle, modifier = Modifier.weight(1f))
                if (comparison.verdict == "below_market") VTag(comparison.verdictLabel) else OTag(comparison.verdictLabel)
            }
            Text(
                "Compared with ${comparison.sampleSize} similar listings nearby, this is $difference the median of ${Format.compactPrice(comparison.median)}.",
                style = SoftType.small, color = SoftTextSoft,
            )
            val span = (comparison.max - comparison.min).coerceAtLeast(1)
            val ratio = ((comparison.price - comparison.min).toFloat() / span).coerceIn(0f, 1f)
            Box(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(8.dp).clip(CircleShape).background(SoftTile))
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(ratio.coerceAtLeast(0.04f))) {
                    Box(Modifier.align(Alignment.CenterEnd).size(14.dp).clip(CircleShape).background(SoftInk))
                }
            }
            Row {
                Text(Format.compactPrice(comparison.min), style = SoftType.footnote, color = SoftSecondary, modifier = Modifier.weight(1f))
                Text(Format.compactPrice(comparison.max), style = SoftType.footnote, color = SoftSecondary)
            }
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = reason == value, onClick = { reason = value })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("Optional details") }, minLines = 2)
                Spacer(Modifier.height(8.dp))
                Text("Reports go to our moderators. Three open reports send a listing back for re-screening automatically.", style = MaterialTheme.typography.labelSmall, color = SoftSecondary)
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(reason, details) }) { Text("Submit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Buying promoted placement for a listing you own. Featured listings were the
 * survey's one unanimous supply-side ask. Sold per listing, since most posters
 * here have one property.
 */
@Composable
private fun PromoteDialog(plans: List<FeaturePlan>, note: String, busy: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var selected by remember(plans) { mutableStateOf(plans.firstOrNull()?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Feature listing") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (plans.isEmpty()) {
                    Text("Loading packages…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    plans.forEach { plan ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = selected == plan.id, onClick = { selected = plan.id })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(plan.label, style = MaterialTheme.typography.bodyMedium)
                                Text("${plan.days} days of promoted placement", style = MaterialTheme.typography.labelSmall, color = SoftSecondary)
                            }
                            Text(Format.price(plan.price, "sale"), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                if (note.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(note, style = MaterialTheme.typography.labelSmall, color = SoftSecondary)
                }
            }
        },
        confirmButton = { TextButton(onClick = { selected?.let(onSubmit) }, enabled = selected != null && !busy) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Suppress("unused")
private val keep = Modifier.softClick {}

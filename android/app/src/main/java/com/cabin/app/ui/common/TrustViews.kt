package com.cabin.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.UserSummary
import com.cabin.app.data.model.Verification
import com.cabin.app.util.Format

// The survey's dominant signal: 86% called trust & verification "extremely
// important", 79% wanted verified-only listings. These views are how that shows
// up on screen, and they are deliberate about what the badge claims.

@Composable
fun verificationColor(status: String): Color = when (status) {
    Verification.VERIFIED -> MaterialTheme.colorScheme.primary
    Verification.FLAGGED -> MaterialTheme.colorScheme.tertiary
    Verification.REJECTED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun verificationIcon(status: String): ImageVector = when (status) {
    Verification.VERIFIED -> Icons.Filled.CheckCircle
    Verification.FLAGGED, Verification.REJECTED -> Icons.Filled.Warning
    else -> Icons.Outlined.Schedule
}

/** Compact badge for listing cards and rows. */
@Composable
fun VerificationBadge(status: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val tint = verificationColor(status)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 3.dp),
    ) {
        Icon(verificationIcon(status), contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        if (!compact) {
            Text(
                Verification.label(status),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = tint,
            )
        }
    }
}

/** Star rating with the review count. */
@Composable
fun RatingStars(rating: Double, count: Int, modifier: Modifier = Modifier) {
    if (count == 0) {
        Text(
            "No reviews yet",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        modifier = modifier,
    ) {
        repeat(5) { i ->
            Icon(
                if (i < Math.round(rating)) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            " %.1f (%d)".format(rating, count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The full trust panel on a listing detail screen: what the review concluded,
 * why, and what it does not promise.
 */
@Composable
fun TrustPanel(listing: Listing, modifier: Modifier = Modifier) {
    val tint = verificationColor(listing.verificationStatus)
    val headline = when (listing.verificationStatus) {
        Verification.VERIFIED -> "Screened and verified"
        Verification.FLAGGED -> "Verified with warnings"
        Verification.REJECTED -> "Failed screening"
        Verification.PENDING -> "Being screened now"
        else -> "Not screened yet"
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.08f))
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(verificationIcon(listing.verificationStatus), contentDescription = null, tint = tint)
            Column {
                Text(headline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Trust score ${listing.verificationScore}/100",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (listing.verificationSummary.isNotBlank()) {
            Text(
                listing.verificationSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        listing.verificationFlags.forEach { flag ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    Format.flagLabel(flag),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        if (listing.reportCount > 0) {
            Text(
                "${listing.reportCount} user report${if (listing.reportCount == 1) "" else "s"} on this listing",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Say plainly what the badge does and does not mean.
        Text(
            "Listings are screened automatically for scam and quality signals. A badge is not a guarantee of ownership — always view in person before paying anything.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown when the owner hasn't confirmed availability in over a month. */
@Composable
fun StaleWarning(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
            .padding(10.dp),
    ) {
        Icon(
            Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "The owner hasn't confirmed this is still available in over a month.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/** Owner / agent card used on the listing detail screen. */
@Composable
fun PosterCard(owner: UserSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        ) {
            Text(
                owner.name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString(""),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(owner.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (owner.isAgent) AgentBadge()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VerificationBadge(owner.verificationStatus)
                RatingStars(owner.ratingAvg, owner.ratingCount)
            }
        }
        Spacer(Modifier.size(0.dp))
    }
}

/** Lifecycle state of a viewing appointment. */
@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val (label, tint) = when (status) {
        "requested" -> "Pending" to MaterialTheme.colorScheme.tertiary
        "confirmed" -> "Confirmed" to MaterialTheme.colorScheme.primary
        "completed" -> "Completed" to MaterialTheme.colorScheme.primary
        "declined" -> "Declined" to MaterialTheme.colorScheme.error
        "cancelled" -> "Cancelled" to MaterialTheme.colorScheme.error
        else -> Format.capitalize(status) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = tint,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

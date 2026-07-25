package com.cabin.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cabin.app.util.Format
import java.util.Locale

/** Warm gold used for rating stars (mirrors the iOS `.orange` rating tint). */
val RatingGold = Color(0xFFF5A623)

/** Coil image that resolves relative (/uploads/...) or absolute URLs. */
@Composable
fun NetworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop,
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = Format.imageUrl(url),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50)),
            )
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, background: Color? = null, contentColor: Color? = null) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background ?: MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor ?: MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun FullScreenMessage(
    title: String,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            )
        }
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) {
                Text(actionLabel)
            }
        }
    }
}

val ScreenPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

/** A "✓ Verified" trust badge. Use [compact] for a seal-only variant on tight surfaces. */
@Composable
fun VerifiedBadge(modifier: Modifier = Modifier, compact: Boolean = false) {
    val forest = MaterialTheme.colorScheme.primary
    if (compact) {
        Icon(
            Icons.Filled.Verified,
            contentDescription = "Verified",
            tint = forest,
            modifier = modifier.size(18.dp),
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(forest.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(Icons.Filled.Verified, contentDescription = null, tint = forest, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(3.dp))
            Text(
                "Verified",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = forest,
            )
        }
    }
}

/** Compact rating display: ★ 4.7 (12). Shows "No reviews yet" when empty. */
@Composable
fun RatingStars(avg: Double, count: Int, modifier: Modifier = Modifier, showCount: Boolean = true) {
    if (count > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = RatingGold, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(3.dp))
            Text(
                String.format(Locale.US, "%.1f", avg),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (showCount) {
                Spacer(Modifier.size(3.dp))
                Text(
                    "($count)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    } else {
        Text(
            "No reviews yet",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = modifier,
        )
    }
}

/** A colored availability pill (Sold / Rented / Pending / Active). */
@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val color = when (status.lowercase(Locale.US)) {
        "sold", "rented" -> Color(0xFFC62828)
        "pending" -> Color(0xFFE08600)
        "inactive" -> Color(0xFF6B6B6B)
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            Format.statusLabel(status),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

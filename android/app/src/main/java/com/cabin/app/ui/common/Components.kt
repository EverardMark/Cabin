package com.cabin.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cabin.app.ui.theme.SoftInk
import com.cabin.app.util.Format

/** Coil image that resolves relative (/uploads/...) or absolute URLs, over the soft placeholder. */
@Composable
fun NetworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier = modifier) {
        SoftPhotoPlaceholder(modifier = Modifier.fillMaxSize())
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

/** Kept for the post-listing form; a full-width ink pill. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    SoftPrimaryButton(text = text, onClick = onClick, modifier = modifier.fillMaxWidth(), large = true, loading = loading, enabled = enabled)
}

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SoftInk, strokeWidth = 2.dp)
    }
}

val ScreenPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp)

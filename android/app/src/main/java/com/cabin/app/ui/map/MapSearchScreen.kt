package com.cabin.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.BuildConfig
import com.cabin.app.data.model.Listing
import com.cabin.app.ui.common.FullScreenMessage
import com.cabin.app.ui.common.NetworkImage
import com.cabin.app.ui.common.VerificationBadge
import com.cabin.app.util.Format
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Map-based property search — 48% of respondents asked for it. Listings load for
 * whatever region is on screen, so panning is the search.
 */
@Composable
fun MapSearchScreen(
    onOpenListing: (String) -> Unit,
    viewModel: MapSearchViewModel = viewModel(),
) {
    if (BuildConfig.MAPS_API_KEY.isBlank()) {
        FullScreenMessage(
            title = "Map isn't configured yet",
            message = "Add a Google Maps SDK for Android key as MAPS_API_KEY in app/build.gradle.kts to turn on map search. Browse and “nearby” search work without it.",
        )
        return
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    // Metro Manila south / Muntinlupa, where most survey respondents live.
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(14.4223, 121.0292), 11f)
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            cameraPositionState.projection?.visibleRegion?.latLngBounds?.let { bounds ->
                viewModel.load(
                    minLat = bounds.southwest.latitude,
                    maxLat = bounds.northeast.latitude,
                    minLng = bounds.southwest.longitude,
                    maxLng = bounds.northeast.longitude,
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
        ) {
            state.listings.forEach { listing ->
                val lat = listing.latitude
                val lng = listing.longitude
                if (lat != null && lng != null) {
                    Marker(
                        state = MarkerState(position = LatLng(lat, lng)),
                        title = Format.compactPrice(listing.price),
                        snippet = listing.title,
                        onClick = {
                            viewModel.select(listing)
                            false
                        },
                    )
                }
            }
        }

        state.selected?.let { listing ->
            MapListingPreview(
                listing = listing,
                onClick = { onOpenListing(listing.id) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            )
        }

        if (state.loading) {
            Text(
                "Loading…",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun MapListingPreview(listing: Listing, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        NetworkImage(
            url = listing.images.firstOrNull()?.url,
            contentDescription = null,
            modifier = Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                listing.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                Format.price(listing.price, listing.listingType),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            VerificationBadge(listing.verificationStatus)
        }
        Text(
            "Open",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

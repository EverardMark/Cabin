package com.cabin.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.BuildConfig
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.Verification
import com.cabin.app.ui.common.NetworkImage
import com.cabin.app.ui.common.SoftEmpty
import com.cabin.app.ui.common.SoftHeader
import com.cabin.app.ui.common.SoftPill
import com.cabin.app.ui.common.softClick
import com.cabin.app.ui.common.softShadowRow
import com.cabin.app.ui.theme.SoftInk
import com.cabin.app.ui.theme.SoftLabel
import com.cabin.app.ui.theme.SoftTextSoft
import com.cabin.app.ui.theme.soft
import com.cabin.app.util.Format
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Map-based property search — 48% of respondents asked for it. Listings load for
 * whatever region is on screen, so panning is the search. Pins are photo circles;
 * the selected one gets a black ring and its price and trust score appear in the
 * floating pills.
 */
@Composable
fun MapSearchScreen(
    onOpenListing: (String) -> Unit,
    viewModel: MapSearchViewModel = viewModel(),
) {
    if (BuildConfig.MAPS_API_KEY.isBlank()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SoftHeader(leading = { com.cabin.app.ui.common.AppMark() }, title = { Text("Map", style = com.cabin.app.ui.theme.SoftType.screenTitle) })
            SoftEmpty(
                Icons.Outlined.Map, "Map isn't configured yet",
                "Add a Google Maps SDK for Android key as MAPS_API_KEY in app/build.gradle.kts to turn on map search. Browse works without it.",
            )
        }
        return
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    // Metro Manila south / Muntinlupa, where most survey respondents live.
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(14.4223, 121.0292), 11f)
    }

    fun reload() {
        cameraPositionState.projection?.visibleRegion?.latLngBounds?.let { bounds ->
            viewModel.load(
                minLat = bounds.southwest.latitude, maxLat = bounds.northeast.latitude,
                minLng = bounds.southwest.longitude, maxLng = bounds.northeast.longitude,
            )
        }
    }

    LaunchedEffect(cameraPositionState.isMoving) { if (!cameraPositionState.isMoving) reload() }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = false, mapToolbarEnabled = false),
        ) {
            state.listings.forEach { listing ->
                val lat = listing.latitude
                val lng = listing.longitude
                if (lat != null && lng != null) {
                    val selected = state.selected?.id == listing.id
                    MarkerComposable(
                        keys = arrayOf(listing.id, selected),
                        state = MarkerState(position = LatLng(lat, lng)),
                        onClick = { viewModel.select(listing); true },
                    ) {
                        MapPin(listing, selected)
                    }
                }
            }
        }

        // Search bar + filter circle.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .softShadowRow(CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(horizontal = 18.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = SoftLabel, modifier = Modifier.size(20.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search", style = soft(18), color = SoftLabel)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = soft(18),
                        cursorBrush = SolidColor(SoftInk),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.setQuery(query); reload() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Box {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .softShadowRow(CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                        .softClick { menuOpen = true },
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Filters", tint = SoftTextSoft, modifier = Modifier.size(22.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text((if (state.filters.verifiedOnly) "✓ " else "") + "Verified only") },
                        onClick = { viewModel.setVerifiedOnly(!state.filters.verifiedOnly); reload(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text((if (state.filters.excludeStale) "✓ " else "") + "Recently confirmed") },
                        onClick = { viewModel.setExcludeStale(!state.filters.excludeStale); reload(); menuOpen = false },
                    )
                    listOf(null to "Buy or rent", "sale" to "Buy", "rent" to "Rent").forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text((if (state.filters.listingType == value) "✓ " else "") + label) },
                            onClick = { viewModel.setListingType(value); reload(); menuOpen = false },
                        )
                    }
                }
            }
        }

        // Floating stat pills, bottom-left, above the tab bar.
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 24.dp, bottom = 110.dp)
                .widthIn(max = 220.dp),
        ) {
            SoftPill(state.listings.count { it.verificationStatus == Verification.VERIFIED }.toString(), "Verified in view")
            state.selected?.let { sel ->
                SoftPill(
                    Format.compactPrice(sel.price) + if (sel.listingType == "rent") "/mo" else "",
                    sel.title,
                    onClick = { onOpenListing(sel.id) },
                )
                SoftPill(
                    sel.verificationScore.toString(),
                    if (sel.verificationStatus == Verification.VERIFIED) "Trust score · screened" else "Trust score · ${Verification.label(sel.verificationStatus).lowercase()}",
                )
            } ?: run {
                if (state.listings.isNotEmpty()) {
                    Text(
                        "Tap a pin", style = soft(13),
                        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.9f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Photo circle with a white (or black, when selected) ring and a dot beneath (.pin). */
@Composable
private fun MapPin(listing: Listing, selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NetworkImage(
            url = listing.images.firstOrNull()?.url,
            contentDescription = listing.title,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .border(3.dp, if (selected) SoftInk else Color.White, CircleShape),
        )
        Box(Modifier.size(6.dp).clip(CircleShape).background(SoftInk))
    }
}

@Suppress("unused")
private val keep = Modifier.width(0.dp)

package com.cabin.app.ui.detail

import android.widget.Toast
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.data.model.Listing
import com.cabin.app.ui.common.FullScreenLoading
import com.cabin.app.ui.common.FullScreenMessage
import com.cabin.app.ui.common.NetworkImage
import com.cabin.app.ui.common.Pill
import com.cabin.app.ui.common.PrimaryButton
import com.cabin.app.util.Format

@Composable
fun ListingDetailScreen(
    listingId: String,
    onBack: () -> Unit,
    viewModel: ListingDetailViewModel = viewModel(),
) {
    LaunchedEffect(listingId) { viewModel.load(listingId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> FullScreenLoading()
            state.error != null -> FullScreenMessage(
                title = "Couldn't load listing",
                message = state.error,
                actionLabel = "Back",
                onAction = onBack,
            )
            state.listing != null -> ListingDetailContent(state.listing!!, onBack)
        }
    }
}

@Composable
private fun ListingDetailContent(listing: Listing, onBack: () -> Unit) {
    val context = LocalContext.current

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
                            Text(owner.name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = "Contact agent",
                onClick = {
                    Toast.makeText(context, "Contacting ${listing.owner?.name ?: "the agent"}…", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            )
            Spacer(Modifier.height(16.dp))
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

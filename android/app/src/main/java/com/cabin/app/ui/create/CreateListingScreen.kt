package com.cabin.app.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cabin.app.data.model.ListingRequest
import com.cabin.app.ui.common.PrimaryButton
import com.cabin.app.util.Format
import com.cabin.app.util.copyUriToCache
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import com.cabin.app.data.model.Listing
import com.cabin.app.ui.common.NetworkImage

private val propertyTypes = listOf("house", "apartment", "condo", "townhouse", "land")

@Composable
fun CreateListingScreen(
    onCreated: (String) -> Unit,
    /** Non-null puts the form in edit mode. */
    editing: Listing? = null,
    onDeleted: () -> Unit = {},
    viewModel: CreateListingViewModel = viewModel(),
) {
    val context = LocalContext.current
    val isEdit = editing != null
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var listingType by rememberSaveable { mutableStateOf("sale") }
    var propertyType by rememberSaveable { mutableStateOf("house") }
    var bedrooms by rememberSaveable { mutableStateOf("") }
    var bathrooms by rememberSaveable { mutableStateOf("") }
    var area by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var stateField by rememberSaveable { mutableStateOf("") }
    var zip by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Prefill once when editing.
    LaunchedEffect(editing?.id) {
        editing?.let { l ->
            title = l.title
            description = l.description
            price = l.price.toString()
            listingType = l.listingType
            propertyType = l.propertyType
            bedrooms = l.bedrooms.toString()
            bathrooms = l.bathrooms.toString()
            area = l.areaSqft.toString()
            address = l.address
            city = l.city
            stateField = l.state
            zip = l.zipCode
            latitude = l.latitude?.toString() ?: ""
            longitude = l.longitude?.toString() ?: ""
            viewModel.startEditing(l)
        }
    }
    val hasPin = latitude.toDoubleOrNull() != null && longitude.toDoubleOrNull() != null

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(6),
    ) { uris ->
        if (uris.isNotEmpty()) images = (images + uris).distinct().take(8)
    }

    val canSubmit = title.isNotBlank() && price.toLongOrNull() != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            if (isEdit) "Edit listing" else "Post a listing",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))

        // Photos
        SectionLabel("Photos")
        if (isEdit && viewModel.existingImages.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(viewModel.existingImages) { image ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NetworkImage(
                            url = image.url,
                            contentDescription = null,
                            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Row {
                            if (viewModel.existingImages.firstOrNull()?.id != image.id) {
                                TextButton(onClick = { viewModel.promotePhoto(editing!!.id, image.id) }) {
                                    Text("Front", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            TextButton(onClick = { viewModel.deletePhoto(editing!!.id, image.id) }) {
                                Text("Delete", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Text(
                "The first photo is what buyers see on the card.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Surface(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(96.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.AddAPhoto, contentDescription = "Add photo")
                        Text("Add", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(images) { uri ->
                Box(modifier = Modifier.size(96.dp)) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    Surface(
                        onClick = { images = images - uri },
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove",
                            tint = Color.White,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Listing type")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = listingType == "sale", onClick = { listingType = "sale" }, label = { Text("For sale") })
            FilterChip(selected = listingType == "rent", onClick = { listingType = "rent" }, label = { Text("For rent") })
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Property type")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(propertyTypes) { type ->
                FilterChip(
                    selected = propertyType == type,
                    onClick = { propertyType = type },
                    label = { Text(Format.capitalize(type)) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Field(value = title, onChange = { title = it }, label = "Title *")
        Field(value = price, onChange = { price = it }, label = "Price (₱) *", keyboardType = KeyboardType.Number)
        Field(
            value = description,
            onChange = { description = it },
            label = "Description",
            singleLine = false,
        )

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(value = bedrooms, onChange = { bedrooms = it }, label = "Beds", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
            Field(value = bathrooms, onChange = { bathrooms = it }, label = "Baths", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
            Field(value = area, onChange = { area = it }, label = "Sq ft", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
        }

        Field(value = address, onChange = { address = it }, label = "Address")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(value = city, onChange = { city = it }, label = "City", modifier = Modifier.weight(2f))
            Field(value = stateField, onChange = { stateField = it }, label = "State", modifier = Modifier.weight(1f))
        }
        Field(value = zip, onChange = { zip = it }, label = "ZIP", keyboardType = KeyboardType.Number)

        val error = viewModel.error
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        // Live feedback on the things the reviewer actually weighs. Poor photos
        // and thin information were the survey's third-biggest complaint.
        SectionLabel("Map location")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(
                value = latitude,
                onChange = { latitude = it },
                label = "Latitude",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            Field(
                value = longitude,
                onChange = { longitude = it },
                label = "Longitude",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Listings without a map pin don't appear in map search. You can copy coordinates from Google Maps by long-pressing the spot.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        SectionLabel("Before you publish")
        QualityCheck("At least 3 photos", images.size + viewModel.existingImages.size >= 3)
        QualityCheck("Description of 20+ words", description.trim().split(Regex("\\s+")).count { it.isNotBlank() } >= 20)
        QualityCheck("City or address filled in", city.isNotBlank() || address.isNotBlank())
        QualityCheck("Price set", (price.toLongOrNull() ?: 0) > 0)
        QualityCheck("Map pin set", hasPin)
        Text(
            "Every listing is screened before it gets a verified badge. Editing sends it back for a fresh check.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        PrimaryButton(
            text = if (isEdit) "Save changes" else "Publish listing",
            onClick = {
                val request = ListingRequest(
                    title = title.trim(),
                    description = description.trim(),
                    price = price.toLongOrNull() ?: 0,
                    propertyType = propertyType,
                    listingType = listingType,
                    bedrooms = bedrooms.toIntOrNull() ?: 0,
                    bathrooms = bathrooms.toDoubleOrNull() ?: 0.0,
                    areaSqft = area.toIntOrNull() ?: 0,
                    address = address.trim(),
                    city = city.trim(),
                    state = stateField.trim(),
                    zipCode = zip.trim(),
                    latitude = latitude.toDoubleOrNull(),
                    longitude = longitude.toDoubleOrNull(),
                )
                val files = images.mapNotNull { copyUriToCache(context, it) }
                viewModel.submit(editing?.id, request, files, onCreated)
            },
            enabled = canSubmit,
            loading = viewModel.loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        )

        if (isEdit) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete listing", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDeleteConfirm && editing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this listing?") },
            text = {
                Text("This can't be undone. Anyone you've been messaging about it will lose the thread.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteListing(editing.id, onDeleted)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun QualityCheck(label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "  $label",
            style = MaterialTheme.typography.labelMedium,
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    )
}


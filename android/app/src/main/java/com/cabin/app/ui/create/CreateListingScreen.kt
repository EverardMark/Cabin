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

private val propertyTypes = listOf("house", "apartment", "condo", "townhouse", "land")

@Composable
fun CreateListingScreen(
    onCreated: (String) -> Unit,
    viewModel: CreateListingViewModel = viewModel(),
) {
    val context = LocalContext.current

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
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }

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
        Text("Post a listing", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Photos
        SectionLabel("Photos")
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
        Field(value = price, onChange = { price = it }, label = "Price *", keyboardType = KeyboardType.Number)
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
        PrimaryButton(
            text = "Publish listing",
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
                )
                val files = images.mapNotNull { copyUriToCache(context, it) }
                viewModel.submit(request, files, onCreated)
            },
            enabled = canSubmit,
            loading = viewModel.loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        )
        Spacer(Modifier.height(24.dp))
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

package com.cabin.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.data.model.Listing
import com.cabin.app.data.model.User
import com.cabin.app.data.model.Verification
import com.cabin.app.ui.common.AppMark
import com.cabin.app.ui.common.CircleButton
import com.cabin.app.ui.common.MessageDialog
import com.cabin.app.ui.common.OTag
import com.cabin.app.ui.common.SoftAvatarView
import com.cabin.app.ui.common.SoftCard
import com.cabin.app.ui.common.SoftChevron
import com.cabin.app.ui.common.SoftError
import com.cabin.app.ui.common.SoftHeader
import com.cabin.app.ui.common.SoftLoading
import com.cabin.app.ui.common.SoftPhoto
import com.cabin.app.ui.common.SoftPrimaryButton
import com.cabin.app.ui.common.SoftRow
import com.cabin.app.ui.common.SoftSmallButton
import com.cabin.app.ui.common.SoftTile
import com.cabin.app.ui.common.VTag
import com.cabin.app.ui.common.softClick
import com.cabin.app.ui.listings.PhotoTags
import com.cabin.app.ui.listings.PriceText
import com.cabin.app.ui.theme.SoftAccent
import com.cabin.app.ui.theme.SoftClay
import com.cabin.app.ui.theme.SoftSecondary
import com.cabin.app.ui.theme.SoftType
import com.cabin.app.ui.theme.soft
import com.cabin.app.util.Format

@Composable
fun ProfileScreen(
    onOpenListing: (String) -> Unit,
    onOpenSavedSearches: () -> Unit,
    onOpenPost: () -> Unit,
    viewModel: ProfileViewModel = viewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPhoneDialog by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SoftHeader(
            leading = { AppMark() },
            title = { Text("Profile", style = SoftType.screenTitle) },
            trailing = {
                Box {
                    CircleButton(Icons.Outlined.Settings, "Settings", onClick = { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Edit profile") }, onClick = { menuOpen = false; showEdit = true })
                        DropdownMenuItem(text = { Text("Post a listing") }, onClick = { menuOpen = false; onOpenPost() })
                        DropdownMenuItem(text = { Text("Log out") }, onClick = { menuOpen = false; showLogout = true })
                    }
                }
            },
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { IdentityCard(user) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SoftTile(Icons.Outlined.Home, "Active listings", state.listings.count { it.status == "active" }.toString(), modifier = Modifier.weight(1f))
                    SoftTile(Icons.Outlined.CalendarMonth, "Requests to answer", summary.pendingViewingRequests.toString(), modifier = Modifier.weight(1f))
                }
            }
            item {
                val newMatches = state.savedSearches.sumOf { it.newMatches }
                SoftRow(onClick = onOpenSavedSearches) {
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("Saved searches", style = SoftType.body)
                        Text(state.savedSearches.firstOrNull()?.name ?: "Get alerted when new listings match", style = SoftType.caption, color = SoftSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (newMatches > 0) VTag("$newMatches new", icon = null, tint = SoftAccent) else SoftChevron()
                }
            }
            item {
                // A confirmed number is the prerequisite for the badge: it means somebody is reachable.
                SoftRow(onClick = { viewModel.resetPhoneFlow(); showPhoneDialog = true }) {
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("Mobile number", style = SoftType.body)
                        Text(
                            when {
                                user?.phoneVerified == true -> "${Format.maskedPhone(user?.phone.orEmpty())} · confirmed by SMS"
                                user?.phone.isNullOrBlank() -> "Add and confirm your number"
                                else -> "${user?.phone} · not confirmed yet"
                            },
                            style = SoftType.caption,
                            color = if (user?.phoneVerified == true) SoftSecondary else SoftClay,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SoftChevron()
                }
            }
            if (user?.verificationStatus != Verification.VERIFIED) {
                item { VerificationRow(user, state.verifying, viewModel::requestVerification) }
            }
            item {
                SoftPrimaryButton("Post a listing", onClick = onOpenPost, icon = Icons.Outlined.Add, large = true, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            item {
                Text("My listings", style = SoftType.small, color = SoftSecondary, modifier = Modifier.padding(horizontal = 8.dp).padding(top = 10.dp))
            }
            when {
                state.loading -> item { SoftLoading() }
                state.error != null -> item { SoftError(state.error) }
                state.listings.isEmpty() -> item {
                    Text("You haven't posted anything yet. Owners, agents and renters can all post.", style = SoftType.small, color = SoftSecondary, modifier = Modifier.padding(horizontal = 8.dp))
                }
                else -> items(state.listings, key = { it.id }) { listing ->
                    MyListingCard(listing, onClick = { onOpenListing(listing.id) })
                }
            }
        }
    }

    if (showPhoneDialog) {
        PhoneVerificationDialog(
            initialPhone = user?.phone.orEmpty(),
            state = state,
            onSend = viewModel::sendPhoneCode,
            onVerify = { code -> viewModel.verifyPhoneCode(code) { showPhoneDialog = false } },
            onDismiss = { showPhoneDialog = false },
        )
    }
    if (showEdit) {
        EditProfileDialog(user, saving = state.savingProfile, onDismiss = { showEdit = false }) { name, phone, bio, licence, agent ->
            viewModel.saveProfile(name, phone, bio, licence, agent)
            showEdit = false
        }
    }
    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("Log out of Cabin?") },
            confirmButton = { TextButton(onClick = { showLogout = false; viewModel.logout() }) { Text("Log out") } },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text("Cancel") } },
        )
    }
    state.verificationMessage?.let { MessageDialog("Verification", it, viewModel::clearVerificationMessage) }
}

@Composable
private fun IdentityCard(user: User?) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SoftAvatarView(user?.name ?: "?", size = 72.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(user?.name ?: "—", style = SoftType.screenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        user == null -> ""
                        user.isAgent && user.licenseNo.isNotBlank() -> "Licensed agent · ${user.licenseNo}"
                        user.isAgent -> "Real estate agent"
                        else -> user.email
                    },
                    style = SoftType.small, color = SoftSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (user?.isVerified == true) VTag("Account verified")
                    else OTag(if (user?.verificationStatus == Verification.PENDING) "Under review" else "Not verified")
                    if (user != null && user.ratingCount > 0) OTag("★ %.1f · %d".format(user.ratingAvg, user.ratingCount))
                }
            }
        }
    }
}

@Composable
private fun VerificationRow(user: User?, verifying: Boolean, onRequest: () -> Unit) {
    SoftRow {
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text("Identity verification", style = SoftType.body)
            Text(
                when {
                    !user?.verificationNotes.isNullOrBlank() -> user!!.verificationNotes
                    user?.phoneVerified == true -> if (user.isAgent) "Needs your PRC licence number and a short review." else "A short automated review of your account."
                    else -> "Confirm your mobile number first."
                },
                style = SoftType.caption, color = SoftSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (user?.phoneVerified == true && user.verificationStatus != Verification.PENDING) {
            SoftSmallButton(if (verifying) "…" else "Request", onClick = onRequest, enabled = !verifying)
        } else {
            OTag(if (user?.verificationStatus == Verification.PENDING) "Reviewing" else "Optional")
        }
    }
}

/** The owner's own listing: compact card plus the screening verdict when something needs fixing. */
@Composable
private fun MyListingCard(listing: Listing, onClick: () -> Unit) {
    SoftCard(padding = 12.dp, modifier = Modifier.softClick(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            SoftPhoto(url = listing.images.firstOrNull()?.url, modifier = Modifier.fillMaxSize())
            PhotoTags(listing, modifier = Modifier.padding(12.dp))
        }
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(horizontal = 8.dp).padding(top = 12.dp)) {
            Text(listing.title, style = soft(18), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            PriceText(listing.price, listing.listingType, size = 16)
        }
        val warning = when {
            listing.verificationStatus == Verification.FLAGGED || listing.verificationStatus == Verification.REJECTED ->
                listing.verificationSummary.ifBlank { Verification.label(listing.verificationStatus) }
            Format.isStale(listing.lastConfirmedAt, listing.createdAt) -> "Not confirmed recently — open it and tap “still available”."
            else -> null
        }
        if (warning != null) {
            Text(warning, style = SoftType.caption, color = SoftClay, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp).padding(top = 4.dp))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun EditProfileDialog(
    user: User?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, bio: String, licence: String, agent: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(user?.name.orEmpty()) }
    var phone by remember { mutableStateOf(user?.phone.orEmpty()) }
    var bio by remember { mutableStateOf(user?.bio.orEmpty()) }
    var licence by remember { mutableStateOf(user?.licenseNo.orEmpty()) }
    var agent by remember { mutableStateOf(user?.isAgent == true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full name") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Mobile number") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Short bio") }, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("I'm a real estate agent", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = agent, onCheckedChange = { agent = it })
                }
                if (agent) OutlinedTextField(value = licence, onValueChange = { licence = it }, label = { Text("PRC licence number") }, singleLine = true)
                Text("Changing your number means you'll need to verify again.", style = MaterialTheme.typography.labelSmall, color = SoftSecondary)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, phone, bio, licence, agent) }, enabled = !saving) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Confirms a mobile number by SMS. */
@Composable
private fun PhoneVerificationDialog(
    initialPhone: String,
    state: ProfileUiState,
    onSend: (String) -> Unit,
    onVerify: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var phone by remember { mutableStateOf(initialPhone) }
    var code by remember { mutableStateOf("") }
    val awaitingCode = state.phoneCodeSentTo != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm your number") },
        text = {
            Column {
                if (!awaitingCode) {
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Mobile number") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    Spacer(Modifier.height(8.dp))
                    Text("We'll text you a 6-digit code. Confirming your number is what earns the verified badge.", style = MaterialTheme.typography.labelSmall, color = SoftSecondary)
                } else {
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("6-digit code") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    Spacer(Modifier.height(6.dp))
                    Text("Sent to ${state.phoneCodeSentTo}", style = MaterialTheme.typography.labelSmall, color = SoftSecondary)
                    state.phoneDevCode?.let {
                        Spacer(Modifier.height(6.dp))
                        Text("Development code: $it (no SMS gateway configured)", style = MaterialTheme.typography.labelSmall, color = SoftClay)
                    }
                }
                state.phoneError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (awaitingCode) onVerify(code) else onSend(phone) },
                enabled = !state.phoneBusy && (if (awaitingCode) code.length == 6 else phone.count { it.isDigit() } >= 10),
            ) { Text(if (awaitingCode) "Confirm" else "Send code") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

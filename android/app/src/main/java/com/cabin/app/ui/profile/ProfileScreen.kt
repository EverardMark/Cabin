package com.cabin.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import com.cabin.app.data.model.Verification
import com.cabin.app.ui.common.AgentBadge
import com.cabin.app.ui.common.RatingStars
import com.cabin.app.ui.common.VerificationBadge
import com.cabin.app.ui.common.verificationColor
import com.cabin.app.ui.listings.ListingCard
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.OutlinedTextField

@Composable
fun ProfileScreen(
    onOpenListing: (String) -> Unit,
    onOpenSavedSearches: () -> Unit,
    onOpenViewings: () -> Unit,
    viewModel: ProfileViewModel = viewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPhoneDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            user?.name?.firstOrNull()?.uppercase() ?: "?",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user?.name ?: "—", style = MaterialTheme.typography.titleLarge)
                        if (user?.isAgent == true) {
                            Spacer(Modifier.size(8.dp))
                            AgentBadge()
                        }
                    }
                    Text(
                        user?.email ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    RatingStars(user?.ratingAvg ?: 0.0, user?.ratingCount ?: 0)
                }
            }

            VerificationCard(
                status = user?.verificationStatus ?: Verification.UNVERIFIED,
                notes = user?.verificationNotes.orEmpty(),
                verifying = state.verifying,
                phoneVerified = user?.phoneVerified == true,
                onRequest = viewModel::requestVerification,
                onConfirmPhone = {
                    viewModel.resetPhoneFlow()
                    showPhoneDialog = true
                },
            )
            Spacer(Modifier.height(10.dp))

            OutlinedButton(onClick = onOpenSavedSearches, modifier = Modifier.fillMaxWidth()) {
                Text("Saved searches")
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onOpenViewings, modifier = Modifier.fillMaxWidth()) {
                Text("My viewings")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = viewModel::logout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Log out")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "My listings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        when {
            state.loading -> item {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            state.error != null -> item {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }
            state.listings.isEmpty() -> item {
                Text(
                    "You haven't posted anything yet. Tap Post to add your first listing — owners, agents and renters can all post.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            else -> items(state.listings, key = { it.id }) { listing ->
                Column {
                    ListingCard(listing = listing, onClick = { onOpenListing(listing.id) })
                    if (listing.verificationStatus == Verification.REJECTED ||
                        listing.verificationStatus == Verification.FLAGGED
                    ) {
                        Text(
                            listing.verificationSummary.ifBlank { Verification.label(listing.verificationStatus) },
                            style = MaterialTheme.typography.labelMedium,
                            color = verificationColor(listing.verificationStatus),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
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
}

/** The account's own verification state, and the way to earn the badge. */
@Composable
private fun VerificationCard(
    status: String,
    notes: String,
    verifying: Boolean,
    phoneVerified: Boolean,
    onRequest: () -> Unit,
    onConfirmPhone: () -> Unit,
) {
    val tint = verificationColor(status)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.08f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (status == Verification.VERIFIED) "Your account is verified" else "Get verified",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            VerificationBadge(status)
        }
        Text(
            notes.ifBlank {
                "Verified accounts get a badge on every listing they post. 86% of people we surveyed said verification is what decides whether they trust a listing."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // A confirmed number is the prerequisite: the badge is meant to mean
        // somebody is reachable, not that they typed a number in.
        if (!phoneVerified) {
            Text(
                "Mobile number not confirmed",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Button(onClick = onConfirmPhone, modifier = Modifier.fillMaxWidth()) {
                Text("Confirm my number")
            }
        } else {
            Text(
                "Mobile number confirmed",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (status != Verification.VERIFIED) {
                Button(onClick = onRequest, enabled = !verifying, modifier = Modifier.fillMaxWidth()) {
                    Text(if (verifying) "Checking…" else "Request verification")
                }
            }
        }
    }
}


/**
 * Confirms a mobile number by SMS. Without it the verified badge was hollow —
 * `phone_verified` was never set by anything.
 */
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
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Mobile number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "We'll text you a 6-digit code. Confirming your number is what earns the verified badge — people can tell a real poster from a throwaway.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("6-digit code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sent to ${state.phoneCodeSentTo}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.phoneDevCode?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Development code: $it (no SMS gateway configured)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
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
                enabled = !state.phoneBusy &&
                    (if (awaitingCode) code.length == 6 else phone.count { it.isDigit() } >= 10),
            ) { Text(if (awaitingCode) "Confirm" else "Send code") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

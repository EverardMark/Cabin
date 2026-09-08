package com.cabin.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cabin.app.ui.theme.SoftSecondary

/** Booking a viewing — 57% asked for in-app scheduling. Shared by Home and the listing detail. */
@Composable
fun BookViewingDialog(onDismiss: () -> Unit, onSubmit: (days: Long, hour: Int, note: String) -> Unit) {
    var days by remember { mutableFloatStateOf(1f) }
    var hour by remember { mutableFloatStateOf(14f) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request a viewing") },
        text = {
            Column {
                Text("In ${days.toInt()} day(s), at ${hour.toInt()}:00", style = MaterialTheme.typography.labelLarge)
                Slider(value = days, onValueChange = { days = it }, valueRange = 1f..30f)
                Slider(value = hour, onValueChange = { hour = it }, valueRange = 7f..20f)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note for the poster") },
                    minLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "The poster has to accept before it's confirmed. Never pay anything before you've seen the property.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SoftSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(days.toLong(), hour.toInt(), note) }) { Text("Request") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A plain message dialog. */
@Composable
fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

package com.ahmedhillawi.myshoppinglist.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.ahmedhillawi.myshoppinglist.R

// Extracted out of ShoppingListScreen to keep that composable's own cognitive complexity down.
@Composable
fun DeleteItemDialog(itemName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = { Text(stringResource(R.string.delete_confirm, itemName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = Color.Red) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

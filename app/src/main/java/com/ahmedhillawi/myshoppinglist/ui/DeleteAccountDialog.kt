package com.ahmedhillawi.myshoppinglist.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.ahmedhillawi.myshoppinglist.R

// Extracted out of ShoppingListScreen to keep that composable's own cognitive complexity down.
// The consequence differs by the caller's own role: an owner deleting their account takes the
// whole household and every item with it (household_id cascades all the way down, see the
// delete_own_household_data migration); a member deleting their account only removes their own
// membership, leaving the household intact for everyone else. The actual deletion call/sign-out
// stays with the caller (ShoppingListScreen) since it's a real side-effecting operation tied to
// the screen's overall lifecycle, not this dialog's own concern.
@Composable
fun DeleteAccountDialog(
    householdName: String,
    myRole: String?,
    isDeletingAccount: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeletingAccount) onDismiss() },
        title = { Text(stringResource(R.string.delete_account_title)) },
        text = {
            val messageRes = if (myRole == "owner") {
                R.string.delete_account_confirm_owner
            } else {
                R.string.delete_account_confirm_member
            }
            Text(stringResource(messageRes, householdName))
        },
        confirmButton = {
            TextButton(enabled = !isDeletingAccount, onClick = onConfirm) {
                Text(stringResource(R.string.delete_account_button), color = Color.Red)
            }
        },
        dismissButton = {
            TextButton(enabled = !isDeletingAccount, onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

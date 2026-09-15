package com.ahmedhillawi.myshoppinglist.ui

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ahmedhillawi.myshoppinglist.R
import com.ahmedhillawi.myshoppinglist.domain.Household
import com.ahmedhillawi.myshoppinglist.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class HouseholdIdParam(@SerialName("p_household_id") val householdId: String)

// Extracted out of ShoppingListScreen to keep that composable's own cognitive complexity down.
// Owns its own memberCount/memberLimit state -- fetched via the same SECURITY DEFINER functions
// the household_members insert policy itself uses to enforce the cap (see household_plans
// migration), so this always matches what would actually be allowed, not a separately-maintained
// copy. Nothing outside this dialog needs those values.
@Composable
fun AccountDetailsDialog(
    household: Household,
    onDismiss: () -> Unit,
    onDeleteAccountClick: () -> Unit
) {
    var memberCount by remember { mutableStateOf<Int?>(null) }
    var memberLimit by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(household.id) {
        val householdId = household.id ?: return@LaunchedEffect
        // Purely informational -- if this fails (network hiccup, backend drift), the dialog
        // still works with the member-count line just omitted (see the null check below),
        // rather than crashing the whole app over a non-essential display value.
        try {
            memberCount = supabase.postgrest.rpc(
                "household_member_count", HouseholdIdParam(householdId)
            ).decodeAs<Int>()
            memberLimit = supabase.postgrest.rpc(
                "household_member_limit", HouseholdIdParam(householdId)
            ).decodeAs<Int>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AccountDetailsDialog", "Failed to fetch member count/limit", e)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_details_title)) },
        text = {
            Column {
                val email = supabase.auth.currentUserOrNull()?.email.orEmpty()
                Text(stringResource(R.string.account_email_label, email))
                Spacer(modifier = Modifier.height(8.dp))
                // A user belongs to exactly one household at a time (household_members.user_id
                // is the primary key), so this shows a single household, not a list.
                Text(stringResource(R.string.account_household_label, household.name))
                Spacer(modifier = Modifier.height(8.dp))
                val planLabel = stringResource(
                    if (household.plan == "paid") R.string.plan_paid else R.string.plan_free
                )
                Text(stringResource(R.string.account_plan_label, planLabel))
                val count = memberCount
                val limit = memberLimit
                if (count != null && limit != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.account_member_count_label, count, limit))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDeleteAccountClick) {
                Text(stringResource(R.string.delete_account_button), color = Color.Red)
            }
        }
    )
}

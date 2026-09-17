package com.ahmedhillawi.myshoppinglist.ui

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ahmedhillawi.myshoppinglist.R
import com.ahmedhillawi.myshoppinglist.domain.Household
import com.ahmedhillawi.myshoppinglist.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class HouseholdIdParam(@SerialName("p_household_id") val householdId: String)

@Serializable
private data class HouseholdMemberEmail(
    @SerialName("user_id") val userId: String,
    val email: String,
    val role: String
)

// Extracted out of ShoppingListScreen to keep that composable's own cognitive complexity down.
// A full screen rather than a dialog (matching e.g. WhatsApp's settings) -- ShoppingListScreen
// swaps its whole content for this one while it's shown, rather than overlaying a popup. Laid out
// like a WhatsApp-style settings page: a centered identity header, then a plain list of
// informational/action rows below it. Owns its own memberCount/memberLimit/memberEmails state --
// fetched via the same SECURITY DEFINER functions the household_members insert policy itself uses
// to enforce the cap (see household_plans migration), so this always matches what would actually
// be allowed, not a separately-maintained copy. Nothing outside this screen needs those values.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    household: Household,
    myRole: String?,
    onBack: () -> Unit,
    onDeleteAccountClick: () -> Unit
) {
    BackHandler(onBack = onBack)

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isOwner = myRole == "owner"

    var memberCount by remember { mutableStateOf<Int?>(null) }
    var memberLimit by remember { mutableStateOf<Int?>(null) }
    var memberEmails by remember { mutableStateOf<List<HouseholdMemberEmail>>(emptyList()) }
    var memberToRemove by remember { mutableStateOf<HouseholdMemberEmail?>(null) }
    var showPlanComparison by remember { mutableStateOf(false) }

    if (showPlanComparison) {
        PlanComparisonScreen(
            currentPlan = household.plan,
            onBack = { showPlanComparison = false }
        )
        return
    }

    LaunchedEffect(household.id) {
        val householdId = household.id ?: return@LaunchedEffect
        // Purely informational -- if this fails (network hiccup, backend drift), the screen still
        // works with the member row just omitted (see the null check below), rather than
        // crashing the whole app over a non-essential display value.
        try {
            memberCount = supabase.postgrest.rpc(
                "household_member_count", HouseholdIdParam(householdId)
            ).decodeAs<Int>()
            memberLimit = supabase.postgrest.rpc(
                "household_member_limit", HouseholdIdParam(householdId)
            ).decodeAs<Int>()
            // No household id param -- always the caller's own household (see the migration).
            memberEmails = supabase.postgrest.rpc("household_member_emails")
                .decodeList<HouseholdMemberEmail>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AccountScreen", "Failed to fetch member count/limit/emails", e)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // A user belongs to exactly one household at a time (household_members.user_id is
            // the primary key), so the household name below the email is a single value, not a
            // list -- the same role the "@handle" line plays under the name in WhatsApp settings.
            AccountHeader(
                email = supabase.auth.currentUserOrNull()?.email.orEmpty(),
                householdName = household.name
            )

            val planLabel = stringResource(
                if (household.plan == "paid") R.string.plan_paid else R.string.plan_free
            )
            ListItem(
                modifier = Modifier.clickable { showPlanComparison = true },
                leadingContent = { Icon(Icons.Default.WorkspacePremium, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.account_plan_label)) },
                supportingContent = { Text(planLabel) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            val count = memberCount
            val limit = memberLimit
            if (count != null && limit != null) {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Group, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.account_member_count_label, count, limit)) },
                    supportingContent = {
                        Column {
                            memberEmails.forEach { member ->
                                MemberRow(
                                    member = member,
                                    // Owners can't remove themselves or another owner this way --
                                    // there's only ever one owner per household -- the RLS policy
                                    // enforces this for real, this just keeps the button from
                                    // showing where it could never succeed.
                                    canRemove = isOwner && member.role != "owner",
                                    onRemoveClick = { memberToRemove = member }
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ListItem(
                modifier = Modifier.clickable(onClick = onDeleteAccountClick),
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                headlineContent = { Text(stringResource(R.string.delete_account_button), color = Color.Red) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }

    memberToRemove?.let { member ->
        RemoveMemberDialog(
            email = member.email,
            onConfirm = {
                scope.launch {
                    try {
                        supabase.from("household_members").delete { filter { eq("user_id", member.userId) } }
                        memberEmails = memberEmails.filter { it.userId != member.userId }
                        memberCount = memberCount?.let { it - 1 }
                        Toast.makeText(
                            context,
                            context.getString(R.string.member_removed_toast, member.email),
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("AccountScreen", "Failed to remove member", e)
                        Toast.makeText(
                            context,
                            context.getString(R.string.member_removed_error_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    memberToRemove = null
                }
            },
            onDismiss = { memberToRemove = null }
        )
    }
}

// Extracted out of AccountScreen's members supportingContent to keep that composable's own
// cognitive complexity down.
@Composable
private fun MemberRow(member: HouseholdMemberEmail, canRemove: Boolean, onRemoveClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = member.email, style = MaterialTheme.typography.bodyLarge)
        if (canRemove) {
            IconButton(onClick = onRemoveClick) {
                Icon(
                    Icons.Default.PersonRemove,
                    contentDescription = stringResource(R.string.remove_member_button)
                )
            }
        }
    }
}

// Extracted out of AccountScreen to keep that composable's own cognitive complexity down.
@Composable
private fun RemoveMemberDialog(email: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remove_member_title)) },
        text = { Text(stringResource(R.string.remove_member_confirm, email)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.remove_member_button), color = Color.Red)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}

// Extracted out of AccountScreen's content to keep that composable's own cognitive complexity
// down. There's no avatar photo in this app, so the circle shows an initial instead -- the same
// visual slot a profile photo fills in WhatsApp's settings header.
@Composable
private fun AccountHeader(email: String, householdName: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = email.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = email, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = householdName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

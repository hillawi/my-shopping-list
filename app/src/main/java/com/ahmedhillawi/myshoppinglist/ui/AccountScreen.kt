package com.ahmedhillawi.myshoppinglist.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Serializable
private data class HouseholdMemberEmail(
    @SerialName("user_id") val userId: String,
    val email: String
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
    onBack: () -> Unit,
    onDeleteAccountClick: () -> Unit
) {
    BackHandler(onBack = onBack)

    var memberCount by remember { mutableStateOf<Int?>(null) }
    var memberLimit by remember { mutableStateOf<Int?>(null) }
    var memberEmails by remember { mutableStateOf<List<HouseholdMemberEmail>>(emptyList()) }

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
                leadingContent = { Icon(Icons.Default.WorkspacePremium, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.account_plan_label)) },
                supportingContent = { Text(planLabel) },
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
                            memberEmails.forEach { member -> Text(member.email) }
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

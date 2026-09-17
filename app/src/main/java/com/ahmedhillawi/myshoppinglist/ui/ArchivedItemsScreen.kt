package com.ahmedhillawi.myshoppinglist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Unarchive
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmedhillawi.myshoppinglist.R
import com.ahmedhillawi.myshoppinglist.domain.ShoppingItem

// Extracted out of ShoppingListScreen to keep that composable's own cognitive complexity down --
// same full-screen-swap pattern as AccountScreen, reached from the overflow menu. Archiving is a
// paid-plan feature (see PLANS.md / the archive_items migration), but this screen itself never
// hides already-archived items from a downgraded household -- only ShoppingListScreen's swipe
// gesture gates the *archiving* action; getting already-archived items back is always allowed
// (see ShoppingListViewModel.unarchiveItem's doc comment), so a free household still sees this
// screen's list, just with an upsell banner reminding them archiving new items needs paid.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedItemsScreen(
    isPaidPlan: Boolean,
    archivedItems: List<ShoppingItem>,
    onUnarchive: (ShoppingItem) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archived_items_title)) },
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
            if (!isPaidPlan) {
                ArchiveUpsellBanner()
            }

            when {
                archivedItems.isEmpty() -> EmptyArchiveState()
                else -> LazyColumn {
                    items(archivedItems, key = { requireNotNull(it.id) }) { item ->
                        ArchivedItemRow(item = item, onUnarchive = { onUnarchive(item) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveUpsellBanner() {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Icon(
            Icons.Default.WorkspacePremium,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.archive_paid_only_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider()
}

@Composable
private fun ArchivedItemRow(item: ShoppingItem, onUnarchive: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.name) },
        supportingContent = { Text("${item.quantity} ${stringResource(item.unit.resId)}") },
        trailingContent = {
            IconButton(onClick = onUnarchive) {
                Icon(Icons.Default.Unarchive, contentDescription = stringResource(R.string.unarchive_button))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun EmptyArchiveState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 48.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Inventory2,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.archived_empty_message),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

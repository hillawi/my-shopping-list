package com.ahmedhillawi.myshoppinglist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ahmedhillawi.myshoppinglist.R
import com.ahmedhillawi.myshoppinglist.domain.ShoppingCategory
import com.ahmedhillawi.myshoppinglist.domain.ShoppingItem
import kotlinx.coroutines.launch

// Extracted out of ShoppingListScreen to keep that composable's own cognitive complexity down.
// Also de-duplicates the swipe-to-delete row: the active-items and purchased-items sections
// previously repeated the exact same SwipeToDismissBox + ShoppingListItem wiring verbatim.
// The six item callbacks are bundled into ShoppingItemActions rather than exposed individually,
// to stay under SonarQube's 7-parameter threshold.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingItemsList(
    activeItems: Map<ShoppingCategory, List<ShoppingItem>>,
    purchasedItems: List<ShoppingItem>,
    filteredPurchasedItems: List<ShoppingItem>,
    purchasedSearchQuery: String,
    onPurchasedSearchQueryChange: (String) -> Unit,
    actions: ShoppingItemActions
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        activeItems.forEach { (category, items) ->
            stickyHeader { CategoryHeader(category) }

            items(items, key = { "active_${it.id}" }) { item ->
                SwipeToDeleteRow(item = item, actions = actions)
                HorizontalDivider()
            }
        }

        if (purchasedItems.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.history_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = purchasedSearchQuery,
                    onValueChange = onPurchasedSearchQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_purchased_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (purchasedSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { onPurchasedSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search_description))
                            }
                        }
                    },
                    singleLine = true
                )
            }

            if (filteredPurchasedItems.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_purchased_items_match, purchasedSearchQuery),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            items(filteredPurchasedItems, key = { "history_${it.id}" }) { item ->
                SwipeToDeleteRow(item = item, actions = actions)
                HorizontalDivider()
            }
        }
    }
}

// Bundles the six per-item callbacks ShoppingItemsList needs -- see its doc comment.
data class ShoppingItemActions(
    val onSwipeToDelete: (ShoppingItem) -> Unit,
    val onCheckedChange: (ShoppingItem) -> Unit,
    val onImportantToggle: (ShoppingItem) -> Unit,
    val onEdit: (ShoppingItem) -> Unit,
    val onIncrementQuantity: (ShoppingItem) -> Unit,
    val onDecrementQuantity: (ShoppingItem) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(item: ShoppingItem, actions: ShoppingItemActions) {
    val scope = rememberCoroutineScope()
    // Require a deliberate, near-full-width swipe so an errant drag while tapping the
    // checkbox/star doesn't pop the delete dialog.
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.75f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        // onDismiss only fires once the swipe has settled (i.e. after the thumb is released) --
        // unlike confirmValueChange, which fires live mid-drag and would pop the dialog too early.
        onDismiss = {
            actions.onSwipeToDelete(item)
            scope.launch { dismissState.reset() }
        },
        backgroundContent = {
            val color = if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) Color.Red else Color.Transparent
            Box(
                modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        }
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ShoppingListItem(
                item = item,
                onCheckedChange = { actions.onCheckedChange(item) },
                onImportantToggle = { actions.onImportantToggle(item) },
                onEdit = { actions.onEdit(item) },
                onIncrementQuantity = { actions.onIncrementQuantity(item) },
                onDecrementQuantity = { actions.onDecrementQuantity(item) }
            )
        }
    }
}

@Composable
fun CategoryHeader(category: ShoppingCategory) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(category.resId),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

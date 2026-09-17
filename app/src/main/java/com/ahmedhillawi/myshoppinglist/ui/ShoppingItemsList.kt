package com.ahmedhillawi.myshoppinglist.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmedhillawi.myshoppinglist.R
import com.ahmedhillawi.myshoppinglist.domain.ShoppingCategory
import com.ahmedhillawi.myshoppinglist.domain.ShoppingItem
import kotlin.math.abs
import kotlinx.coroutines.launch

// Fraction of a row's width a swipe (or fast flick, see SwipeToDeleteRow) must cover before it
// counts as a delete gesture. Raised from 0.75 once, then brought back down here -- 0.9 needed too
// deliberate a swipe once the fast-flick fix (below) made the threshold apply consistently
// regardless of speed.
private const val SwipeDismissThresholdFraction = 0.75f

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
    isPaidPlan: Boolean,
    actions: ShoppingItemActions
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        activeItems.forEach { (category, items) ->
            stickyHeader { CategoryHeader(category) }

            items(items, key = { "active_${it.id}" }) { item ->
                SwipeActionsRow(item = item, actions = actions, isPaidPlan = isPaidPlan)
                HorizontalDivider()
            }
        }

        // A brand new household (or one that's been fully cleared) would otherwise render as
        // blank space here -- nothing else in this LazyColumn produces any content.
        if (activeItems.isEmpty() && purchasedItems.isEmpty()) {
            item { EmptyListState() }
        }

        if (purchasedItems.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.history_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            items(filteredPurchasedItems, key = { "history_${it.id}" }) { item ->
                SwipeActionsRow(item = item, actions = actions, isPaidPlan = isPaidPlan)
                HorizontalDivider()
            }
        }
    }
}

// Bundles the six per-item callbacks ShoppingItemsList needs -- see its doc comment.
data class ShoppingItemActions(
    val onSwipeToDelete: (ShoppingItem) -> Unit,
    val onSwipeToArchive: (ShoppingItem) -> Unit,
    val onCheckedChange: (ShoppingItem) -> Unit,
    val onImportantToggle: (ShoppingItem) -> Unit,
    val onEdit: (ShoppingItem) -> Unit,
    val onIncrementQuantity: (ShoppingItem) -> Unit,
    val onDecrementQuantity: (ShoppingItem) -> Unit
)

// Swipe EndToStart (the reading-direction reverse -- right-to-left in LTR) to delete, StartToEnd
// to archive -- opposite-direction swipes for opposite-weight actions is a well-worn pattern
// (Gmail/Mail-style) that needs no extra icon on an already icon-dense row. Archiving is
// paid-plan-gated (see PLANS.md / the archive_items migration); a free household still gets the
// gesture and its background color/icon while dragging, but releasing it shows an upgrade toast
// and resets instead of archiving -- teaches the feature exists rather than silently disabling it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionsRow(item: ShoppingItem, actions: ShoppingItemActions, isPaidPlan: Boolean) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    // Assigned right after rememberSwipeToDismissBoxState below, before any gesture can reach the
    // confirmValueChange lambda that reads it -- captured by reference so the (necessarily
    // self-referential) closure below always sees the real state once it's actually invoked.
    var dismissStateRef: SwipeToDismissBoxState? = null

    // Require a deliberate swipe so an errant drag while tapping the checkbox/star doesn't
    // trigger either action. positionalThreshold alone isn't enough for a fast flick, though:
    // Compose's built-in fling behavior hardcodes a 125dp/s velocity threshold with no public way
    // to raise it (AnchoredDraggableDefaults.flingBehavior always completes the dismiss once a
    // flick clears that speed, no matter how little distance it actually covered) --
    // confirmValueChange re-checks the real dragged distance against the measured row width,
    // independent of velocity, so a quick flick needs the same travel a slow drag does. This
    // constructor overload is deprecated in this Material3 version with no non-deprecated
    // replacement that supports a custom veto like this.
    @Suppress("DEPRECATION")
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { targetValue ->
            val state = dismissStateRef
            targetValue == SwipeToDismissBoxValue.Settled || state == null || rowWidthPx <= 0f ||
                abs(state.requireOffset()) >= rowWidthPx * SwipeDismissThresholdFraction
        },
        positionalThreshold = { totalDistance -> totalDistance * SwipeDismissThresholdFraction }
    )
    dismissStateRef = dismissState

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.onSizeChanged { rowWidthPx = it.width.toFloat() },
        // onDismiss only fires once the swipe has settled (i.e. after the thumb is released) --
        // unlike confirmValueChange, which fires live mid-drag and would pop the dialog too early.
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.EndToStart -> actions.onSwipeToDelete(item)
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (isPaidPlan) {
                        actions.onSwipeToArchive(item)
                    } else {
                        Toast.makeText(context, context.getString(R.string.archive_paid_only_toast), Toast.LENGTH_LONG).show()
                    }
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { dismissState.reset() }
        },
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.tertiary
                SwipeToDismissBoxValue.EndToStart -> Color.Red
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Icon(
                        Icons.Default.Archive,
                        contentDescription = stringResource(R.string.archive_action_description),
                        tint = Color.White
                    )
                    SwipeToDismissBoxValue.EndToStart -> Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_button),
                        tint = Color.White
                    )
                    SwipeToDismissBoxValue.Settled -> Unit
                }
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

// Extracted out of ShoppingItemsList to keep that composable's own cognitive complexity down.
@Composable
private fun EmptyListState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_list_message),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.empty_list_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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

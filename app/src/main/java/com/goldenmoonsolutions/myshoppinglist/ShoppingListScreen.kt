package com.goldenmoonsolutions.myshoppinglist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.goldenmoonsolutions.myshoppinglist.domain.ShoppingCategory
import com.goldenmoonsolutions.myshoppinglist.domain.ShoppingItem
import com.goldenmoonsolutions.myshoppinglist.ui.ShoppingListItem
import com.goldenmoonsolutions.myshoppinglist.viewmodel.ShoppingListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel) {
    val activeItems by viewModel.activeItems.collectAsState()
    val purchasedItems by viewModel.purchasedItems.collectAsState()

    var itemName by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf("1") }
    var itemToDelete by remember { mutableStateOf<ShoppingItem?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(ShoppingCategory.GENERAL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Shopping List") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // --- 1. BETTER INPUT LAYOUT (Stacked) ---
            Column(modifier = Modifier.fillMaxWidth()) {
                // Row 1: Name and Qty
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Item Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = itemQuantity,
                        onValueChange = { itemQuantity = it },
                        modifier = Modifier.width(80.dp),
                        label = { Text("Qty") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            // Quick add on Enter
                            if (itemName.isNotBlank()) {
                                viewModel.addOrUpdateItem(itemName, selectedCategory)
                                itemName = ""
                                itemQuantity = "1"
                            }
                        })
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 2: Category and Add Button
                Row(Modifier.fillMaxWidth()) {
                    // Category Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedCard(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(selectedCategory.icon, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(selectedCategory.label)
                            }
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            ShoppingCategory.entries.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(cat.icon, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text(cat.label)
                                        }
                                    },
                                    onClick = {
                                        selectedCategory = cat
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Add Button
                    Button(
                        onClick = {
                            if (itemName.isNotBlank()) {
                                viewModel.addOrUpdateItem(itemName, selectedCategory)
                                itemName = ""
                                itemQuantity = "1"
                            }
                        },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Add")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 2. UNIFIED LIST (Active + History) ---
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // PART A: ACTIVE ITEMS (Grouped)
                activeItems.forEach { (category, items) ->
                    stickyHeader { CategoryHeader(category) }

                    items(items, key = { "active_${it.id}" }) { item ->
                        SwipeToDeleteItem(
                            item = item,
                            onSwipeDelete = { itemToDelete = item },
                            content = {
                                ShoppingListItem(
                                    item = item,
                                    onCheckedChange = { viewModel.togglePurchased(item) },
                                    onDelete = { itemToDelete = item }
                                )
                            }
                        )
                        HorizontalDivider()
                    }
                }

                // PART B: HISTORY / PANTRY (If exists)
                if (purchasedItems.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                "Recently Purchased",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                    }

                    items(purchasedItems, key = { "history_${it.id}" }) { item ->
                        PurchasedHistoryItem(
                            item = item,
                            onRestore = { viewModel.togglePurchased(item) }
                        )
                    }
                }
            }
        }

        // Delete Dialog
        itemToDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text("Delete Item") },
                text = { Text("Permanently delete '${item.name}'?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeItem(item)
                        itemToDelete = null
                    }) { Text("Delete", color = Color.Red) }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
}

// --- HELPER COMPOSABLES ---

@Composable
fun SwipeToDeleteItem(
    item: ShoppingItem,
    onSwipeDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it != SwipeToDismissBoxValue.Settled) {
                onSwipeDelete()
                false // Don't dismiss immediately, let the dialog handle it
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = 0.8f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
            }
        }
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) { content() }
    }
}

@Composable
fun PurchasedHistoryItem(item: ShoppingItem, onRestore: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = TextDecoration.LineThrough,
                    color = Color.Gray
                )
            )
        },
        leadingContent = {
            Icon(Icons.Default.Refresh, contentDescription = "Restore", tint = Color.Gray)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onRestore() }
    )
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
                text = category.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
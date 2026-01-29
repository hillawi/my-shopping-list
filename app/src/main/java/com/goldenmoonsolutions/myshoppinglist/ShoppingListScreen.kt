package com.goldenmoonsolutions.myshoppinglist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goldenmoonsolutions.myshoppinglist.domain.ShoppingCategory
import com.goldenmoonsolutions.myshoppinglist.domain.ShoppingItem
import com.goldenmoonsolutions.myshoppinglist.ui.ShoppingListItem
import com.goldenmoonsolutions.myshoppinglist.viewmodel.ShoppingListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel) {
    val activeItems by viewModel.activeItems.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    var itemName by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf("1") }
    var itemToDelete by remember { mutableStateOf<ShoppingItem?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(ShoppingCategory.GENERAL) }

    val items by viewModel.items.collectAsStateWithLifecycle()

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
            // --- 1. INPUT SECTION ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Name Input
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Item Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Quantity Input
                OutlinedTextField(
                    value = itemQuantity,
                    onValueChange = { itemQuantity = it },
                    modifier = Modifier.width(80.dp),
                    label = { Text("Qty") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (itemName.isNotBlank()) {
                                viewModel.addOrUpdateItem(itemName, selectedCategory)
                                itemName = ""
                                itemQuantity = "1"
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(modifier = Modifier.weight(0.8f)) {
                    OutlinedCard(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(selectedCategory.label)
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ShoppingCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(cat.icon, contentDescription = null, modifier = Modifier.size(18.dp))
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

                // Add Button
                Button(
                    onClick = {
                        if (itemName.isNotBlank()) {
                            viewModel.addOrUpdateItem(itemName, selectedCategory)
                            itemName = ""
                            itemQuantity = "1"
                        }
                    },
                    modifier = Modifier.height(56.dp) // Match height of TextFields
                ) {
                    Text("Add")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 2. THE LIST ---
            // If the list is empty, show a helpful message
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Your list is empty!", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    activeItems.forEach { (category, items) ->
                            // 1. Category Header
                            stickyHeader { CategoryHeader(category) }

                            // 2. Items in this category
                            items(items = items, key = { item -> "${item.category}_${item.id}" }) { item ->
                                // 1. Create the state for this specific item's swipe
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { dismissValue ->
                                        // Only trigger if swiped significantly to the end/start
                                        if (dismissValue != SwipeToDismissBoxValue.Settled) {
                                            itemToDelete = item
                                            false
                                        } else {
                                            false
                                        }
                                    },
                                    // This adjusts how far you have to swipe (0.5 = 50% of the width)
                                    positionalThreshold = { distance -> distance * 0.5f }
                                )

                                // 2. Wrap the item in the SwipeToDismissBox
                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = true,
                                    enableDismissFromEndToStart = true,
                                    backgroundContent = {
                                        // Background only shows when swiping
                                        val alignment = when (dismissState.dismissDirection) {
                                            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                            else -> Alignment.Center
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Red.copy(alpha = 0.8f)) // Slight transparency
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = alignment
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                                        }
                                    }
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surface // Forces a solid background
                                    ) {
                                        ShoppingListItem(
                                            item = item,
                                            onCheckedChange = { viewModel.togglePurchased(item) },
                                            onDelete = { itemToDelete = item }
                                        )
                                    }
                                }

                                HorizontalDivider()
                            }
                    }
                }
            }
        }

        itemToDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { itemToDelete = null }, // Hide dialog if user taps outside
                title = { Text("Delete Item") },
                text = { Text("Are you sure you want to remove '${item.name}' from your list?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.removeItem(item)
                            itemToDelete = null // Hide dialog
                        }
                    ) {
                        Text("Delete", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("Cancel")
                    }
                }
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
                text = category.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/*
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
}*/

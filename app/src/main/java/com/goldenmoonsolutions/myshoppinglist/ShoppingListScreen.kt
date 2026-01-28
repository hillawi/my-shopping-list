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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.goldenmoonsolutions.myshoppinglist.ui.ShoppingListItem
import com.goldenmoonsolutions.myshoppinglist.viewmodel.ShoppingListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel) {
    // Local state for the text fields (Java dev: think of these as transient form data)
    var itemName by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf("1") }

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
                .padding(paddingValues) // Essential to keep content visible!
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
                    label = { Text("Item") },
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
                                viewModel.addItem(itemName, itemQuantity.toIntOrNull() ?: 1)
                                itemName = ""
                                itemQuantity = "1"
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Add Button
                Button(
                    onClick = {
                        if (itemName.isNotBlank()) {
                            viewModel.addItem(itemName, itemQuantity.toIntOrNull() ?: 1)
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
            if (viewModel.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Your list is empty!", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = viewModel.items,
                        key = { it.id }
                    ) { item ->
                        // 1. Create the state for this specific item's swipe
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                // Only trigger if swiped significantly to the end/start
                                if (dismissValue != SwipeToDismissBoxValue.Settled) {
                                    viewModel.removeItem(item)
                                    true
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
                            // --- THE FIX FOR THE TRANSPARENCY ---
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface // Forces a solid background
                            ) {
                                ShoppingListItem(
                                    item = item,
                                    onCheckedChange = { viewModel.toggleItem(item, it) },
                                    onDelete = { viewModel.removeItem(item) }
                                )
                            }
                        }

                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
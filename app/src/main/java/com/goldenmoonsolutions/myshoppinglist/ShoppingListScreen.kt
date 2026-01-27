package com.goldenmoonsolutions.myshoppinglist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        topBar = { TopAppBar(title = { Text("My Shopping List") }) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {

            // --- INPUT SECTION ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Item name") }
                )
                TextField(
                    value = itemQuantity,
                    onValueChange = { itemQuantity = it },
                    modifier = Modifier.width(70.dp).padding(horizontal = 4.dp),
                    placeholder = { Text("Qty") }
                )
                Button(onClick = {
                    if (itemName.isNotBlank()) {
                        viewModel.addItem(itemName, itemQuantity.toIntOrNull() ?: 1)
                        itemName = "" // Clear input
                        itemQuantity = "1" // Reset quantity
                    }
                }) {
                    Text("Add")
                }
            }

            // --- THE LIST ---
            LazyColumn {
                items(items = viewModel.items, key = { it.id }) { item ->
                    ShoppingListItem(
                        item = item,
                        onCheckedChange = { isChecked -> viewModel.toggleItem(item, isChecked) },
                        onDelete = { viewModel.removeItem(item) }
                    )
                }
            }
        }
    }
}
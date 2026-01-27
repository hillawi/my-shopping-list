package com.goldenmoonsolutions.myshoppinglist.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goldenmoonsolutions.myshoppinglist.domain.ShoppingItem

@Composable
fun ShoppingListItem (
    item: ShoppingItem,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. The checkbox
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = onCheckedChange
        )

        // 2. The Name and Quantity
        Text(
            text = "${item.name} (x${item.quantity})",
            modifier = Modifier.weight(1f)
        )

        // 3. the Delete Button
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Delete")
        }
    }
}
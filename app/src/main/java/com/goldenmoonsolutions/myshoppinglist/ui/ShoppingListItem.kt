package com.goldenmoonsolutions.myshoppinglist.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.goldenmoonsolutions.myshoppinglist.domain.ShoppingItem

@Composable
fun ShoppingListItem(
    item: ShoppingItem,
    onCheckedChange: (Boolean) -> Unit,
    onImportantToggle: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Display quantity prominently if it's not the default "1"
            if (item.quantity != "1") {
                Text(
                    text = "${item.quantity} ",
                    fontWeight = FontWeight.Bold,
                    color = if (item.isPurchased) Color.Gray else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge
                    )
                }
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (item.isPurchased) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (item.isPurchased) Color.Gray else Color.Unspecified
                    )
                )
            }
        },
        leadingContent = {
            Checkbox(
                checked = item.isPurchased,
                onCheckedChange = { isChecked ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCheckedChange(isChecked)
                }
            )
        },
        trailingContent = {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onImportantToggle()
                }
            ) {
                Icon(
                    imageVector = if(item.isImportant) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "Important",
                    tint = if(item.isImportant) Color(0xFFFF9800) else Color.Gray.copy(alpha = 0.5f)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
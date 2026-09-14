package com.ahmedhillawi.myshoppinglist.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmedhillawi.myshoppinglist.R
import com.ahmedhillawi.myshoppinglist.domain.ShoppingCategory

// Extracted out of ShoppingListScreen to keep that composable's own cognitive complexity down.
// draft/onDraftChange bundle name+quantity+unit+category (see ItemDraft) rather than exposing
// each field/onChange pair separately, to stay under SonarQube's 7-parameter threshold. Owns its
// own category-dropdown-expanded state -- nothing outside this dialog needs it.
@Composable
fun EditItemDialog(
    draft: ItemDraft,
    onDraftChange: (ItemDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.item_name_label)) },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = draft.quantity,
                    onValueChange = { onDraftChange(draft.copy(quantity = it)) },
                    modifier = Modifier.width(120.dp),
                    label = { Text(stringResource(R.string.qty_label)) },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        onClick = { categoryExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(draft.category.icon, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(draft.category.resId),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        ShoppingCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(cat.icon, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text(stringResource(cat.resId))
                                    }
                                },
                                onClick = {
                                    onDraftChange(draft.copy(category = cat))
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.unit_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
                Spacer(Modifier.height(4.dp))
                UnitSelector(selectedUnit = draft.unit, onUnitSelected = { onDraftChange(draft.copy(unit = it)) })
            }
        },
        confirmButton = {
            TextButton(enabled = draft.name.isNotBlank(), onClick = onSave) {
                Text(stringResource(R.string.save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}

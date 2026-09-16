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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ahmedhillawi.myshoppinglist.R
import com.ahmedhillawi.myshoppinglist.domain.MeasurementUnit
import com.ahmedhillawi.myshoppinglist.domain.ShoppingCategory

// Extracted out of ShoppingListScreen to keep that composable's own cognitive complexity down.
// draft/onDraftChange bundle name+quantity+unit+category (see ItemDraft) rather than exposing
// each field/onChange pair separately, to stay under SonarQube's 7-parameter threshold.
// `onSubmitViaKeyboard`/`onSubmitViaButton` are deliberately separate rather than one shared
// callback: the two call sites already behaved slightly differently before this extraction
// (pressing Enter also resets the selected unit back to PCS; the Add button does not) and this
// preserves that exactly rather than silently unifying it.
@Composable
fun AddItemForm(
    draft: ItemDraft,
    onDraftChange: (ItemDraft) -> Unit,
    onSubmitViaKeyboard: () -> Unit,
    onSubmitViaButton: () -> Unit
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ROW A: Name and Qty Inputs
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.item_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = draft.quantity,
                onValueChange = { onDraftChange(draft.copy(quantity = it)) },
                modifier = Modifier.width(100.dp), // Wider for "1.5 kg"
                label = { Text(stringResource(R.string.qty_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (draft.name.isNotBlank()) onSubmitViaKeyboard()
                })
            )
        }

        // ROW B: Unit dropdown -- same OutlinedCard + DropdownMenu presentation as the category
        // picker in ROW C below, rather than the filter-chip row this replaced.
        Box(modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedCard(
                onClick = { unitExpanded = true },
                modifier = Modifier.width(140.dp).height(56.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(draft.unit.resId))
                }
            }
            DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                MeasurementUnit.entries.forEach { unitEnum ->
                    DropdownMenuItem(
                        text = { Text(stringResource(unitEnum.resId)) },
                        onClick = {
                            onDraftChange(draft.copy(unit = unitEnum))
                            unitExpanded = false
                        }
                    )
                }
            }
        }

        // ROW C: Category & Button
        Row(Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
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
                        Text(stringResource(draft.category.resId))
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

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { if (draft.name.isNotBlank()) onSubmitViaButton() },
                modifier = Modifier.height(56.dp)
            ) {
                Text(stringResource(R.string.add_button))
            }
        }
    }
}

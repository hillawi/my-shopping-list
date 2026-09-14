package com.ahmedhillawi.myshoppinglist.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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

        // ROW B: Unit Chips (Smart Suggestions) -- sits right below the inputs for easy tapping.
        LazyRow(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(MeasurementUnit.entries.toTypedArray()) { unitEnum ->
                FilterChip(
                    selected = draft.unit == unitEnum,
                    onClick = { onDraftChange(draft.copy(unit = unitEnum)) },
                    label = { Text(stringResource(unitEnum.resId)) },
                    leadingIcon = if (draft.unit == unitEnum) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
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

@Composable
fun UnitSelector(selectedUnit: MeasurementUnit, onUnitSelected: (MeasurementUnit) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        MeasurementUnit.entries.forEach { unit ->
            FilterChip(
                selected = selectedUnit == unit,
                onClick = { onUnitSelected(unit) },
                label = { Text(stringResource(unit.resId)) }
            )
        }
    }
}

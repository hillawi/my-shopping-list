package com.ahmedhillawi.myshoppinglist

import android.app.LocaleManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.LocaleList
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ahmedhillawi.myshoppinglist.domain.MeasurementUnit
import com.ahmedhillawi.myshoppinglist.domain.ShoppingCategory
import com.ahmedhillawi.myshoppinglist.domain.ShoppingItem
import com.ahmedhillawi.myshoppinglist.ui.ShoppingListItem
import com.ahmedhillawi.myshoppinglist.viewmodel.ShoppingListViewModel
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel) {
    val activeItems by viewModel.activeItems.collectAsState()
    val purchasedItems by viewModel.purchasedItems.collectAsState()

    // State for the Settings Menu
    var menuExpanded by remember { mutableStateOf(false) }

    var selectedUnit by remember { mutableStateOf(MeasurementUnit.PCS) }

    // Get current language
    val currentLocale = AppCompatDelegate.getApplicationLocales()[0]?.language ?: "en"

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val shareList = {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, generateShareText(context, activeItems))
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }

    val copyToClipboard = {
        val textToCopy = generateShareText(context, activeItems)

        scope.launch {
            try {
                val clipEntry = ClipEntry(
                    ClipData.newPlainText("Shopping List", textToCopy)
                )
                clipboard.setClipEntry(clipEntry)
                Toast.makeText(context, context.getString(R.string.copied_toast), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to copy list", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var itemName by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf("1") }
    var itemToDelete by remember { mutableStateOf<ShoppingItem?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(ShoppingCategory.GENERAL) }
    var purchasedSearchQuery by remember { mutableStateOf("") }
    val filteredPurchasedItems = remember(purchasedItems, purchasedSearchQuery) {
        if (purchasedSearchQuery.isBlank()) {
            purchasedItems
        } else {
            purchasedItems.filter { it.name.contains(purchasedSearchQuery, ignoreCase = true) }
        }
    }

    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // 1. Copy Button
                    IconButton(onClick = {copyToClipboard()}) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy List"
                        )
                    }
                    // 2. Share Button
                    IconButton(onClick = { shareList() }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share List"
                        )
                    }
                    // 3. Settings/Language Menu
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            // Inside your TopAppBar actions:
                            val localeManager = context.getSystemService(LocaleManager::class.java)
                            // 1. Get current language tag
                            val currentTag = if (!localeManager.applicationLocales.isEmpty) {
                                localeManager.applicationLocales[0].toLanguageTag()
                            } else "en"
                            val targetLanguageLabel = if (currentTag.contains("ar")) "English" else "العربية"
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = targetLanguageLabel,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                                onClick = {
                                    // 3. Toggle: If it contains "ar", switch to "en", otherwise "ar"
                                    val newTag = if (currentTag.contains("ar")) "en" else "ar"
                                    // 4. Apply the new locale (This triggers Activity recreation automatically!)
                                    localeManager.applicationLocales = LocaleList.forLanguageTags(newTag)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.logout)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Default.ExitToApp, contentDescription = null)},
                                onClick = {
                                    scope.launch {
                                        supabase.auth.signOut()
                                    }
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                enabled = false,
                                text = {
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TIMESTAMP})",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                onClick = {}
                            )
                        }
                    }
                },
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
                // --- 1. INPUT SECTION ---
                Column(modifier = Modifier.fillMaxWidth()) {
                    // ROW A: Name and Qty Inputs
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = itemName,
                            onValueChange = { itemName = it },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.item_name_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedTextField(
                            value = itemQuantity,
                            onValueChange = { itemQuantity = it },
                            modifier = Modifier.width(100.dp), // Wider for "1.5 kg"
                            label = { Text(stringResource(R.string.qty_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (itemName.isNotBlank()) {
                                    viewModel.addOrUpdateItem(
                                        itemName,
                                        itemQuantity,
                                        selectedUnit,
                                        selectedCategory
                                    )
                                    itemName = ""
                                    itemQuantity = "1"
                                    selectedUnit = MeasurementUnit.PCS
                                }
                            })
                        )
                    }

                    // ROW B: Unit Chips (Smart Suggestions)
                    // This sits right below the inputs for easy tapping
                    LazyRow(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(MeasurementUnit.entries.toTypedArray()) { unitEnum ->
                            // Using FilterChip or SuggestionChip
                            FilterChip(
                                selected = selectedUnit == unitEnum,
                                onClick = {
                                    selectedUnit = unitEnum
                                },
                                label = {
                                    // This fetches the translated label automatically
                                    Text(stringResource(unitEnum.resId))
                                },
                                leadingIcon = if (selectedUnit == unitEnum) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }

                    // ROW C: Category & Button
                    Row(Modifier.fillMaxWidth()) {
                        // Category Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedCard(
                                onClick = { expanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(selectedCategory.icon, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(selectedCategory.resId))
                                }
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                                            selectedCategory = cat
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (itemName.isNotBlank()) {
                                    viewModel.addOrUpdateItem(
                                        itemName,
                                        itemQuantity,
                                        selectedUnit,
                                        selectedCategory
                                    )
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
                            // Require a deliberate, near-full-width swipe so an errant drag
                            // while tapping the checkbox/star doesn't pop the delete dialog.
                            val dismissState = rememberSwipeToDismissBoxState(
                                positionalThreshold = { totalDistance -> totalDistance * 0.75f }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                // onDismiss only fires once the swipe has settled (i.e. after
                                // the thumb is released) — unlike confirmValueChange, which
                                // fires live mid-drag and would pop the dialog too early.
                                onDismiss = {
                                    itemToDelete = item // Triggers the AlertDialog
                                    scope.launch { dismissState.reset() }
                                },
                                backgroundContent = {
                                    val color = if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) Color.Red else Color.Transparent
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(color)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                                    }
                                }
                            ) {
                                Surface(color = MaterialTheme.colorScheme.surface) {
                                    // Parameter 'onDelete' is now gone!
                                    ShoppingListItem(
                                        item = item,
                                        onCheckedChange = { viewModel.togglePurchased(item) },
                                        onImportantToggle = { viewModel.toggleImportant(item) }
                                    )
                                }
                            }
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
                            OutlinedTextField(
                                value = purchasedSearchQuery,
                                onValueChange = { purchasedSearchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                placeholder = { Text("Search purchased items") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (purchasedSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { purchasedSearchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                singleLine = true
                            )
                        }

                        if (filteredPurchasedItems.isEmpty()) {
                            item {
                                Text(
                                    "No purchased items match \"$purchasedSearchQuery\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            }
                        }

                        items(filteredPurchasedItems, key = { "history_${it.id}" }) { item ->
                            PurchasedHistoryItem(
                                item = item,
                                onRestore = {
                                    viewModel.togglePurchased(item)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        }
                    }
                }
            }

            // Delete Dialog
            itemToDelete?.let { item ->
                AlertDialog(
                    onDismissRequest = { itemToDelete = null },
                    title = { Text(stringResource(R.string.delete_title)) },
                    text = { Text(stringResource(R.string.delete_confirm, item.name)) },
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
}

fun generateShareText(
    context: Context,
    activeItems: Map<ShoppingCategory, List<ShoppingItem>>): String {
    if (activeItems.isEmpty()) return context.getString(R.string.empty_list_message)

    return buildString {
        appendLine(context.getString(R.string.share_title))
        appendLine("-------------------------")

        activeItems.forEach { (category, items) ->
            appendLine("\n*${context.getString(category.resId)}*") // Bold category
            items.forEach { item ->
                val unitLabel = context.getString(item.unit.resId)
                val qtyPart = if (item.quantity.isNotEmpty()) {
                    "(${item.quantity} $unitLabel) "
                } else {
                    "• "
                }

                appendLine("$qtyPart${item.name}")
            }
        }

        appendLine("\n-------------------------")
        appendLine(context.getString(R.string.last_updated, getFormattedTimestamp(context)))
    }
}

fun getFormattedTimestamp(context: Context): String {
    val current = LocalDateTime.now()
    val locale = context.resources.configuration.locales[0]
    val formatter = DateTimeFormatter.ofPattern("MMM d, hh:mm a", locale)
    return current.format(formatter)
}

// --- HELPER COMPOSABLE ---

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
                text = stringResource(category.resId),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
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
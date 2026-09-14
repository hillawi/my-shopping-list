package com.ahmedhillawi.myshoppinglist

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ahmedhillawi.myshoppinglist.domain.Household
import com.ahmedhillawi.myshoppinglist.domain.MeasurementUnit
import com.ahmedhillawi.myshoppinglist.domain.ShoppingCategory
import com.ahmedhillawi.myshoppinglist.domain.ShoppingItem
import com.ahmedhillawi.myshoppinglist.ui.AccountDetailsDialog
import com.ahmedhillawi.myshoppinglist.ui.AddItemForm
import com.ahmedhillawi.myshoppinglist.ui.DeleteAccountDialog
import com.ahmedhillawi.myshoppinglist.ui.DeleteItemDialog
import com.ahmedhillawi.myshoppinglist.ui.EditItemDialog
import com.ahmedhillawi.myshoppinglist.ui.ItemDraft
import com.ahmedhillawi.myshoppinglist.ui.ShoppingItemActions
import com.ahmedhillawi.myshoppinglist.ui.ShoppingItemsList
import com.ahmedhillawi.myshoppinglist.ui.ShoppingListTopBar
import com.ahmedhillawi.myshoppinglist.viewmodel.HouseholdViewModel
import com.ahmedhillawi.myshoppinglist.viewmodel.ShoppingListViewModel
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Orchestrates the screen's state and wires it into the sub-composables under ui/ -- this used
// to be one ~900-line composable (Cognitive Complexity 125, SonarQube's threshold is 15); each
// major section (top bar, add-item form, the items list, each dialog) now owns its own file, and
// the top-bar actions/delete-account flow below are extracted into plain functions for the same
// reason -- this function's job is just to hold state and connect callbacks, not contain branching
// logic itself. See CLAUDE.md's Testing section for why this seam pattern exists elsewhere too.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel, household: Household, householdViewModel: HouseholdViewModel) {
    val activeItems by viewModel.activeItems.collectAsState()
    val purchasedItems by viewModel.purchasedItems.collectAsState()
    val myRole by householdViewModel.myRole.collectAsState()

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var newItemDraft by remember { mutableStateOf(ItemDraft("", "1", MeasurementUnit.PCS, ShoppingCategory.GENERAL)) }

    var itemToDelete by remember { mutableStateOf<ShoppingItem?>(null) }

    var itemToEdit by remember { mutableStateOf<ShoppingItem?>(null) }
    var editDraft by remember { mutableStateOf(ItemDraft("", "1", MeasurementUnit.PCS, ShoppingCategory.GENERAL)) }

    var showAccountDialog by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }

    var purchasedSearchQuery by remember { mutableStateOf("") }
    val filteredPurchasedItems = remember(purchasedItems, purchasedSearchQuery) {
        if (purchasedSearchQuery.isBlank()) {
            purchasedItems
        } else {
            purchasedItems.filter { it.name.contains(purchasedSearchQuery, ignoreCase = true) }
        }
    }

    fun submitNewItem() {
        viewModel.addOrUpdateItem(newItemDraft.name, newItemDraft.quantity, newItemDraft.unit, newItemDraft.category)
        newItemDraft = newItemDraft.copy(name = "", quantity = "1")
    }

    Scaffold(
        topBar = {
            ShoppingListTopBar(
                householdName = household.name,
                onCopyList = { copyListToClipboard(context, clipboard, scope, activeItems, household.name) },
                onShareList = { shareList(context, activeItems, household.name) },
                onCopyInviteCode = { copyInviteCodeToClipboard(context, clipboard, scope, household.inviteCode) },
                onShowAccountDialog = { showAccountDialog = true },
                onLogout = { scope.launch { supabase.auth.signOut() } }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AddItemForm(
                    draft = newItemDraft,
                    onDraftChange = { newItemDraft = it },
                    onSubmitViaKeyboard = {
                        submitNewItem()
                        newItemDraft = newItemDraft.copy(unit = MeasurementUnit.PCS)
                    },
                    onSubmitViaButton = { submitNewItem() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                ShoppingItemsList(
                    activeItems = activeItems,
                    purchasedItems = purchasedItems,
                    filteredPurchasedItems = filteredPurchasedItems,
                    purchasedSearchQuery = purchasedSearchQuery,
                    onPurchasedSearchQueryChange = { purchasedSearchQuery = it },
                    actions = ShoppingItemActions(
                        onSwipeToDelete = { itemToDelete = it },
                        onCheckedChange = { viewModel.togglePurchased(it) },
                        onImportantToggle = { viewModel.toggleImportant(it) },
                        onEdit = { item ->
                            itemToEdit = item
                            editDraft = ItemDraft(item.name, item.quantity, item.unit, ShoppingCategory.fromString(item.category))
                        },
                        onIncrementQuantity = { viewModel.adjustQuantity(it, increase = true) },
                        onDecrementQuantity = { viewModel.adjustQuantity(it, increase = false) }
                    )
                )
            }

            itemToDelete?.let { item ->
                DeleteItemDialog(
                    itemName = item.name,
                    onConfirm = {
                        viewModel.removeItem(item)
                        itemToDelete = null
                    },
                    onDismiss = { itemToDelete = null }
                )
            }

            itemToEdit?.let { item ->
                EditItemDialog(
                    draft = editDraft,
                    onDraftChange = { editDraft = it },
                    onSave = {
                        viewModel.updateItem(item, editDraft.name, editDraft.quantity, editDraft.unit, editDraft.category)
                        itemToEdit = null
                    },
                    onDismiss = { itemToEdit = null }
                )
            }

            // Account details — editing details is still a planned follow-up, not built here
            // since there's nothing yet to wire it to.
            if (showAccountDialog) {
                AccountDetailsDialog(
                    household = household,
                    onDismiss = { showAccountDialog = false },
                    onDeleteAccountClick = {
                        showAccountDialog = false
                        showDeleteAccountConfirm = true
                    }
                )
            }

            if (showDeleteAccountConfirm) {
                DeleteAccountDialog(
                    householdName = household.name,
                    myRole = myRole,
                    isDeletingAccount = isDeletingAccount,
                    onConfirm = {
                        scope.launch {
                            isDeletingAccount = true
                            deleteAccount(context)
                            isDeletingAccount = false
                            showDeleteAccountConfirm = false
                        }
                    },
                    onDismiss = { showDeleteAccountConfirm = false }
                )
            }
        }
    }
}

private fun copyListToClipboard(
    context: Context,
    clipboard: Clipboard,
    scope: CoroutineScope,
    activeItems: Map<ShoppingCategory, List<ShoppingItem>>,
    householdName: String
) {
    scope.launch {
        val textToCopy = generateShareText(context, activeItems, householdName)
        val copied = copyPlainTextToClipboard(clipboard, "Shopping List", textToCopy)
        val messageRes = if (copied) R.string.copied_toast else R.string.copy_failed_toast
        Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
    }
}

private fun shareList(context: Context, activeItems: Map<ShoppingCategory, List<ShoppingItem>>, householdName: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, generateShareText(context, activeItems, householdName))
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

private fun copyInviteCodeToClipboard(context: Context, clipboard: Clipboard, scope: CoroutineScope, inviteCode: String?) {
    scope.launch {
        val copied = copyPlainTextToClipboard(clipboard, "Household Invite Code", inviteCode.orEmpty())
        val messageRes = if (copied) R.string.invite_code_copied_toast else R.string.copy_failed_toast
        Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
    }
}

private suspend fun deleteAccount(context: Context) {
    try {
        supabase.functions("delete-account")
        supabase.auth.signOut()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.delete_account_error_toast), Toast.LENGTH_SHORT).show()
    }
}

private suspend fun copyPlainTextToClipboard(clipboard: Clipboard, label: String, text: String): Boolean =
    try {
        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
        true
    } catch (e: Exception) {
        false
    }

fun generateShareText(
    context: Context,
    activeItems: Map<ShoppingCategory, List<ShoppingItem>>,
    householdName: String): String {
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
        appendLine(context.getString(R.string.account_household_label, householdName))
        appendLine(context.getString(R.string.last_updated, getFormattedTimestamp(context)))
    }
}

fun getFormattedTimestamp(context: Context): String {
    val current = LocalDateTime.now()
    val locale = context.resources.configuration.locales[0]
    val formatter = DateTimeFormatter.ofPattern("MMM d, hh:mm a", locale)
    return current.format(formatter)
}

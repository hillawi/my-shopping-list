package com.ahmedhillawi.myshoppinglist.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmedhillawi.myshoppinglist.data.ShoppingItemsApi
import com.ahmedhillawi.myshoppinglist.data.SupabaseShoppingItemsApi
import com.ahmedhillawi.myshoppinglist.domain.MeasurementUnit
import com.ahmedhillawi.myshoppinglist.domain.ShoppingCategory
import com.ahmedhillawi.myshoppinglist.domain.ShoppingItem
import com.ahmedhillawi.myshoppinglist.supabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

// ViewModel is kept in memory by the Android OS until the screen is permanently closed.
// `api` defaults to the real Supabase-backed implementation in production (so `by viewModels()`
// in MainActivity needs no factory -- @JvmOverloads generates the no-arg constructor Android's
// default ViewModelProvider.Factory looks for via reflection); tests construct this directly with
// a hand-written fake instead.
class ShoppingListViewModel @JvmOverloads constructor(
    private val api: ShoppingItemsApi = SupabaseShoppingItemsApi(supabase)
) : ViewModel() {
    private val _allItems = MutableStateFlow<List<ShoppingItem>>(emptyList())
    private var householdId: String? = null
    private var itemsJob: Job? = null

    // 1. Active Items (Grouped by Category, important items pinned to the top of each group --
    // sortedByDescending is stable, so ties (same isImportant value) keep their existing relative
    // order rather than being reshuffled).
    val activeItems = _allItems.map { list ->
        list.filter { !it.isPurchased && !it.isArchived }
            .distinctBy { it.id }
            .groupBy { ShoppingCategory.fromString(it.category) }
            .mapValues { (_, items) -> items.sortedByDescending { it.isImportant } }
            .toSortedMap(compareBy { it.order })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // 2. History/Pantry Items (Flat list, most recently purchased first)
    val purchasedItems = _allItems.map { list ->
        list.filter { it.isPurchased && !it.isArchived }
            .sortedByDescending { it.purchasedAt ?: "" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. Archived Items (Flat list, most recently archived first) -- see AccountScreen's Plan
    // row/PLANS.md: archiving is a paid-plan feature, but any already-archived item stays visible
    // and unarchivable regardless of the household's current plan (see the archive_items
    // migration's doc comment for why).
    val archivedItems = _allItems.map { list ->
        list.filter { it.isArchived }
            .sortedByDescending { it.archivedAt ?: "" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Called once the caller's household is known (see MainActivity) — the list can't be
    // observed before that, since every row is scoped to a household.
    fun start(householdId: String) {
        if (this.householdId == householdId) return
        this.householdId = householdId
        itemsJob?.cancel()
        _allItems.value = emptyList()
        observeItems(householdId)
    }

    // Called when the authenticated user changes (sign-out, or a different account signing in on
    // the same device — see MainActivity) so a stale subscription/state from the previous user's
    // household doesn't linger or race with the next one.
    fun reset() {
        itemsJob?.cancel()
        itemsJob = null
        householdId = null
        _allItems.value = emptyList()
    }

    private fun observeItems(householdId: String) {
        itemsJob = api.observeItems(householdId)
            .onEach { _allItems.value = it }
            .launchIn(viewModelScope)
    }

    fun addOrUpdateItem(name: String, quantity: String, unit: MeasurementUnit, category: ShoppingCategory) {
        val householdId = householdId ?: return
        viewModelScope.launch {
            val item = ShoppingItem(
                name = name.trim(),
                category = category.name, // Use .name for consistency with Enum
                quantity = quantity.ifBlank { "1" },
                unit = unit,
                isPurchased = false, // Always bring back to active list
                householdId = householdId
            )
            try {
                api.addOrUpdateItem(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Previously this upsert essentially never failed; the household_item_cap_trigger
                // (see supabase/migrations) now gives it a real, expected failure mode once a
                // household hits its distinct-item limit — this must not crash the app per
                // CLAUDE.md's known-gaps note on this exact function.
                Log.w("ShoppingListViewModel", "Failed to add or update item '$name'", e)
            }
        }
    }

    private fun updateItemLocally(id: Long?, transform: (ShoppingItem) -> ShoppingItem) {
        if (id == null) return // Never matches a real item; guards against colliding on shared null ids.
        _allItems.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    fun togglePurchased(item: ShoppingItem) {
        val id = item.id ?: return
        val newValue = !item.isPurchased
        val newPurchasedAt = if (newValue) Instant.now().toString() else null
        // Checking an item off means it's no longer something to look out for -- clear any "must
        // buy" flag so it doesn't carry over if the same item is bought again later.
        val clearImportant = newValue && item.isImportant
        updateItemLocally(id) {
            it.copy(
                isPurchased = newValue,
                purchasedAt = newPurchasedAt,
                isImportant = if (clearImportant) false else it.isImportant
            )
        }
        viewModelScope.launch {
            try {
                api.setPurchased(id, newValue, newPurchasedAt)
                if (clearImportant) api.setImportant(id, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ShoppingListViewModel", "Failed to toggle purchased for item $id", e)
                updateItemLocally(id) {
                    it.copy(isPurchased = item.isPurchased, purchasedAt = item.purchasedAt, isImportant = item.isImportant)
                }
            }
        }
    }

    fun updateItem(
        item: ShoppingItem,
        newName: String,
        newQuantity: String,
        newUnit: MeasurementUnit,
        newCategory: ShoppingCategory
    ) {
        val id = item.id ?: return
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) return
        val trimmedQuantity = newQuantity.ifBlank { "1" }
        if (trimmedName == item.name && trimmedQuantity == item.quantity &&
            newUnit == item.unit && newCategory.name == item.category
        ) return
        val previous = item
        updateItemLocally(id) {
            it.copy(name = trimmedName, quantity = trimmedQuantity, unit = newUnit, category = newCategory.name)
        }
        viewModelScope.launch {
            try {
                api.updateItem(id, trimmedName, trimmedQuantity, newUnit, newCategory.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ShoppingListViewModel", "Failed to update item $id", e)
                // Revert only the fields this call changed, not the whole item — a concurrent
                // realtime push (e.g. another device toggling isImportant) may have landed on
                // _allItems while this call was in flight, and replacing the whole item with
                // `previous` would silently discard that update.
                updateItemLocally(id) {
                    it.copy(name = previous.name, quantity = previous.quantity, unit = previous.unit, category = previous.category)
                }
            }
        }
    }

    fun adjustQuantity(item: ShoppingItem, increase: Boolean) {
        val current = item.quantity.trim().toDoubleOrNull() ?: return
        val step = item.unit.step
        // coerceAtMost(current) keeps a decrement from ever increasing the value: a quantity
        // typed below one step (free-text edit, no step validation) would otherwise have
        // current - step go negative, get floored back up to `step`, and net *increase*.
        val target = if (increase) current + step else (current - step).coerceAtLeast(step).coerceAtMost(current)
        updateItem(item, item.name, formatQuantity(target), item.unit, ShoppingCategory.fromString(item.category))
    }

    private fun formatQuantity(value: Double): String {
        val oneDecimal = "%.1f".format(Locale.US, value)
        return if (oneDecimal.endsWith(".0")) oneDecimal.dropLast(2) else oneDecimal
    }

    fun removeItem(item: ShoppingItem) {
        val id = item.id ?: return
        viewModelScope.launch {
            api.deleteItem(id)
        }
    }

    fun toggleImportant(item: ShoppingItem) {
        val id = item.id ?: return
        val newValue = !item.isImportant
        updateItemLocally(id) { it.copy(isImportant = newValue) }
        viewModelScope.launch {
            try {
                api.setImportant(id, newValue)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ShoppingListViewModel", "Failed to toggle important for item $id", e)
                updateItemLocally(id) { it.copy(isImportant = item.isImportant) }
            }
        }
    }

    // Archiving itself is gated to the paid plan server-side (see the archive_items migration) --
    // the UI is expected to check the household's plan before ever calling this, but the api call
    // failing outright (rather than silently no-opping) if that check is ever bypassed is exactly
    // what the revert-on-failure below already handles.
    fun archiveItem(item: ShoppingItem) {
        val id = item.id ?: return
        val archivedAt = Instant.now().toString()
        updateItemLocally(id) { it.copy(isArchived = true, archivedAt = archivedAt) }
        viewModelScope.launch {
            try {
                api.setArchived(id, isArchived = true, archivedAt = archivedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ShoppingListViewModel", "Failed to archive item $id", e)
                updateItemLocally(id) { it.copy(isArchived = item.isArchived, archivedAt = item.archivedAt) }
            }
        }
    }

    // Unlike archiveItem, allowed regardless of the household's current plan -- see the
    // archive_items migration's doc comment.
    fun unarchiveItem(item: ShoppingItem) {
        val id = item.id ?: return
        updateItemLocally(id) { it.copy(isArchived = false, archivedAt = null) }
        viewModelScope.launch {
            try {
                api.setArchived(id, isArchived = false, archivedAt = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ShoppingListViewModel", "Failed to unarchive item $id", e)
                updateItemLocally(id) { it.copy(isArchived = item.isArchived, archivedAt = item.archivedAt) }
            }
        }
    }
}

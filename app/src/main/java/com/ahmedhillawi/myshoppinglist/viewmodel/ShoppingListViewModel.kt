package com.ahmedhillawi.myshoppinglist.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmedhillawi.myshoppinglist.domain.MeasurementUnit
import com.ahmedhillawi.myshoppinglist.domain.ShoppingCategory
import com.ahmedhillawi.myshoppinglist.domain.ShoppingItem
import com.ahmedhillawi.myshoppinglist.supabase
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.selectAsFlow
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
class ShoppingListViewModel : ViewModel() {
    private val _allItems = MutableStateFlow<List<ShoppingItem>>(emptyList())
    private var householdId: String? = null
    private var itemsJob: Job? = null

    // 1. Active Items (Grouped by Category)
    val activeItems = _allItems.map { list ->
        list.filter { !it.isPurchased }
            .distinctBy { it.id }
            .groupBy { ShoppingCategory.fromString(it.category) }
            .toSortedMap(compareBy { it.order })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // 2. History/Pantry Items (Flat list, most recently purchased first)
    val purchasedItems = _allItems.map { list ->
        list.filter { it.isPurchased }
            .sortedByDescending { it.purchasedAt ?: "" }
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

    @OptIn(SupabaseExperimental::class)
    private fun observeItems(householdId: String) {
        itemsJob = supabase.from("shopping_items")
            .selectAsFlow(
                ShoppingItem::id,
                filter = FilterOperation("household_id", FilterOperator.EQ, householdId)
            )
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
                // 'upsert' checks for a household_id+name conflict. If found, it updates (e.g. setting isPurchased to false)
                supabase.from("shopping_items").upsert(item) {
                    onConflict = "household_id,name"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ShoppingListViewModel", "Failed to add or update item '$name'", e)
            }
        }
    }

    private fun updateItemLocally(id: Long?, transform: (ShoppingItem) -> ShoppingItem) {
        if (id == null) return // Never matches a real item; guards against colliding on shared null ids.
        _allItems.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    fun togglePurchased(item: ShoppingItem) {
        val newValue = !item.isPurchased
        val newPurchasedAt = if (newValue) Instant.now().toString() else null
        updateItemLocally(item.id) { it.copy(isPurchased = newValue, purchasedAt = newPurchasedAt) }
        viewModelScope.launch {
            try {
                supabase.from("shopping_items").update({
                    ShoppingItem::isPurchased setTo newValue
                    ShoppingItem::purchasedAt setTo newPurchasedAt
                }) { filter { ShoppingItem::id eq item.id } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ShoppingListViewModel", "Failed to toggle purchased for item ${item.id}", e)
                updateItemLocally(item.id) { it.copy(isPurchased = item.isPurchased, purchasedAt = item.purchasedAt) }
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
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) return
        val trimmedQuantity = newQuantity.ifBlank { "1" }
        if (trimmedName == item.name && trimmedQuantity == item.quantity &&
            newUnit == item.unit && newCategory.name == item.category
        ) return
        val previous = item
        updateItemLocally(item.id) {
            it.copy(name = trimmedName, quantity = trimmedQuantity, unit = newUnit, category = newCategory.name)
        }
        viewModelScope.launch {
            try {
                supabase.from("shopping_items").update({
                    ShoppingItem::name setTo trimmedName
                    ShoppingItem::quantity setTo trimmedQuantity
                    ShoppingItem::unit setTo newUnit
                    ShoppingItem::category setTo newCategory.name
                }) { filter { ShoppingItem::id eq item.id } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ShoppingListViewModel", "Failed to update item ${item.id}", e)
                // Revert only the fields this call changed, not the whole item — a concurrent
                // realtime push (e.g. another device toggling isImportant) may have landed on
                // _allItems while this call was in flight, and replacing the whole item with
                // `previous` would silently discard that update.
                updateItemLocally(item.id) {
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
        viewModelScope.launch {
            supabase.from("shopping_items").delete {
                filter { eq("id", item.id ?: 0) }
            }
        }
    }

    fun toggleImportant(item: ShoppingItem) {
        val newValue = !item.isImportant
        updateItemLocally(item.id) { it.copy(isImportant = newValue) }
        viewModelScope.launch {
            try {
                supabase.from("shopping_items").update({
                    ShoppingItem::isImportant setTo newValue
                }) { filter { ShoppingItem::id eq item.id } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ShoppingListViewModel", "Failed to toggle important for item ${item.id}", e)
                updateItemLocally(item.id) { it.copy(isImportant = item.isImportant) }
            }
        }
    }
}

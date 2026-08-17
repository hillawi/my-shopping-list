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
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ViewModel is kept in memory by the Android OS until the screen is permanently closed.
class ShoppingListViewModel : ViewModel() {
    private val _allItems = MutableStateFlow<List<ShoppingItem>>(emptyList())

    // 1. Active Items (Grouped by Category)
    val activeItems = _allItems.map { list ->
        list.filter { !it.isPurchased }
            .distinctBy { it.id }
            .groupBy { ShoppingCategory.fromString(it.category) }
            .toSortedMap(compareBy { it.order })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // 2. History/Pantry Items (Flat list, A-Z)
    val purchasedItems = _allItems.map { list ->
        list.filter { it.isPurchased }
            .sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        observeItems()
    }

    @OptIn(SupabaseExperimental::class)
    private fun observeItems() {
        supabase.from("shopping_items")
            .selectAsFlow(ShoppingItem::id)
            .onEach { _allItems.value = it }
            .launchIn(viewModelScope)
    }

    fun addOrUpdateItem(name: String, quantity: String, unit: MeasurementUnit, category: ShoppingCategory) {
        viewModelScope.launch {
            val item = ShoppingItem(
                name = name.trim(),
                category = category.name, // Use .name for consistency with Enum
                quantity = quantity.ifBlank { "1" },
                unit = unit,
                isPurchased = false // Always bring back to active list
            )
            // 'upsert' checks for name conflict. If found, it updates (e.g. setting isPurchased to false)
            supabase.from("shopping_items").upsert(item) {
                onConflict = "name"
            }
        }
    }

    private fun updateItemLocally(id: Long?, transform: (ShoppingItem) -> ShoppingItem) {
        if (id == null) return // Never matches a real item; guards against colliding on shared null ids.
        _allItems.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    fun togglePurchased(item: ShoppingItem) {
        val newValue = !item.isPurchased
        updateItemLocally(item.id) { it.copy(isPurchased = newValue) }
        viewModelScope.launch {
            try {
                supabase.from("shopping_items").update({
                    ShoppingItem::isPurchased setTo newValue
                }) { filter { ShoppingItem::id eq item.id } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ShoppingListViewModel", "Failed to toggle purchased for item ${item.id}", e)
                updateItemLocally(item.id) { it.copy(isPurchased = item.isPurchased) }
            }
        }
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

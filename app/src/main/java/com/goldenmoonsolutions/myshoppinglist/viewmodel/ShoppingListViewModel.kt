package com.goldenmoonsolutions.myshoppinglist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldenmoonsolutions.myshoppinglist.domain.MeasurementUnit
import com.goldenmoonsolutions.myshoppinglist.domain.ShoppingCategory
import com.goldenmoonsolutions.myshoppinglist.domain.ShoppingItem
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.selectAsFlow
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ViewModel is kept in memory by the Android OS until the screen is permanently closed.
class ShoppingListViewModel : ViewModel() {
    // 1. Initialize Supabase
    val supabase = createSupabaseClient(
        supabaseUrl = "https://comxreruiurkxjawwkie.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNvbXhyZXJ1aXVya3hqYXd3a2llIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjgwMzAwMzksImV4cCI6MjA4MzYwNjAzOX0.Pa-viBl4bIDoPUPPgcY__t375smzjCg8FY1t2lsldRg"
    ) {
        httpEngine = OkHttp.create()
        install(Postgrest)
        install(Realtime)
    }

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

    fun togglePurchased(item: ShoppingItem) {
        viewModelScope.launch {
            supabase.from("shopping_items").update({
                ShoppingItem::isPurchased setTo !item.isPurchased
            }) { filter { ShoppingItem::id eq item.id } }
        }
    }

    fun removeItem(item: ShoppingItem) {
        viewModelScope.launch {
            supabase.from("shopping_items").delete {
                filter { eq("id", item.id ?: 0) }
            }
        }
    }
}
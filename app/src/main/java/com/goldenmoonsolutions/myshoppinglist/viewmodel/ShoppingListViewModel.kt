package com.goldenmoonsolutions.myshoppinglist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _items = MutableStateFlow<List<ShoppingItem>>(emptyList())
    val items: StateFlow<List<ShoppingItem>> = _items.asStateFlow()

    private val _allItems = MutableStateFlow<List<ShoppingItem>>(emptyList())
    // UI-ready: Filtered to only show items NOT yet purchased
    val activeItems = _allItems.map { list ->
        list.filter { !it.isPurchased }
            .distinctBy { it.id }
            .groupBy { ShoppingCategory.fromString(it.category) }
            .toSortedMap(compareBy { it.order })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Purchased items (History) - Simple flat list, sorted alphabetically
    val purchasedItems = _allItems.map { list ->
        list.filter { it.isPurchased }
            .sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All items (for search suggestions)
    val catalog = _allItems.asStateFlow()

    init {
        observeItems()
    }

    // 2. Real-time Subscription (The "Sync" Magic)
    @OptIn(SupabaseExperimental::class)
    private fun observeItems() {
        supabase.from("shopping_items")
            .selectAsFlow(ShoppingItem::id)
            .onEach { _allItems.value = it }
            .launchIn(viewModelScope)
    }

    fun addItem(name: String, category: ShoppingCategory) {
        viewModelScope.launch {
            val newItem = ShoppingItem(
                name = name.trim(),
                category = category.name,
                isPurchased = false,
                // userEmail = currentUserEmail TODO later
            )

            // Use 'upsert' instead of 'insert'
            // This tells Supabase: "If the name exists, just update the other fields"
            supabase.from("shopping_items").upsert(newItem) {
                onConflict = "name"
            }
        }
    }

    fun addOrUpdateItem(name: String, category: ShoppingCategory) {
        viewModelScope.launch {
            val item = ShoppingItem(
                name = name.trim(),
                category = category.label,
                isPurchased = false // Always bring back to active list
            )
            // 'upsert' updates the row if the 'name' already exists
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
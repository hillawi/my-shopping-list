package com.goldenmoonsolutions.myshoppinglist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldenmoonsolutions.myshoppinglist.domain.ShoppingItem
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.selectAsFlow
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    init {
        observeItems()
    }

    // 2. Real-time Subscription (The "Sync" Magic)
    @OptIn(SupabaseExperimental::class)
    private fun observeItems() {
        // This opens a websocket to Supabase
        supabase.from("shopping_items")
            .selectAsFlow(ShoppingItem::id) // Tracks changes by ID
            .onEach { latestItems ->
                _items.value = latestItems.distinctBy { it.id }
            }
            .launchIn(viewModelScope)
    }

    fun addItem(name: String, qty: Int) {
        viewModelScope.launch {
            val newItem = ShoppingItem(name = name, quantity = qty)
            supabase.from("shopping_items").insert(newItem)
        }
    }

    fun toggleItem(item: ShoppingItem, isPurchased: Boolean) {
        viewModelScope.launch {
            supabase.from("shopping_items").update(
                mapOf("is_purchased" to isPurchased)
            ) {
                filter { eq("id", item.id ?: 0) }
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
}
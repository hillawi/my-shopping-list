package com.ahmedhillawi.myshoppinglist.data

import com.ahmedhillawi.myshoppinglist.domain.MeasurementUnit
import com.ahmedhillawi.myshoppinglist.domain.ShoppingItem
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.flow.Flow

// Everything ShoppingListViewModel needs from the backend, as a seam: the real implementation
// below wraps the actual Supabase calls, and tests use a hand-written in-memory fake instead of a
// mocking library. See CLAUDE.md's Testing section for why this exists and what it deliberately
// does NOT cover (RLS/data-shape behavior -- see the integration tests under src/test's
// `integration` package for that instead).
interface ShoppingItemsApi {
    fun observeItems(householdId: String): Flow<List<ShoppingItem>>
    suspend fun addOrUpdateItem(item: ShoppingItem)
    suspend fun updateItem(id: Long, name: String, quantity: String, unit: MeasurementUnit, category: String)
    suspend fun setPurchased(id: Long, isPurchased: Boolean, purchasedAt: String?)
    suspend fun setImportant(id: Long, isImportant: Boolean)
    suspend fun deleteItem(id: Long)
}

class SupabaseShoppingItemsApi(private val supabase: SupabaseClient) : ShoppingItemsApi {

    @OptIn(SupabaseExperimental::class)
    override fun observeItems(householdId: String): Flow<List<ShoppingItem>> =
        supabase.from("shopping_items").selectAsFlow(
            ShoppingItem::id,
            filter = FilterOperation("household_id", FilterOperator.EQ, householdId)
        )

    override suspend fun addOrUpdateItem(item: ShoppingItem) {
        // Checks for a household_id+name conflict. If found, updates instead (e.g. setting
        // isPurchased back to false when re-adding an item already in the purchased history).
        supabase.from("shopping_items").upsert(item) {
            onConflict = "household_id,name"
        }
    }

    override suspend fun updateItem(id: Long, name: String, quantity: String, unit: MeasurementUnit, category: String) {
        supabase.from("shopping_items").update({
            ShoppingItem::name setTo name
            ShoppingItem::quantity setTo quantity
            ShoppingItem::unit setTo unit
            ShoppingItem::category setTo category
        }) { filter { ShoppingItem::id eq id } }
    }

    override suspend fun setPurchased(id: Long, isPurchased: Boolean, purchasedAt: String?) {
        supabase.from("shopping_items").update({
            ShoppingItem::isPurchased setTo isPurchased
            ShoppingItem::purchasedAt setTo purchasedAt
        }) { filter { ShoppingItem::id eq id } }
    }

    override suspend fun setImportant(id: Long, isImportant: Boolean) {
        supabase.from("shopping_items").update({
            ShoppingItem::isImportant setTo isImportant
        }) { filter { ShoppingItem::id eq id } }
    }

    override suspend fun deleteItem(id: Long) {
        supabase.from("shopping_items").delete {
            filter { eq("id", id) }
        }
    }
}

package com.ahmedhillawi.myshoppinglist.data

import com.ahmedhillawi.myshoppinglist.domain.MeasurementUnit
import com.ahmedhillawi.myshoppinglist.domain.ShoppingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// In-memory stand-in for SupabaseShoppingItemsApi. Mutation methods update the same backing
// MutableStateFlow that observeItems() returns, simulating the realtime round-trip the real
// implementation relies on (a mutation doesn't update local state directly -- it goes through the
// backend and comes back via the observed flow), so tests exercise ShoppingListViewModel exactly
// as it behaves in production: start() must be called before a mutation's effect becomes visible.
class FakeShoppingItemsApi : ShoppingItemsApi {
    private val itemsByHousehold = mutableMapOf<String, MutableStateFlow<List<ShoppingItem>>>()
    private var nextId = 1L

    var addOrUpdateItemError: Throwable? = null
    var updateItemError: Throwable? = null
    var setPurchasedError: Throwable? = null
    var setImportantError: Throwable? = null

    var deleteCallCount = 0
        private set

    private fun flowFor(householdId: String) = itemsByHousehold.getOrPut(householdId) { MutableStateFlow(emptyList()) }

    // Test setup helper -- seeds a household's items directly, as if they already existed before
    // the ViewModel ever subscribed (mirrors joining a household that already has items in it).
    fun seed(householdId: String, items: List<ShoppingItem>) {
        flowFor(householdId).value = items
    }

    override fun observeItems(householdId: String): Flow<List<ShoppingItem>> = flowFor(householdId)

    override suspend fun addOrUpdateItem(item: ShoppingItem) {
        addOrUpdateItemError?.let { throw it }
        val householdId = requireNotNull(item.householdId) { "addOrUpdateItem requires a household id" }
        val flow = flowFor(householdId)
        val existing = flow.value.find { it.name == item.name }
        flow.value = if (existing != null) {
            flow.value.map { if (it.id == existing.id) item.copy(id = existing.id) else it }
        } else {
            flow.value + item.copy(id = nextId++)
        }
    }

    private fun mutateItem(id: Long, transform: (ShoppingItem) -> ShoppingItem) {
        for (flow in itemsByHousehold.values) {
            if (flow.value.any { it.id == id }) {
                flow.value = flow.value.map { if (it.id == id) transform(it) else it }
                return
            }
        }
    }

    override suspend fun updateItem(id: Long, name: String, quantity: String, unit: MeasurementUnit, category: String) {
        updateItemError?.let { throw it }
        mutateItem(id) { it.copy(name = name, quantity = quantity, unit = unit, category = category) }
    }

    override suspend fun setPurchased(id: Long, isPurchased: Boolean, purchasedAt: String?) {
        setPurchasedError?.let { throw it }
        mutateItem(id) { it.copy(isPurchased = isPurchased, purchasedAt = purchasedAt) }
    }

    override suspend fun setImportant(id: Long, isImportant: Boolean) {
        setImportantError?.let { throw it }
        mutateItem(id) { it.copy(isImportant = isImportant) }
    }

    override suspend fun deleteItem(id: Long) {
        deleteCallCount++
        for ((householdId, flow) in itemsByHousehold) {
            if (flow.value.any { it.id == id }) {
                itemsByHousehold[householdId]!!.value = flow.value.filterNot { it.id == id }
                return
            }
        }
    }
}

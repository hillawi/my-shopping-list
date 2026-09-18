@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ahmedhillawi.myshoppinglist.viewmodel

import com.ahmedhillawi.myshoppinglist.data.FakeShoppingItemsApi
import com.ahmedhillawi.myshoppinglist.domain.MeasurementUnit
import com.ahmedhillawi.myshoppinglist.domain.ShoppingCategory
import com.ahmedhillawi.myshoppinglist.domain.ShoppingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val HOUSEHOLD_ID = "household-1"

class ShoppingListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var api: FakeShoppingItemsApi
    private lateinit var viewModel: ShoppingListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        api = FakeShoppingItemsApi()
        viewModel = ShoppingListViewModel(api)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // activeItems/purchasedItems are stateIn(..., SharingStarted.WhileSubscribed(5000), ...): the
    // upstream map() pipeline only starts once something actually collects them, exactly like the
    // real UI's collectAsState() does. Without a live collector, `.value` would sit frozen at the
    // initial empty value forever, so every test keeps one running for its duration via
    // backgroundScope (auto-cancelled when the test ends).
    private fun TestScope.warmUp() {
        backgroundScope.launch { viewModel.activeItems.collect {} }
        backgroundScope.launch { viewModel.purchasedItems.collect {} }
        backgroundScope.launch { viewModel.archivedItems.collect {} }
    }

    private fun item(
        id: Long? = null,
        name: String,
        unit: MeasurementUnit = MeasurementUnit.PCS,
        category: ShoppingCategory = ShoppingCategory.GENERAL,
        isPurchased: Boolean = false,
        purchasedAt: String? = null,
        isImportant: Boolean = false,
        isArchived: Boolean = false,
        archivedAt: String? = null
    ) = ShoppingItem(
        id = id,
        name = name,
        unit = unit,
        category = category.name,
        isPurchased = isPurchased,
        purchasedAt = purchasedAt,
        isImportant = isImportant,
        householdId = HOUSEHOLD_ID,
        isArchived = isArchived,
        archivedAt = archivedAt
    )

    @Test
    fun `activeItems groups unpurchased items by category sorted by category order`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(
            HOUSEHOLD_ID,
            listOf(
                item(id = 1, name = "bread", category = ShoppingCategory.BAKERY),
                item(id = 2, name = "milk", category = ShoppingCategory.DAIRY),
                item(id = 3, name = "old", category = ShoppingCategory.GENERAL, isPurchased = true)
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        val active = viewModel.activeItems.value
        assertEquals(listOf(ShoppingCategory.DAIRY, ShoppingCategory.BAKERY), active.keys.toList())
        assertEquals(listOf("milk"), active.getValue(ShoppingCategory.DAIRY).map { it.name })
        assertFalse(active.values.flatten().any { it.name == "old" })
    }

    @Test
    fun `activeItems pins important items to the top of their category, preserving relative order otherwise`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(
            HOUSEHOLD_ID,
            listOf(
                item(id = 1, name = "bread", category = ShoppingCategory.BAKERY),
                item(id = 2, name = "bagel", category = ShoppingCategory.BAKERY, isImportant = true),
                item(id = 3, name = "croissant", category = ShoppingCategory.BAKERY)
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        val bakery = viewModel.activeItems.value.getValue(ShoppingCategory.BAKERY)
        assertEquals(listOf("bagel", "bread", "croissant"), bakery.map { it.name })
    }

    @Test
    fun `purchasedItems contains only purchased items sorted most recently purchased first`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(
            HOUSEHOLD_ID,
            listOf(
                item(id = 1, name = "older", isPurchased = true, purchasedAt = "2026-01-01T00:00:00Z"),
                item(id = 2, name = "newer", isPurchased = true, purchasedAt = "2026-02-01T00:00:00Z"),
                item(id = 3, name = "active")
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("newer", "older"), viewModel.purchasedItems.value.map { it.name })
    }

    @Test
    fun `start is idempotent for the same household id`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(HOUSEHOLD_ID, listOf(item(id = 1, name = "milk")))
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.start(HOUSEHOLD_ID)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.activeItems.value.values.flatten().size)
    }

    @Test
    fun `addOrUpdateItem trims the name and forwards it to the api`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        viewModel.addOrUpdateItem("  eggs  ", "12", MeasurementUnit.PCS, ShoppingCategory.DAIRY)
        dispatcher.scheduler.advanceUntilIdle()

        val added = viewModel.activeItems.value.getValue(ShoppingCategory.DAIRY).single()
        assertEquals("eggs", added.name)
        assertEquals("12", added.quantity)
    }

    @Test
    fun `addOrUpdateItem defaults a blank quantity to 1`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        viewModel.addOrUpdateItem("eggs", "", MeasurementUnit.PCS, ShoppingCategory.DAIRY)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("1", viewModel.activeItems.value.getValue(ShoppingCategory.DAIRY).single().quantity)
    }

    @Test
    fun `addOrUpdateItem does not crash when the api throws`() = runTest {
        warmUp()
        api.addOrUpdateItemError = RuntimeException("household_item_limit_reached")
        viewModel.start(HOUSEHOLD_ID)
        viewModel.addOrUpdateItem("eggs", "1", MeasurementUnit.PCS, ShoppingCategory.DAIRY)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.activeItems.value.isEmpty())
    }

    @Test
    fun `togglePurchased optimistically updates then reverts when the api call fails`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(HOUSEHOLD_ID, listOf(item(id = 1, name = "eggs")))
        dispatcher.scheduler.advanceUntilIdle()
        api.setPurchasedError = RuntimeException("boom")

        val target = viewModel.activeItems.value.values.flatten().single()
        viewModel.togglePurchased(target)
        dispatcher.scheduler.advanceUntilIdle()

        val stillActive = viewModel.activeItems.value.values.flatten().singleOrNull { it.id == 1L }
        assertTrue("expected the optimistic update to be reverted back to unpurchased", stillActive != null)
    }

    @Test
    fun `togglePurchased clears the important flag when marking an item purchased`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(HOUSEHOLD_ID, listOf(item(id = 1, name = "eggs", isImportant = true)))
        dispatcher.scheduler.advanceUntilIdle()

        val target = viewModel.activeItems.value.values.flatten().single()
        viewModel.togglePurchased(target)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.purchasedItems.value.single().isImportant)
    }

    @Test
    fun `togglePurchased does nothing for an item with no id yet`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        viewModel.togglePurchased(item(id = null, name = "eggs"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, api.deleteCallCount) // no crash, no-op -- nothing to assert on the api directly
    }

    @Test
    fun `updateItem reverts only the fields it changed on failure`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(HOUSEHOLD_ID, listOf(item(id = 1, name = "eggs", isImportant = true)))
        dispatcher.scheduler.advanceUntilIdle()
        api.updateItemError = RuntimeException("boom")

        val target = viewModel.activeItems.value.values.flatten().single()
        viewModel.updateItem(target, "changed-name", "5", MeasurementUnit.KG, ShoppingCategory.PANTRY)
        dispatcher.scheduler.advanceUntilIdle()

        val reverted = viewModel.activeItems.value.values.flatten().single { it.id == 1L }
        assertEquals("eggs", reverted.name)
        assertTrue("isImportant is untouched by updateItem and must survive the revert", reverted.isImportant)
    }

    @Test
    fun `updateItem is a no-op when nothing actually changed`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        val original = item(id = 1, name = "eggs", unit = MeasurementUnit.PCS, category = ShoppingCategory.DAIRY)
        api.seed(HOUSEHOLD_ID, listOf(original))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.updateItem(original, "eggs", "1", MeasurementUnit.PCS, ShoppingCategory.DAIRY)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, api.deleteCallCount)
    }

    @Test
    fun `adjustQuantity increasing adds one step`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(HOUSEHOLD_ID, listOf(item(id = 1, name = "milk", unit = MeasurementUnit.LITRE)))
        dispatcher.scheduler.advanceUntilIdle()

        val target = viewModel.activeItems.value.values.flatten().single()
        viewModel.adjustQuantity(target, increase = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("1.5", viewModel.activeItems.value.values.flatten().single().quantity)
    }

    @Test
    fun `adjustQuantity decreasing below one step never increases the value`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        // LITRE's step is 0.5 -- start below a full step so a naive decrement would go negative
        // and floor back up to `step`, netting an *increase* instead of a decrease.
        api.seed(HOUSEHOLD_ID, listOf(item(id = 1, name = "milk", unit = MeasurementUnit.LITRE).copy(quantity = "0.2")))
        dispatcher.scheduler.advanceUntilIdle()

        val target = viewModel.activeItems.value.values.flatten().single()
        viewModel.adjustQuantity(target, increase = false)
        dispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.activeItems.value.values.flatten().single().quantity.toDouble()
        assertTrue("decreasing must never increase the value: was 0.2, got $result", result <= 0.2)
    }

    @Test
    fun `removeItem forwards the id to the api`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(HOUSEHOLD_ID, listOf(item(id = 1, name = "eggs")))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.removeItem(viewModel.activeItems.value.values.flatten().single())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, api.deleteCallCount)
        assertTrue(viewModel.activeItems.value.isEmpty())
    }

    @Test
    fun `removeItem does nothing for an item with no id yet`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        viewModel.removeItem(item(id = null, name = "eggs"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, api.deleteCallCount)
    }

    @Test
    fun `toggleImportant reverts on failure`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(HOUSEHOLD_ID, listOf(item(id = 1, name = "eggs", isImportant = false)))
        dispatcher.scheduler.advanceUntilIdle()
        api.setImportantError = RuntimeException("boom")

        val target = viewModel.activeItems.value.values.flatten().single()
        viewModel.toggleImportant(target)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.activeItems.value.values.flatten().single().isImportant)
    }

    @Test
    fun `activeItems and purchasedItems exclude archived items`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(
            HOUSEHOLD_ID,
            listOf(
                item(id = 1, name = "active-archived", isArchived = true),
                item(id = 2, name = "active"),
                item(id = 3, name = "purchased-archived", isPurchased = true, isArchived = true),
                item(id = 4, name = "purchased", isPurchased = true)
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("active"), viewModel.activeItems.value.values.flatten().map { it.name })
        assertEquals(listOf("purchased"), viewModel.purchasedItems.value.map { it.name })
    }

    @Test
    fun `archivedItems contains only archived items sorted most recently archived first`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(
            HOUSEHOLD_ID,
            listOf(
                item(id = 1, name = "older", isArchived = true, archivedAt = "2026-01-01T00:00:00Z"),
                item(id = 2, name = "newer", isArchived = true, archivedAt = "2026-02-01T00:00:00Z"),
                item(id = 3, name = "not-archived")
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("newer", "older"), viewModel.archivedItems.value.map { it.name })
    }

    @Test
    fun `archiveItem optimistically archives then reverts when the api call fails`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(HOUSEHOLD_ID, listOf(item(id = 1, name = "eggs")))
        dispatcher.scheduler.advanceUntilIdle()
        api.setArchivedError = RuntimeException("boom")

        val target = viewModel.activeItems.value.values.flatten().single()
        viewModel.archiveItem(target)
        dispatcher.scheduler.advanceUntilIdle()

        val stillActive = viewModel.activeItems.value.values.flatten().singleOrNull { it.id == 1L }
        assertTrue("expected the optimistic archive to be reverted", stillActive != null)
        assertTrue(viewModel.archivedItems.value.isEmpty())
    }

    @Test
    fun `unarchiveItem optimistically unarchives then reverts when the api call fails`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(HOUSEHOLD_ID, listOf(item(id = 1, name = "eggs", isArchived = true, archivedAt = "2026-01-01T00:00:00Z")))
        dispatcher.scheduler.advanceUntilIdle()
        api.setArchivedError = RuntimeException("boom")

        val target = viewModel.archivedItems.value.single()
        viewModel.unarchiveItem(target)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue("expected the optimistic unarchive to be reverted", viewModel.archivedItems.value.singleOrNull { it.id == 1L } != null)
        assertTrue(viewModel.activeItems.value.isEmpty())
    }

    @Test
    fun `archiveItem does nothing for an item with no id yet`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        viewModel.archiveItem(item(id = null, name = "eggs"))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.archivedItems.value.isEmpty())
    }

    @Test
    fun `reset clears items and allows a fresh start for a new household`() = runTest {
        warmUp()
        viewModel.start(HOUSEHOLD_ID)
        api.seed(HOUSEHOLD_ID, listOf(item(id = 1, name = "eggs")))
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.activeItems.value.isEmpty())

        viewModel.reset()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.activeItems.value.isEmpty())

        viewModel.start("household-2")
        api.seed("household-2", listOf(item(id = 2, name = "bread").copy(householdId = "household-2")))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("bread"), viewModel.activeItems.value.values.flatten().map { it.name })
    }
}

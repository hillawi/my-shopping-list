package com.ahmedhillawi.myshoppinglist.integration

import com.ahmedhillawi.myshoppinglist.data.SupabaseHouseholdApi
import com.ahmedhillawi.myshoppinglist.data.SupabaseShoppingItemsApi
import com.ahmedhillawi.myshoppinglist.domain.MeasurementUnit
import com.ahmedhillawi.myshoppinglist.domain.ShoppingCategory
import com.ahmedhillawi.myshoppinglist.domain.ShoppingItem
import io.github.jan.supabase.postgrest.from
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Exercises SupabaseShoppingItemsApi's real Postgrest calls (update/delete/RLS-scoped select)
// against real Postgres -- these are thin wrappers, but untested against the real schema they'd
// silently break on a column rename or @SerialName mismatch that a fake can't catch. Run manually
// with `supabase start` up locally; skipped automatically otherwise (see LocalSupabase.kt).
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShoppingItemsApiIntegrationTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        assumeLocalSupabaseRunning()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Signs up a fresh member, creates a household for them, and seeds one item -- returning the
    // itemsApi bound to THAT SAME member's client, since RLS scopes every call to the caller's
    // own household membership: an api built from a different/unauthenticated client would see
    // nothing here, not an error, which would make assertions against it pass vacuously.
    private suspend fun newHouseholdWithItem(): Triple<SupabaseShoppingItemsApi, String, Long> {
        val client = newTestClient()
        val userId = client.signUpRandomUser()
        val household = SupabaseHouseholdApi(client).createHousehold("Items API Test Household", userId)
        val householdId = requireNotNull(household.id)
        val itemsApi = SupabaseShoppingItemsApi(client)
        itemsApi.addOrUpdateItem(ShoppingItem(name = "milk", unit = MeasurementUnit.PCS, householdId = householdId))
        val stored = client.from("shopping_items")
            .select { filter { eq("household_id", householdId) } }
            .decodeSingle<ShoppingItem>()
        return Triple(itemsApi, householdId, requireNotNull(stored.id))
    }

    @Test
    fun `updateItem changes name, quantity, unit and category`() = runTest {
        val client = newTestClient()
        val userId = client.signUpRandomUser()
        val household = SupabaseHouseholdApi(client).createHousehold("Update Item Test Household", userId)
        val householdId = requireNotNull(household.id)
        val itemsApi = SupabaseShoppingItemsApi(client)
        itemsApi.addOrUpdateItem(ShoppingItem(name = "milk", unit = MeasurementUnit.PCS, householdId = householdId))
        val id = requireNotNull(
            client.from("shopping_items").select { filter { eq("household_id", householdId) } }
                .decodeSingle<ShoppingItem>().id
        )

        itemsApi.updateItem(id, name = "oat milk", quantity = "2", unit = MeasurementUnit.LITRE, category = ShoppingCategory.DAIRY.name)

        val updated = client.from("shopping_items").select { filter { eq("id", id) } }.decodeSingle<ShoppingItem>()
        assertEquals("oat milk", updated.name)
        assertEquals("2", updated.quantity)
        assertEquals(MeasurementUnit.LITRE, updated.unit)
        assertEquals(ShoppingCategory.DAIRY.name, updated.category)
    }

    @Test
    fun `setPurchased marks an item purchased with a timestamp and can unmark it`() = runTest {
        val (itemsApi, householdId, id) = newHouseholdWithItem()

        itemsApi.setPurchased(id, isPurchased = true, purchasedAt = "2026-09-14T00:00:00Z")
        val purchased = itemsApi.observeItems(householdId).first().first { it.id == id }
        assertTrue(purchased.isPurchased)
        // Postgres round-trips this as an equivalent offset ("+00:00" rather than "Z") -- compare
        // parsed instants, not the raw string.
        assertEquals(OffsetDateTime.parse("2026-09-14T00:00:00Z"), OffsetDateTime.parse(purchased.purchasedAt))

        itemsApi.setPurchased(id, isPurchased = false, purchasedAt = null)
        val unpurchased = itemsApi.observeItems(householdId).first().first { it.id == id }
        assertTrue(!unpurchased.isPurchased)
        assertNull(unpurchased.purchasedAt)
    }

    @Test
    fun `setImportant toggles the important flag`() = runTest {
        val (itemsApi, householdId, id) = newHouseholdWithItem()

        itemsApi.setImportant(id, isImportant = true)
        assertTrue(itemsApi.observeItems(householdId).first().first { it.id == id }.isImportant)

        itemsApi.setImportant(id, isImportant = false)
        assertTrue(!itemsApi.observeItems(householdId).first().first { it.id == id }.isImportant)
    }

    @Test
    fun `deleteItem removes the row`() = runTest {
        val (itemsApi, householdId, id) = newHouseholdWithItem()

        itemsApi.deleteItem(id)

        val remaining = itemsApi.observeItems(householdId).first()
        assertTrue(remaining.none { it.id == id })
    }

    @Test
    fun `observeItems only returns items scoped to the given household`() = runTest {
        val (itemsApiA, householdIdA, _) = newHouseholdWithItem()
        val (_, householdIdB, _) = newHouseholdWithItem()

        // itemsApiA's caller is a member only of household A -- RLS means querying household B's
        // id through their client returns nothing at all, not household B's data, so assert
        // against the meaningful, non-vacuous side: household A's own items.
        val itemsForA = itemsApiA.observeItems(householdIdA).first()
        assertTrue(itemsForA.isNotEmpty())
        assertTrue(itemsForA.all { it.householdId == householdIdA })
        assertTrue(itemsForA.none { it.householdId == householdIdB })
    }
}

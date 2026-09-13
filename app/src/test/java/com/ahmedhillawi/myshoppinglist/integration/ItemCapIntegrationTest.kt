package com.ahmedhillawi.myshoppinglist.integration

import com.ahmedhillawi.myshoppinglist.data.SupabaseHouseholdApi
import com.ahmedhillawi.myshoppinglist.data.SupabaseShoppingItemsApi
import com.ahmedhillawi.myshoppinglist.domain.MeasurementUnit
import com.ahmedhillawi.myshoppinglist.domain.ShoppingCategory
import com.ahmedhillawi.myshoppinglist.domain.ShoppingItem
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Exercises the real household_item_cap_trigger (supabase/migrations) against real Postgres --
// the exact kind of RLS/trigger behavior a mocked FakeShoppingItemsApi can't verify. Run manually
// with `supabase start` up locally; skipped automatically otherwise (see LocalSupabase.kt).
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ItemCapIntegrationTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        assumeLocalSupabaseRunning()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a genuinely new item past the cap is rejected, but re-adding an existing name at the cap still succeeds`() = runTest {
        val client = newTestClient()
        val userId = client.signUpRandomUser()
        val householdApi = SupabaseHouseholdApi(client)
        val household = householdApi.createHousehold("Item Cap Test Household", userId)
        val itemsApi = SupabaseShoppingItemsApi(client)

        // Fill to exactly the cap in one bulk insert (fast) rather than 500 individual upserts
        // through the app's own api -- seeding isn't behavior under test here.
        val seedItems = (1..500).map {
            ShoppingItem(
                name = "item_$it",
                unit = MeasurementUnit.PCS,
                category = ShoppingCategory.GENERAL.name,
                householdId = household.id
            )
        }
        client.from("shopping_items").insert(seedItems)

        val rejected = runCatching {
            itemsApi.addOrUpdateItem(
                ShoppingItem(name = "one_too_many", unit = MeasurementUnit.PCS, householdId = household.id)
            )
        }
        assertTrue("a genuinely new item past the cap must be rejected", rejected.isFailure)

        // Re-adding (upserting) an EXISTING name must still succeed even at the cap -- this is
        // the whole reason the cap is a trigger with an existence check, not a plain count(*) >=
        // 500 guard: addOrUpdateItem() always upserts on (household_id, name), so this is the
        // app's actual real-world "add an item already in the list" path.
        val upsertExisting = runCatching {
            itemsApi.addOrUpdateItem(
                ShoppingItem(name = "item_1", quantity = "5", unit = MeasurementUnit.KG, householdId = household.id)
            )
        }
        assertTrue("upserting an existing item name at the cap must still succeed: ${upsertExisting.exceptionOrNull()}", upsertExisting.isSuccess)
    }
}

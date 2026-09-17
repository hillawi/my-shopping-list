package com.ahmedhillawi.myshoppinglist.integration

import com.ahmedhillawi.myshoppinglist.data.SupabaseHouseholdApi
import com.ahmedhillawi.myshoppinglist.data.SupabaseShoppingItemsApi
import com.ahmedhillawi.myshoppinglist.domain.MeasurementUnit
import com.ahmedhillawi.myshoppinglist.domain.ShoppingItem
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

private const val LOCAL_API_URL = "http://127.0.0.1:54321"
private const val LOCAL_SERVICE_ROLE_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU"

// Exercises the real archive RLS policy (supabase/migrations/20260917000000_archive_items.sql)
// against real Postgres -- specifically that archiving requires the paid plan while unarchiving
// doesn't. Run manually with `supabase start` up locally; skipped otherwise.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShoppingItemsArchiveIntegrationTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        assumeLocalSupabaseRunning()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // No client-facing way to change a household's plan (that's a billing/admin concern, not
    // something exposed to a regular authenticated user) -- a service-role client is the only way
    // to arrange a paid household for a test, same rationale as confirmEmailForTest's admin client
    // in LocalSupabase.kt. Only ever used here to set up preconditions, never to perform the
    // archive/unarchive call under test.
    private suspend fun setHouseholdPlan(householdId: String, plan: String) {
        val adminClient = createSupabaseClient(supabaseUrl = LOCAL_API_URL, supabaseKey = LOCAL_SERVICE_ROLE_KEY) {
            httpEngine = OkHttp.create()
            install(Postgrest)
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                enableLifecycleCallbacks = false
            }
        }
        adminClient.from("households").update({ set("plan", plan) }) { filter { eq("id", householdId) } }
    }

    private suspend fun newHouseholdWithItem(): Triple<SupabaseShoppingItemsApi, String, Long> {
        val client = newTestClient()
        val userId = client.signUpRandomUser()
        val household = SupabaseHouseholdApi(client).createHousehold("Archive Test Household", userId)
        val householdId = requireNotNull(household.id)
        val itemsApi = SupabaseShoppingItemsApi(client)
        itemsApi.addOrUpdateItem(ShoppingItem(name = "milk", unit = MeasurementUnit.PCS, householdId = householdId))
        val stored = client.from("shopping_items")
            .select { filter { eq("household_id", householdId) } }
            .decodeSingle<ShoppingItem>()
        return Triple(itemsApi, householdId, requireNotNull(stored.id))
    }

    @Test
    fun `archiving an item is rejected on the free plan`() = runTest {
        val (itemsApi, _, id) = newHouseholdWithItem()

        try {
            itemsApi.setArchived(id, isArchived = true, archivedAt = "2026-09-17T00:00:00Z")
            fail("expected the free-plan household's archive attempt to be rejected by RLS")
        } catch (e: PostgrestRestException) {
            // Expected: the row is visible (USING still matches), but the proposed new row fails
            // WITH CHECK, which Postgres raises as a genuine RLS-violation error (42501) rather
            // than silently updating zero rows the way a USING-only failure would.
        }
    }

    @Test
    fun `archiving an item succeeds once the household is upgraded to paid`() = runTest {
        val (itemsApi, householdId, id) = newHouseholdWithItem()
        setHouseholdPlan(householdId, "paid")

        itemsApi.setArchived(id, isArchived = true, archivedAt = "2026-09-17T00:00:00Z")

        val archived = itemsApi.observeItems(householdId).first().first { it.id == id }
        assertTrue(archived.isArchived)
    }

    @Test
    fun `unarchiving is allowed even after the household is downgraded back to free`() = runTest {
        val (itemsApi, householdId, id) = newHouseholdWithItem()
        setHouseholdPlan(householdId, "paid")
        itemsApi.setArchived(id, isArchived = true, archivedAt = "2026-09-17T00:00:00Z")
        setHouseholdPlan(householdId, "free")

        itemsApi.setArchived(id, isArchived = false, archivedAt = null)

        val unarchived = itemsApi.observeItems(householdId).first().first { it.id == id }
        assertTrue(!unarchived.isArchived)
        assertNull(unarchived.archivedAt)
    }
}

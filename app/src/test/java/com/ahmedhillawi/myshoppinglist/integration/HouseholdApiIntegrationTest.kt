package com.ahmedhillawi.myshoppinglist.integration

import com.ahmedhillawi.myshoppinglist.data.JoinHouseholdResult
import com.ahmedhillawi.myshoppinglist.data.SupabaseHouseholdApi
import kotlinx.coroutines.Dispatchers
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

// Exercises SupabaseHouseholdApi's real Postgrest calls (getMembership/getHousehold/createHousehold)
// against real Postgres and RLS -- companion to HouseholdMemberCapIntegrationTest, which already
// covers joinHousehold's cap/invalid-code paths. Run manually with `supabase start` up locally;
// skipped automatically otherwise (see LocalSupabase.kt).
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HouseholdApiIntegrationTest {

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
    fun `getMembership returns the caller's household_members row after creating a household`() = runTest {
        val client = newTestClient()
        val userId = client.signUpRandomUser()
        val api = SupabaseHouseholdApi(client)
        val household = api.createHousehold("Get Membership Test Household", userId)

        val membership = api.getMembership(userId)

        assertEquals(household.id, membership?.householdId)
        assertEquals(userId, membership?.userId)
        assertEquals("owner", membership?.role)
    }

    @Test
    fun `getMembership returns null for a user with no household`() = runTest {
        val client = newTestClient()
        val userId = client.signUpRandomUser()

        val membership = SupabaseHouseholdApi(client).getMembership(userId)

        assertNull(membership)
    }

    @Test
    fun `getHousehold returns the household by id for a member, including its invite code`() = runTest {
        val client = newTestClient()
        val userId = client.signUpRandomUser()
        val api = SupabaseHouseholdApi(client)
        val created = api.createHousehold("Get Household Test Household", userId)

        val fetched = api.getHousehold(requireNotNull(created.id))

        assertEquals(created.id, fetched?.id)
        assertEquals("Get Household Test Household", fetched?.name)
        assertTrue("a member should be able to read the real invite code", !fetched?.inviteCode.isNullOrBlank())
    }

    @Test
    fun `getHousehold returns null for a household the caller is not a member of`() = runTest {
        val ownerClient = newTestClient()
        val ownerId = ownerClient.signUpRandomUser()
        val household = SupabaseHouseholdApi(ownerClient).createHousehold("Someone Else's Household", ownerId)

        val outsiderClient = newTestClient()
        outsiderClient.signUpRandomUser()

        val fetched = SupabaseHouseholdApi(outsiderClient).getHousehold(requireNotNull(household.id))

        assertNull("RLS should hide another household's row from a non-member", fetched)
    }

    @Test
    fun `createHousehold generates a unique invite code and makes the creator its owner`() = runTest {
        val client = newTestClient()
        val userId = client.signUpRandomUser()
        val api = SupabaseHouseholdApi(client)

        val household = api.createHousehold("  Trimmed Name  ", userId)

        assertEquals("Trimmed Name", household.name)
        assertTrue(!household.inviteCode.isNullOrBlank())
        assertEquals("owner", api.getMembership(userId)?.role)
    }

    @Test
    fun `a member who joins can immediately read the household's invite code`() = runTest {
        val ownerClient = newTestClient()
        val ownerId = ownerClient.signUpRandomUser()
        val ownerApi = SupabaseHouseholdApi(ownerClient)
        val household = ownerApi.createHousehold("Join Then Read Test Household", ownerId)

        val memberClient = newTestClient()
        val memberUserId = memberClient.signUpRandomUser()
        val joinResult = SupabaseHouseholdApi(memberClient).joinHousehold(requireNotNull(household.inviteCode), memberUserId)

        assertTrue(joinResult is JoinHouseholdResult.Success)
        val joined = (joinResult as JoinHouseholdResult.Success).household
        assertEquals(household.inviteCode, joined.inviteCode)
    }
}

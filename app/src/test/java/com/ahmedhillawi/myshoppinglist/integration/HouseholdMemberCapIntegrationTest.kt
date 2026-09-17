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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Exercises the real member-cap RLS policy (supabase/migrations/20260913000000_household_plans.sql)
// against real Postgres -- specifically that hitting the free-plan cap surfaces as
// JoinHouseholdResult.MemberLimitReached (mapped from the RLS violation's Postgres error code),
// not a generic error. Run manually with `supabase start` up locally; skipped otherwise.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HouseholdMemberCapIntegrationTest {

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
    fun `joining a free household past its 2-member cap is rejected as MemberLimitReached`() = runTest {
        val ownerClient = newTestClient()
        val ownerId = ownerClient.signUpRandomUser()
        val ownerApi = SupabaseHouseholdApi(ownerClient)
        val household = ownerApi.createHousehold("Member Cap Test Household", ownerId)
        val inviteCode = requireNotNull(household.inviteCode)

        // Free plan caps at 2 members (see household_member_limit()) -- the owner already counts
        // as one, so a second member should fit and a third should not.
        val secondClient = newTestClient()
        val secondUserId = secondClient.signUpRandomUser()
        val secondResult = SupabaseHouseholdApi(secondClient).joinHousehold(inviteCode, secondUserId)
        assertTrue("the second member should fit under the free-plan cap: $secondResult", secondResult is JoinHouseholdResult.Success)

        val thirdClient = newTestClient()
        val thirdUserId = thirdClient.signUpRandomUser()
        val thirdResult = SupabaseHouseholdApi(thirdClient).joinHousehold(inviteCode, thirdUserId)
        assertEquals(JoinHouseholdResult.MemberLimitReached, thirdResult)
    }

    @Test
    fun `joining with an invite code that matches no household is rejected as InvalidCode`() = runTest {
        val client = newTestClient()
        val userId = client.signUpRandomUser()

        val result = SupabaseHouseholdApi(client).joinHousehold("NOSUCH", userId)

        assertEquals(JoinHouseholdResult.InvalidCode, result)
    }
}

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ahmedhillawi.myshoppinglist.viewmodel

import com.ahmedhillawi.myshoppinglist.data.FakeHouseholdApi
import com.ahmedhillawi.myshoppinglist.data.JoinHouseholdResult
import com.ahmedhillawi.myshoppinglist.domain.Household
import com.ahmedhillawi.myshoppinglist.domain.HouseholdMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private const val USER_ID = "user-1"

class HouseholdViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var api: FakeHouseholdApi
    private lateinit var viewModel: HouseholdViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        api = FakeHouseholdApi()
        viewModel = HouseholdViewModel(api)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `resolveHousehold leaves household null when the user has no membership`() = runTest {
        viewModel.resolveHousehold(USER_ID)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.household.value)
        assertNull(viewModel.myRole.value)
        assertEquals(USER_ID, viewModel.resolvedUserId.value)
        assertEquals(false, viewModel.isLoading.value)
    }

    @Test
    fun `resolveHousehold sets household and role from existing membership`() = runTest {
        val household = Household(id = "household-1", name = "Home", inviteCode = "ABC123")
        api.households[household.id!!] = household
        api.membership = HouseholdMember(householdId = household.id, userId = USER_ID, role = "owner")

        viewModel.resolveHousehold(USER_ID)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(household, viewModel.household.value)
        assertEquals("owner", viewModel.myRole.value)
        assertEquals(USER_ID, viewModel.resolvedUserId.value)
    }

    @Test
    fun `createHousehold sets the caller as owner`() = runTest {
        viewModel.resolveHousehold(USER_ID)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.createHousehold("My Home")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("My Home", viewModel.household.value?.name)
        assertEquals("owner", viewModel.myRole.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `createHousehold surfaces a generic error on failure`() = runTest {
        viewModel.resolveHousehold(USER_ID)
        dispatcher.scheduler.advanceUntilIdle()
        api.createHouseholdError = RuntimeException("boom")

        viewModel.createHousehold("My Home")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HouseholdError.GENERIC, viewModel.error.value)
        assertNull(viewModel.household.value)
    }

    @Test
    fun `joinHousehold with an invalid code sets INVALID_CODE`() = runTest {
        viewModel.resolveHousehold(USER_ID)
        dispatcher.scheduler.advanceUntilIdle()
        api.joinResult = JoinHouseholdResult.InvalidCode

        viewModel.joinHousehold("NOPE")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HouseholdError.INVALID_CODE, viewModel.error.value)
        assertNull(viewModel.household.value)
    }

    @Test
    fun `joinHousehold at the member cap sets MEMBER_LIMIT_REACHED`() = runTest {
        viewModel.resolveHousehold(USER_ID)
        dispatcher.scheduler.advanceUntilIdle()
        api.joinResult = JoinHouseholdResult.MemberLimitReached

        viewModel.joinHousehold("ABC123")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HouseholdError.MEMBER_LIMIT_REACHED, viewModel.error.value)
    }

    @Test
    fun `joinHousehold success sets household and role to member`() = runTest {
        viewModel.resolveHousehold(USER_ID)
        dispatcher.scheduler.advanceUntilIdle()
        val household = Household(id = "household-1", name = "Home", inviteCode = "ABC123")
        api.joinResult = JoinHouseholdResult.Success(household)

        viewModel.joinHousehold("ABC123")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(household, viewModel.household.value)
        assertEquals("member", viewModel.myRole.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `createHousehold does nothing before resolveHousehold has run`() = runTest {
        // No resolveHousehold() call -- currentUserId is still unset.
        viewModel.createHousehold("Too Early")
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.household.value)
    }

    @Test
    fun `clearError resets the error state`() = runTest {
        viewModel.resolveHousehold(USER_ID)
        dispatcher.scheduler.advanceUntilIdle()
        api.joinResult = JoinHouseholdResult.InvalidCode
        viewModel.joinHousehold("NOPE")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.clearError()

        assertNull(viewModel.error.value)
    }
}

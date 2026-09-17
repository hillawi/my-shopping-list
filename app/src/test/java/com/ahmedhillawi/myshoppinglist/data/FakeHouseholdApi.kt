package com.ahmedhillawi.myshoppinglist.data

import com.ahmedhillawi.myshoppinglist.domain.Household
import com.ahmedhillawi.myshoppinglist.domain.HouseholdMember

// In-memory stand-in for SupabaseHouseholdApi. joinHousehold's result is configured directly via
// `joinResult` rather than reimplementing invite-code matching/RLS -- HouseholdViewModel only
// needs to react correctly to each JoinHouseholdResult case, not to the lookup logic itself
// (that lookup logic lives in SupabaseHouseholdApi and is covered by the integration tests
// instead, against real Postgres/RLS).
class FakeHouseholdApi : HouseholdApi {
    var membership: HouseholdMember? = null
    val households = mutableMapOf<String, Household>()
    var joinResult: JoinHouseholdResult = JoinHouseholdResult.Error
    var createHouseholdError: Throwable? = null

    private var nextHouseholdId = 1

    override suspend fun getMembership(userId: String): HouseholdMember? =
        membership?.takeIf { it.userId == userId }

    override suspend fun getHousehold(id: String): Household? = households[id]

    override suspend fun createHousehold(name: String, ownerId: String): Household {
        createHouseholdError?.let { throw it }
        val household = Household(id = "household-${nextHouseholdId++}", name = name.trim(), inviteCode = "ABCDEF")
        households[household.id!!] = household
        membership = HouseholdMember(householdId = household.id, userId = ownerId, role = "owner")
        return household
    }

    override suspend fun joinHousehold(code: String, userId: String): JoinHouseholdResult {
        val result = joinResult
        if (result is JoinHouseholdResult.Success) {
            households[result.household.id!!] = result.household
            membership = HouseholdMember(householdId = result.household.id, userId = userId, role = "member")
        }
        return result
    }
}

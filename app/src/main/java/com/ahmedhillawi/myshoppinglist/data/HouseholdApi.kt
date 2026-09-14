package com.ahmedhillawi.myshoppinglist.data

import com.ahmedhillawi.myshoppinglist.domain.Household
import com.ahmedhillawi.myshoppinglist.domain.HouseholdMember
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Postgres error code for an RLS policy violation -- surfaced here specifically because the
// household_members insert policy enforces the plan's member cap (see
// supabase/migrations/20260913000000_household_plans.sql).
private const val POSTGRES_RLS_VIOLATION_CODE = "42501"

@Serializable
private data class InviteCodeParams(@SerialName("p_code") val code: String)

sealed interface JoinHouseholdResult {
    data class Success(val household: Household) : JoinHouseholdResult
    data object InvalidCode : JoinHouseholdResult
    data object MemberLimitReached : JoinHouseholdResult
    data object Error : JoinHouseholdResult
}

// Everything HouseholdViewModel needs from the backend -- see ShoppingItemsApi's doc comment for
// why this seam exists. joinHousehold returns a typed result rather than throwing
// PostgrestRestException so a hand-written fake never needs to construct Supabase's own exception
// types to exercise the member-cap/invalid-code paths.
interface HouseholdApi {
    suspend fun getMembership(userId: String): HouseholdMember?
    suspend fun getHousehold(id: String): Household?
    suspend fun createHousehold(name: String, ownerId: String): Household
    suspend fun joinHousehold(code: String, userId: String): JoinHouseholdResult
}

class SupabaseHouseholdApi(private val supabase: SupabaseClient) : HouseholdApi {

    override suspend fun getMembership(userId: String): HouseholdMember? =
        supabase.from("household_members")
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<HouseholdMember>()

    override suspend fun getHousehold(id: String): Household? =
        supabase.from("households")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<Household>()

    override suspend fun createHousehold(name: String, ownerId: String): Household {
        val created = supabase.from("households")
            .insert(Household(name = name.trim())) { select() }
            .decodeSingle<Household>()
        supabase.from("household_members").insert(
            HouseholdMember(householdId = created.id!!, userId = ownerId, role = "owner")
        )
        return created
    }

    override suspend fun joinHousehold(code: String, userId: String): JoinHouseholdResult {
        // A regular row select would need the caller to already be a member (see the households
        // RLS policy), which isn't true yet at lookup time -- so this goes through a SECURITY
        // DEFINER RPC that only exposes id/name for an exact invite-code match, instead of
        // widening the table's SELECT policy to "any authenticated user" (which would let
        // invite_codes be enumerated wholesale).
        val found = supabase.postgrest.rpc(
            "find_household_by_invite_code",
            InviteCodeParams(code.trim().uppercase())
        ).decodeSingleOrNull<Household>() ?: return JoinHouseholdResult.InvalidCode

        try {
            supabase.from("household_members").insert(
                HouseholdMember(householdId = found.id!!, userId = userId, role = "member")
            )
        } catch (e: PostgrestRestException) {
            return if (e.code == POSTGRES_RLS_VIOLATION_CODE) {
                JoinHouseholdResult.MemberLimitReached
            } else {
                JoinHouseholdResult.Error
            }
        }

        // Now that membership exists, re-fetch the full row (the RPC above omits invite_code) so
        // the new member can immediately share it too.
        val household = getHousehold(found.id) ?: found
        return JoinHouseholdResult.Success(household)
    }
}

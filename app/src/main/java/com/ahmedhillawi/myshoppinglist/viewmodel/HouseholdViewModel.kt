package com.ahmedhillawi.myshoppinglist.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmedhillawi.myshoppinglist.domain.Household
import com.ahmedhillawi.myshoppinglist.domain.HouseholdMember
import com.ahmedhillawi.myshoppinglist.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class HouseholdError { INVALID_CODE, GENERIC, MEMBER_LIMIT_REACHED }

// Postgres error code for an RLS policy violation — surfaced here specifically because the
// household_members insert policy enforces the plan's member cap (see
// supabase/migrations/20260913000000_household_plans.sql). The only other condition that policy
// checks (user_id = auth.uid()) is always true here since userId is set from the current session,
// so a 42501 at this call site means the cap, not some other permission issue.
private const val POSTGRES_RLS_VIOLATION_CODE = "42501"

@Serializable
private data class InviteCodeParams(@SerialName("p_code") val code: String)

// Resolves, creates, or joins the household the current user belongs to.
// Kept separate from ShoppingListViewModel: household resolution is a distinct
// lifecycle concern (runs once per session, before the list can even be observed).
class HouseholdViewModel : ViewModel() {
    private val _household = MutableStateFlow<Household?>(null)
    val household: StateFlow<Household?> = _household.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<HouseholdError?>(null)
    val error: StateFlow<HouseholdError?> = _error.asStateFlow()

    // Distinct from isLoading (initial resolution): guards createHousehold/joinHousehold
    // against a double-tap firing two concurrent inserts.
    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    // Called from MainActivity whenever the authenticated user id changes (initial sign-in, or a
    // different account signing in after a sign-out on the same device) — the ViewModel survives
    // that transition (it's fetched via `by viewModels()`), so resolution must be re-triggered
    // explicitly rather than relying on init {} running only once per ViewModel instance.
    fun resolveHousehold() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
                val membership = supabase.from("household_members")
                    .select { filter { eq("user_id", userId) } }
                    .decodeSingleOrNull<HouseholdMember>()
                _household.value = membership?.let { fetchHousehold(it.householdId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("HouseholdViewModel", "Failed to resolve household", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchHousehold(id: String): Household? =
        supabase.from("households")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<Household>()

    fun createHousehold(name: String) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _error.value = null
            _isSubmitting.value = true
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
                val created = supabase.from("households")
                    .insert(Household(name = name.trim())) { select() }
                    .decodeSingle<Household>()
                supabase.from("household_members").insert(
                    HouseholdMember(householdId = created.id!!, userId = userId, role = "owner")
                )
                _household.value = created
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("HouseholdViewModel", "Failed to create household", e)
                _error.value = HouseholdError.GENERIC
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun joinHousehold(code: String) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _error.value = null
            _isSubmitting.value = true
            try {
                val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
                // A regular row select would need the caller to already be a member (see the
                // households RLS policy), which isn't true yet at lookup time — so this goes
                // through a SECURITY DEFINER RPC that only exposes id/name for an exact
                // invite-code match, instead of widening the table's SELECT policy to "any
                // authenticated user" (which would let invite_codes be enumerated wholesale).
                val found = supabase.postgrest.rpc(
                    "find_household_by_invite_code",
                    InviteCodeParams(code.trim().uppercase())
                ).decodeSingleOrNull<Household>()
                if (found == null) {
                    _error.value = HouseholdError.INVALID_CODE
                    return@launch
                }
                supabase.from("household_members").insert(
                    HouseholdMember(householdId = found.id!!, userId = userId, role = "member")
                )
                // Now that membership exists, re-fetch the full row (the RPC above omits
                // invite_code) so the new member can immediately share it too.
                _household.value = fetchHousehold(found.id) ?: found
            } catch (e: CancellationException) {
                throw e
            } catch (e: PostgrestRestException) {
                Log.w("HouseholdViewModel", "Failed to join household", e)
                _error.value = if (e.code == POSTGRES_RLS_VIOLATION_CODE) {
                    HouseholdError.MEMBER_LIMIT_REACHED
                } else {
                    HouseholdError.GENERIC
                }
            } catch (e: Exception) {
                Log.w("HouseholdViewModel", "Failed to join household", e)
                _error.value = HouseholdError.GENERIC
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}

package com.ahmedhillawi.myshoppinglist.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmedhillawi.myshoppinglist.data.HouseholdApi
import com.ahmedhillawi.myshoppinglist.data.JoinHouseholdResult
import com.ahmedhillawi.myshoppinglist.data.SupabaseHouseholdApi
import com.ahmedhillawi.myshoppinglist.domain.Household
import com.ahmedhillawi.myshoppinglist.supabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class HouseholdError { INVALID_CODE, GENERIC, MEMBER_LIMIT_REACHED }

// Resolves, creates, or joins the household the current user belongs to.
// Kept separate from ShoppingListViewModel: household resolution is a distinct
// lifecycle concern (runs once per session, before the list can even be observed).
// `api` defaults to the real Supabase-backed implementation in production (so `by viewModels()`
// in MainActivity needs no factory -- @JvmOverloads generates the no-arg constructor Android's
// default ViewModelProvider.Factory looks for via reflection); tests construct this directly with
// a hand-written fake instead.
class HouseholdViewModel @JvmOverloads constructor(
    private val api: HouseholdApi = SupabaseHouseholdApi(supabase)
) : ViewModel() {
    private val _household = MutableStateFlow<Household?>(null)
    val household: StateFlow<Household?> = _household.asStateFlow()

    // The caller's own role ("owner"/"member") in `household` above -- kept alongside it rather
    // than re-derived on demand, since account deletion needs to know it to decide whether
    // deleting the account takes the whole household down or just the caller's membership.
    private val _myRole = MutableStateFlow<String?>(null)
    val myRole: StateFlow<String?> = _myRole.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // The user id `household`/`myRole` above were actually resolved for. This ViewModel survives
    // a sign-out/sign-in within the same Activity (see resolveHousehold()'s doc comment), so
    // MainActivity's very first composition for a newly-signed-in user reads `household` before
    // the LaunchedEffect that calls resolveHousehold() has had a chance to run and clear the
    // *previous* user's household out — a LaunchedEffect is a post-composition side effect, so no
    // amount of resetting inside it can affect that same frame's render decision. Comparing this
    // against the live session's user id (see MainActivity) forces the loading state instead of
    // briefly rendering the previous user's household under the new user's identity, which used
    // to let ShoppingListViewModel.start() fire once for that stale id and permanently no-op on
    // it afterwards (its own idempotency guard treats "already subscribed to this id" as done,
    // even when it was never a legitimate subscription).
    private val _resolvedUserId = MutableStateFlow<String?>(null)
    val resolvedUserId: StateFlow<String?> = _resolvedUserId.asStateFlow()

    private val _error = MutableStateFlow<HouseholdError?>(null)
    val error: StateFlow<HouseholdError?> = _error.asStateFlow()

    // Distinct from isLoading (initial resolution): guards createHousehold/joinHousehold
    // against a double-tap firing two concurrent inserts.
    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    // Set by resolveHousehold() and reused by createHousehold()/joinHousehold() so those don't
    // each need their own way to learn the caller's id -- the caller (MainActivity) already knows
    // it and passes it into resolveHousehold() once.
    private var currentUserId: String? = null

    fun clearError() {
        _error.value = null
    }

    // Called from MainActivity whenever the authenticated user id changes (initial sign-in, or a
    // different account signing in after a sign-out on the same device) — the ViewModel survives
    // that transition (it's fetched via `by viewModels()`), so resolution must be re-triggered
    // explicitly rather than relying on init {} running only once per ViewModel instance.
    fun resolveHousehold(userId: String) {
        currentUserId = userId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val membership = api.getMembership(userId)
                _household.value = membership?.let { api.getHousehold(it.householdId) }
                _myRole.value = membership?.role
                _resolvedUserId.value = userId
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("HouseholdViewModel", "Failed to resolve household", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createHousehold(name: String) {
        if (_isSubmitting.value) return
        val userId = currentUserId ?: return
        viewModelScope.launch {
            _error.value = null
            _isSubmitting.value = true
            try {
                val created = api.createHousehold(name, userId)
                _household.value = created
                _myRole.value = "owner"
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
        val userId = currentUserId ?: return
        viewModelScope.launch {
            _error.value = null
            _isSubmitting.value = true
            try {
                when (val result = api.joinHousehold(code, userId)) {
                    is JoinHouseholdResult.Success -> {
                        _household.value = result.household
                        _myRole.value = "member"
                    }
                    JoinHouseholdResult.InvalidCode -> _error.value = HouseholdError.INVALID_CODE
                    JoinHouseholdResult.MemberLimitReached -> _error.value = HouseholdError.MEMBER_LIMIT_REACHED
                    JoinHouseholdResult.Error -> _error.value = HouseholdError.GENERIC
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("HouseholdViewModel", "Failed to join household", e)
                _error.value = HouseholdError.GENERIC
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}

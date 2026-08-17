---
title: 'Fix checkbox tap lag when marking items purchased'
type: 'bugfix'
created: '2026-08-17'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '3d6707101b2d1032e248152d23452f7b3645bde6'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Tapping the checkbox on an active item to mark it purchased often appears to do nothing on the first tap, requiring multiple attempts. `Checkbox`'s `checked` state is driven directly by `item.isPurchased` from `ShoppingListViewModel`, and `togglePurchased()` only mutates state via a Supabase network call — the checkbox doesn't visually flip until the Realtime echo comes back, so any latency reads as an unresponsive tap. `toggleImportant()` (the star icon) has the identical structural issue.

**Approach:** Make both `togglePurchased()` and `toggleImportant()` update the local `_allItems` state immediately (optimistic update) before their Supabase call, and revert that local change if the call throws. The eventual Realtime echo will simply reconfirm the same value once it arrives.

## Boundaries & Constraints

**Always:**
- Keep the fix contained to `ShoppingListViewModel` — no repository/service layer, no DI, no `UiState` sealed class.
- Preserve the existing trailing-lambda Supabase DSL style for the update call.
- On failure, revert the optimistic change silently (no Toast/snackbar) — consistent with this codebase's existing "log or silently ignore" error-handling posture.

**Ask First:** Nothing outstanding — scope now covers both `togglePurchased()` and `toggleImportant()`.

**Never:**
- Don't touch `addOrUpdateItem()` or `removeItem()` in this pass.
- Don't add a debounce/disable-while-pending mechanism — the optimistic update already makes rapid re-taps behave correctly.
- Don't add new test infrastructure (mocking library, DI seam) to cover this.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Happy path tap (purchased) | `item.isPurchased == false`, user taps checkbox | Checkbox shows checked immediately; Supabase update succeeds; later Realtime echo reconfirms `true` | N/A |
| Happy path tap (important) | `item.isImportant == false`, user taps the star | Star fills in immediately; Supabase update succeeds; later Realtime echo reconfirms `true` | N/A |
| Update call fails | Either toggle above, but the Supabase `update` call throws | UI flips immediately, then reverts once the failure is caught | Exception caught in `viewModelScope.launch`; local state reverted; no crash |
| Rapid double-tap | Two taps on the same control land within the network round-trip window | First tap flips locally; second tap reads the already-updated local state and flips back; both calls independently sent to Supabase | Each call independently try/catch-reverts on its own failure |
| Item removed elsewhere mid-flight | Item's `id` no longer present in `_allItems` when the optimistic update runs | The list `map` finds no matching id and no-ops | N/A, no crash |

</frozen-after-approval>

## Code Map

- `app/src/main/java/com/ahmedhillawi/myshoppinglist/viewmodel/ShoppingListViewModel.kt` -- `togglePurchased()` and `toggleImportant()` currently have no optimistic update and no error handling; both get rewritten to share a new private `updateItemLocally` helper.
- `app/src/main/java/com/ahmedhillawi/myshoppinglist/domain/ShoppingItem.kt` -- `id: Long?` is the match key used by the local optimistic mutation (read-only reference, no changes).

## Tasks & Acceptance

**Execution:**
- [x] `app/src/main/java/com/ahmedhillawi/myshoppinglist/viewmodel/ShoppingListViewModel.kt` -- add a private `updateItemLocally(id, transform)` helper that maps over `_allItems.value` and applies `transform` to the item matching `id`; rewrite `togglePurchased(item)` to call it with the flipped `isPurchased` value before launching the Supabase update, wrapped in `try/catch` that calls `updateItemLocally` again to revert on failure -- fixes the visible tap lag and makes rapid re-taps toggle correctly instead of silently failing to register.
- [x] `app/src/main/java/com/ahmedhillawi/myshoppinglist/viewmodel/ShoppingListViewModel.kt` -- apply the identical pattern to `toggleImportant(item)`, reusing the same `updateItemLocally` helper for the `isImportant` field -- fixes the same class of lag on the star toggle.

**Acceptance Criteria:**
- Given an unpurchased active item, when the user taps its checkbox, then the checkbox shows checked immediately, without waiting for the network round trip.
- Given an unpurchased active item, when the user taps its checkbox and the Supabase update throws, then the checkbox reverts to unchecked and the app does not crash.
- Given a user taps the same item's checkbox twice in quick succession, when the first tap's network call is still pending, then the second tap toggles based on the already-flipped local state rather than the stale pre-tap state.
- Given a non-important active item, when the user taps its star icon, then the star fills in immediately, without waiting for the network round trip.
- Given a non-important active item, when the user taps its star icon and the Supabase update throws, then the star reverts to unfilled and the app does not crash.

## Spec Change Log

## Verification

**Commands:**
- `./gradlew assembleDebug` -- expected: build succeeds with no compile errors.

**Manual checks (if no CLI):**
- Run the app, add an item, tap its checkbox once, confirm it visually checks immediately (before any perceptible network delay).
- Tap an item's star icon once, confirm it visually fills immediately (before any perceptible network delay).
- With the device offline (airplane mode) or Supabase briefly unreachable, tap the checkbox and the star and confirm each flips then reverts shortly after, without a crash.

## Suggested Review Order

**Optimistic update mechanism**

- New shared helper: guards against the null-id collision an adversarial review caught, then does an atomic `StateFlow` update instead of a read-then-set.
  [`ShoppingListViewModel.kt:69-72`](../../app/src/main/java/com/ahmedhillawi/myshoppinglist/viewmodel/ShoppingListViewModel.kt#L69-L72)

**Toggle purchased**

- Optimistic flip happens synchronously before the network call, so the checkbox no longer waits on a round trip to respond.
  [`ShoppingListViewModel.kt:75-76`](../../app/src/main/java/com/ahmedhillawi/myshoppinglist/viewmodel/ShoppingListViewModel.kt#L75-L76)
- Failure path re-throws cancellation, logs the real error, then reverts to the pre-tap value rather than double-negating.
  [`ShoppingListViewModel.kt:82-86`](../../app/src/main/java/com/ahmedhillawi/myshoppinglist/viewmodel/ShoppingListViewModel.kt#L82-L86)

**Toggle important**

- Identical pattern applied to the star toggle, reusing the same helper.
  [`ShoppingListViewModel.kt:100-111`](../../app/src/main/java/com/ahmedhillawi/myshoppinglist/viewmodel/ShoppingListViewModel.kt#L100-L111)

**Imports**

- New imports supporting the atomic update, cancellation handling, and logging.
  [`ShoppingListViewModel.kt:3`](../../app/src/main/java/com/ahmedhillawi/myshoppinglist/viewmodel/ShoppingListViewModel.kt#L3)
  [`ShoppingListViewModel.kt:13`](../../app/src/main/java/com/ahmedhillawi/myshoppinglist/viewmodel/ShoppingListViewModel.kt#L13)
  [`ShoppingListViewModel.kt:20`](../../app/src/main/java/com/ahmedhillawi/myshoppinglist/viewmodel/ShoppingListViewModel.kt#L20)

# Deferred Work

Append-only log of findings surfaced during quick-dev reviews that are real but out of scope for the story that surfaced them.

- source_spec: `_bmad-output/implementation-artifacts/spec-checkbox-toggle-lag.md`
  summary: `togglePurchased`/`toggleImportant` compute their optimistic new value from the `item` parameter snapshot (possibly stale relative to `_allItems.value` at tap time), and the realtime `onEach` in `observeItems()` unconditionally overwrites the entire `_allItems` list — so a stale/interleaved realtime snapshot can transiently overwrite an in-flight optimistic update, or a late failure-revert can stomp a since-confirmed value from another concurrent toggle.
  evidence: Surfaced independently by both Blind Hunter and Edge Case Hunter review of the checkbox-toggle-lag diff. This diff is the first code path to write to `_allItems` outside of the single realtime collector, so the race is newly possible; before this diff there was exactly one writer. Effect is a transient, self-correcting UI flicker (next realtime echo re-syncs), not permanent corruption, so it doesn't block this bug fix — but a proper fix (e.g. tracking in-flight optimistic item ids and having `observeItems()` merge around them instead of blindly overwriting) is a real design decision worth its own pass rather than folding into a "keep it contained, no new abstraction" bugfix spec.

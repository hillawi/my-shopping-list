-- Archive: lets a user file an item away (out of the active list and purchase history) without
-- deleting it, and bring it back later. Paid-plan feature -- see PLANS.md.
--
-- Orthogonal to is_purchased (an archived item can be either): archiving is its own bucket, not a
-- variant of purchase history, so it deliberately bypasses the 30-day retention window below
-- rather than inheriting it.
--
-- Un-archiving (setting is_archived back to false) is allowed regardless of the household's
-- current plan -- only the act of archiving a *new* item requires paid, so a household that
-- downgraded after archiving things never loses the ability to get them back, only to file away
-- more. This mirrors the member-cap gate (public.household_member_count(...) <
-- public.household_member_limit(...) in the household_members insert policy) more than the
-- purchase-history-retention gate: that one hides content on downgrade, this one only blocks the
-- forward action.

alter table shopping_items add column is_archived boolean not null default false;
alter table shopping_items add column archived_at timestamptz;

drop policy if exists "items scoped to own household" on shopping_items;
create policy "items scoped to own household" on shopping_items
  for all to authenticated using (
    household_id = public.current_user_household_id()
    and (
      is_archived
      or not is_purchased
      or purchased_at > now() - interval '30 days'
      or public.household_plan(household_id) = 'paid'
    )
  ) with check (
    household_id = public.current_user_household_id()
    and (not is_archived or public.household_plan(household_id) = 'paid')
  );

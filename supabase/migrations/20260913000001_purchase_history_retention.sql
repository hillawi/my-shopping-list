-- Purchase history retention: free households can only see/act on purchased items from the
-- last 30 days; paid households (see household_plans migration) have no such limit.
--
-- Non-destructive: old purchased items are NOT deleted, only hidden from a free household under
-- RLS — upgrading to paid makes them visible again immediately, no backfill needed. Active
-- (unpurchased) items are never affected regardless of age or plan.

-- SECURITY DEFINER for consistency with the other helpers this policy already relies on
-- (current_user_household_id), and so this doesn't depend on the households table's own SELECT
-- policy staying shaped a particular way — a plain subquery would happen to work today (the
-- caller is already established as a member of this exact household by the time this runs), but
-- decoupling it here means a future change to households' RLS can't silently break this one.
create or replace function public.household_plan(p_household_id uuid)
returns text
language sql
security definer
set search_path = public
stable
as $$
  select plan from households where id = p_household_id
$$;

drop policy if exists "items scoped to own household" on shopping_items;
create policy "items scoped to own household" on shopping_items
  for all to authenticated using (
    household_id = public.current_user_household_id()
    and (
      not is_purchased
      or purchased_at > now() - interval '30 days'
      or public.household_plan(household_id) = 'paid'
    )
  ) with check (
    household_id = public.current_user_household_id()
  );

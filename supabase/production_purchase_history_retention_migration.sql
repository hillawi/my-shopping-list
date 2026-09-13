-- Purchase history retention — PRODUCTION migration. Run manually via the Supabase SQL editor
-- against the production project (production has no tracked migrations).
--
-- Mirrors supabase/migrations/20260913000001_purchase_history_retention.sql (local dev).
-- Requires the household_plans migration (production_household_plans_migration.sql) to already
-- be applied — this reads households.plan.
--
-- Non-destructive: old purchased items are NOT deleted, only hidden from a free household under
-- RLS — upgrading to paid makes them visible again immediately.

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

-- Household item cap — PRODUCTION migration. Run manually via the Supabase SQL editor against
-- the production project (production has no tracked migrations).
--
-- Mirrors supabase/migrations/20260913000002_household_item_cap.sql (local dev).
--
-- Hard safety cap: at most 100 distinct items per household, regardless of plan. This is
-- abuse/bug protection (unbounded row growth), not a monetization lever — flat across free and
-- paid. shopping_items has a unique(household_id, name) constraint and the app always upserts on
-- (household_id, name), so this is really "100 distinct item names ever used by a household",
-- far above what any real household needs.
--
-- Deliberately a BEFORE INSERT trigger, not a condition on the existing shopping_items RLS
-- policy (which is FOR ALL and would otherwise also block ordinary updates once a household is
-- at/over the cap). Handles the upsert-resolves-to-update case explicitly: a row with this
-- (household_id, name) already existing is never blocked, regardless of current count.

create or replace function public.enforce_household_item_cap()
returns trigger
language plpgsql
as $$
begin
  if not exists (
    select 1 from shopping_items
    where household_id = new.household_id and name = new.name
  ) then
    if (select count(*) from shopping_items where household_id = new.household_id) >= 100 then
      raise exception 'household_item_limit_reached' using errcode = 'P0001';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists household_item_cap_trigger on shopping_items;
create trigger household_item_cap_trigger
  before insert on shopping_items
  for each row
  execute function public.enforce_household_item_cap();

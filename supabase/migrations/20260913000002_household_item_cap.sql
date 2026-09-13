-- Hard safety cap: at most 1000 distinct items per household, regardless of plan. This is
-- abuse/bug protection (unbounded row growth), not a monetization lever like the member cap or
-- purchase history retention — flat across free and paid.
--
-- shopping_items has a unique(household_id, name) constraint, and addOrUpdateItem() always
-- upserts on (household_id, name) — so re-adding an existing item name never creates a new row,
-- it updates the existing one. This cap is really "1000 distinct item names ever used by a
-- household", not "1000 purchases" — expected to be far above what any real household needs.
--
-- Deliberately a BEFORE INSERT trigger, not a condition on the existing shopping_items RLS
-- policy: that policy is `FOR ALL` (covers INSERT/UPDATE/DELETE/SELECT alike), so a count check
-- added to its WITH CHECK would also block ordinary updates (toggling purchased, editing
-- quantity) once a household is at/over the cap. A trigger only fires on the INSERT path.
--
-- Correctness note: for `INSERT ... ON CONFLICT DO UPDATE` (what upsert compiles to), Postgres
-- still fires BEFORE INSERT triggers for a row even when it will end up resolving to the UPDATE
-- branch — so this explicitly checks whether a row with this (household_id, name) already
-- exists, and only enforces the cap when this insert would create a genuinely new row.

create or replace function public.enforce_household_item_cap()
returns trigger
language plpgsql
as $$
begin
  if not exists (
    select 1 from shopping_items
    where household_id = new.household_id and name = new.name
  ) then
    if (select count(*) from shopping_items where household_id = new.household_id) >= 1000 then
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

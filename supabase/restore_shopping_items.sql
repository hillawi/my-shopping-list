-- Households migration — POST-migration restore of shopping_items.
--
-- Run this AFTER production_migration.sql, and after a household has been
-- created (open the app, create the household — or insert one directly). It
-- assigns household_id on the pre-migration rows (captured by
-- backup_shopping_items.sql) so the family's existing list becomes visible
-- again under the new household scoping, instead of staying orphaned per the
-- original "fresh start" plan.
--
-- ⚠️ Before running: replace REPLACE_WITH_HOUSEHOLD_ID below with the target
-- household's actual id. Find it with:
--   select id, name, invite_code from households;

do $$
declare
  target_household_id uuid := 'REPLACE_WITH_HOUSEHOLD_ID'::uuid;
  updated_count int;
  skipped_count int;
begin
  -- Only touch rows that are (a) still orphaned on the live table and (b) were
  -- actually present in the pre-migration backup — not just any NULL row, in
  -- case something else produced one after the migration ran.
  update shopping_items si
  set household_id = target_household_id
  from shopping_items_backup b
  where si.id = b.id
    and si.household_id is null
    -- Skip a row if the target household already has an item with the same
    -- name (e.g. someone already re-added "Milk" by hand before this ran) —
    -- the household_id,name unique constraint would otherwise fail the whole
    -- statement. Skipped rows stay orphaned for manual review.
    and not exists (
      select 1 from shopping_items existing
      where existing.household_id = target_household_id
        and existing.name = si.name
    );

  get diagnostics updated_count = row_count;

  select count(*) into skipped_count
  from shopping_items_backup b
  join shopping_items si on si.id = b.id
  where si.household_id is null;

  raise notice 'Restored % item(s) to household %.', updated_count, target_household_id;
  if skipped_count > 0 then
    raise notice '% item(s) still orphaned (name conflict with an existing item in that household) — review manually.', skipped_count;
  end if;
end $$;

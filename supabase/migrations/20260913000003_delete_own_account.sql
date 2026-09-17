-- Account deletion: a user can delete their own account. If they're the owner of their
-- household, deleting the account deletes the whole household and every item in it (for every
-- member). If they're just a member, deleting the account only removes their own membership --
-- the household and its items stay untouched for everyone else.
--
-- Deleting the auth.users row itself requires the Supabase Admin API (service-role key), which
-- only the delete-account Edge Function has access to -- this migration only prepares the
-- Postgres side that function calls first, before it deletes the auth user.

-- shopping_items.household_id had no ON DELETE clause (defaulted to NO ACTION), so deleting a
-- household would fail with a foreign-key violation while it still had items. household_members
-- already cascades on both its FKs (see 20260908000000_households.sql) -- bring shopping_items
-- in line so `delete from households` cascades cleanly to it too.
alter table shopping_items drop constraint shopping_items_household_id_fkey;
alter table shopping_items add constraint shopping_items_household_id_fkey
  foreign key (household_id) references households(id) on delete cascade;

-- SECURITY DEFINER so it can look up and delete the caller's own household_members/households
-- rows regardless of their own RLS visibility, same rationale as current_user_household_id().
-- Scoped entirely by auth.uid() -- there's no parameter, so it can only ever act on the caller's
-- own membership, never anyone else's.
create or replace function public.delete_own_household_data()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_household_id uuid;
  v_role text;
begin
  select household_id, role into v_household_id, v_role
  from household_members
  where user_id = auth.uid();

  if v_household_id is null then
    return; -- no household yet (e.g. mid-onboarding) -- nothing to clean up
  end if;

  if v_role = 'owner' then
    -- Cascades to household_members and shopping_items for this household.
    delete from households where id = v_household_id;
  else
    delete from household_members where user_id = auth.uid();
  end if;
end;
$$;

revoke all on function public.delete_own_household_data() from public;
grant execute on function public.delete_own_household_data() to authenticated;

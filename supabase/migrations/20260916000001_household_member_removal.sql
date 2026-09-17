-- Lets a household's owner remove another (non-owner) member -- the client shows a confirmation
-- dialog first, but the real guarantee is this policy: role <> 'owner' blocks an owner from
-- removing themselves or another owner this way (there's only ever one owner per household,
-- set at creation time -- see the households/household_members schema), and
-- current_user_is_owner() blocks a plain member from removing anyone at all.

create or replace function public.current_user_is_owner()
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1 from household_members where user_id = auth.uid() and role = 'owner'
  )
$$;

create policy "members deletable by household owner" on household_members
  for delete to authenticated using (
    role <> 'owner'
    and household_id = public.current_user_household_id()
    and public.current_user_is_owner()
  );

-- The account screen needs each member's role too, to decide which rows should even offer a
-- remove action (enforcement is the policy above, not this) -- return type is changing so the
-- existing function has to be dropped first rather than just `create or replace`d.
drop function if exists public.household_member_emails();

create function public.household_member_emails()
returns table (user_id uuid, email text, role text)
language sql
security definer
set search_path = public
stable
as $$
  select household_members.user_id, auth.users.email::text, household_members.role
  from household_members
  join auth.users on auth.users.id = household_members.user_id
  where household_members.household_id = public.current_user_household_id()
$$;

revoke all on function household_member_emails() from public;
grant execute on function household_member_emails() to authenticated;

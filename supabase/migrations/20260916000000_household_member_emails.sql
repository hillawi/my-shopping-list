-- The account screen used to show only a member count; now it lists members by email too.
-- auth.users isn't exposed via the REST API's exposed schemas, so joining it to household_members
-- from the client isn't possible even with RLS -- this SECURITY DEFINER function does the join
-- server-side instead. It takes no household id: it always resolves to the caller's own household
-- via current_user_household_id() (see fix_household_members_rls_recursion.sql), so it can't be
-- used to read another household's member emails by passing an arbitrary id.

create or replace function public.household_member_emails()
returns table (user_id uuid, email text)
language sql
security definer
set search_path = public
stable
as $$
  select household_members.user_id, auth.users.email::text
  from household_members
  join auth.users on auth.users.id = household_members.user_id
  where household_members.household_id = public.current_user_household_id()
$$;

revoke all on function household_member_emails() from public;
grant execute on function household_member_emails() to authenticated;

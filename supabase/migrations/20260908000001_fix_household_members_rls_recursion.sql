-- household_members' own SELECT policy queried household_members inside its
-- using() clause, which recurses (Postgres error 42P17) the moment anything
-- (e.g. shopping_items' policy) triggers it as a subquery. Standard fix: look
-- up the caller's household through a SECURITY DEFINER function, which runs
-- with elevated privileges and so bypasses RLS on its own internal select
-- instead of re-entering the recursive policy.

create or replace function public.current_user_household_id()
returns uuid
language sql
security definer
set search_path = public
stable
as $$
  select household_id from household_members where user_id = auth.uid()
$$;

drop policy "members readable within own household" on household_members;
create policy "members readable within own household" on household_members
  for select to authenticated using (
    household_id = public.current_user_household_id()
  );

drop policy "items scoped to own household" on shopping_items;
create policy "items scoped to own household" on shopping_items
  for all to authenticated using (
    household_id = public.current_user_household_id()
  ) with check (
    household_id = public.current_user_household_id()
  );

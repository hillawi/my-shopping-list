-- Household plans: free vs paid, gating how many members a household can have.
--
-- Free: 2 members max (matches "one household, max 2 linked accounts" from the product decision).
-- Paid: 10 members max — a concrete generous cap rather than "unlimited", easy to change below.

alter table households add column plan text not null default 'free' check (plan in ('free', 'paid'));

-- SECURITY DEFINER: both households and household_members are RLS-restricted to members/the
-- household's own creator (see the earlier households/household_members policies) — someone
-- *joining* a household isn't a member yet, so a plain query from their session would see zero
-- rows and always compute a count of 0, silently defeating the cap. These bypass RLS to compute
-- the real count/limit regardless of the caller's own visibility, mirroring why
-- find_household_by_invite_code and current_user_household_id are SECURITY DEFINER too.

create or replace function public.household_member_count(p_household_id uuid)
returns int
language sql
security definer
set search_path = public
stable
as $$
  select count(*)::int from household_members where household_id = p_household_id
$$;

create or replace function public.household_member_limit(p_household_id uuid)
returns int
language sql
security definer
set search_path = public
stable
as $$
  select case (select plan from households where id = p_household_id)
    when 'paid' then 10
    else 2
  end
$$;

-- Re-scope the existing "insert yourself as a member" policy to also enforce the cap. The owner's
-- own insert during household creation is unaffected (count is 0 at that point, always < limit).
drop policy if exists "members insertable as self" on household_members;
create policy "members insertable as self" on household_members
  for insert to authenticated with check (
    user_id = auth.uid()
    and public.household_member_count(household_id) < public.household_member_limit(household_id)
  );

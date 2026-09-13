-- Household plans: free vs paid, gating how many members a household can have — PRODUCTION
-- migration. Run this manually via the Supabase SQL editor against the production project
-- (production has no tracked migrations).
--
-- Mirrors supabase/migrations/20260913000000_household_plans.sql (local dev), with IF EXISTS /
-- IF NOT EXISTS guards so it's safe to re-run if something goes wrong partway through.
--
-- Free: 2 members max. Paid: 10 members max — a concrete generous cap rather than "unlimited",
-- easy to change below (two `10`s, in household_member_limit).

do $$
begin
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'households' and column_name = 'plan'
  ) then
    alter table households add column plan text not null default 'free' check (plan in ('free', 'paid'));
  end if;
end $$;

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

drop policy if exists "members insertable as self" on household_members;
create policy "members insertable as self" on household_members
  for insert to authenticated with check (
    user_id = auth.uid()
    and public.household_member_count(household_id) < public.household_member_limit(household_id)
  );

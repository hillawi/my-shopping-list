-- Households: per-account shared shopping lists — PRODUCTION migration.
--
-- Run this manually via the Supabase SQL editor against the production project
-- (production has no tracked migrations — see CLAUDE.md "Git workflow"). This is
-- NOT picked up by `supabase start`/`supabase db reset`: those only read files
-- under supabase/migrations/, which mirror this same end-state for local dev.
--
-- Safe to run as one script/transaction: table/policy/function creation uses
-- guards (IF NOT EXISTS, DO blocks) wherever production's current state can't be
-- verified from here, so a partial prior run or an already-realtime-enabled
-- shopping_items table won't cause it to fail outright.
--
-- IMPORTANT — read before running:
--   1. This does NOT backfill existing shopping_items rows into a household.
--      Per the plan, that's deliberate: existing rows become orphaned (still in
--      the table, invisible under the new RLS) the moment this ships, since
--      nothing links them to a household_id yet. Whichever family member opens
--      the updated app first creates a household and shares the invite code
--      with the others — they'll start a fresh list, not see old items.
--   2. Review the DO block that drops shopping_items' old single-column
--      `unique(name)` constraint and its old permissive RLS policies below
--      before running, in case production's shopping_items has been customized
--      beyond what CLAUDE.md describes.

-- ── households / household_members ──────────────────────────────────────────

create table if not exists households (
  id uuid primary key default gen_random_uuid(),
  name text not null default 'My Household',
  invite_code text not null unique default upper(substr(md5(random()::text), 1, 6)),
  created_at timestamptz not null default now(),
  -- Lets the creator read back their own just-inserted row (RETURNING is subject to the SELECT
  -- policy below) before the household_members row that would otherwise grant that exists.
  created_by uuid not null default auth.uid() references auth.users(id)
);

create table if not exists household_members (
  user_id uuid primary key references auth.users(id) on delete cascade,
  household_id uuid not null references households(id) on delete cascade,
  role text not null default 'member' check (role in ('owner', 'member')),
  joined_at timestamptz not null default now()
);

alter table households enable row level security;
alter table household_members enable row level security;

-- ── shopping_items: add household scoping ───────────────────────────────────

alter table shopping_items add column if not exists household_id uuid references households(id);

-- Drop the old single-column unique(name) constraint, whatever it's actually called on
-- production — found dynamically instead of hardcoding a guessed name.
do $$
declare
  c record;
begin
  for c in
    select con.conname
    from pg_constraint con
    join pg_class rel on rel.oid = con.conrelid
    join pg_namespace nsp on nsp.oid = rel.relnamespace
    where nsp.nspname = 'public'
      and rel.relname = 'shopping_items'
      and con.contype = 'u'
      and (
        select array_agg(attname::text order by attnum)
        from pg_attribute
        where attrelid = con.conrelid and attnum = any(con.conkey)
      ) = array['name']
  loop
    execute format('alter table public.shopping_items drop constraint %I', c.conname);
    raise notice 'Dropped old unique(name) constraint: %', c.conname;
  end loop;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'shopping_items_household_name_key'
  ) then
    alter table shopping_items add constraint shopping_items_household_name_key unique (household_id, name);
  end if;
end $$;

alter table shopping_items enable row level security;

-- ── RLS: households ──────────────────────────────────────────────────────────
-- Deliberately NOT "using (true)": that would let any authenticated user read every
-- household's invite_code by selecting the table directly, bypassing the invite-code gate
-- entirely. Only a member (or the row's own creator, needed for the insert's RETURNING —
-- see created_by above) can read a household; joining by code goes through the RPC below.

drop policy if exists "households readable by own membership or creator" on households;
create policy "households readable by own membership or creator" on households
  for select to authenticated using (
    created_by = auth.uid()
    or id in (select household_id from household_members where user_id = auth.uid())
  );

drop policy if exists "households insertable by authenticated" on households;
create policy "households insertable by authenticated" on households
  for insert to authenticated with check (created_by = auth.uid());

-- SECURITY DEFINER: runs as the function owner, bypassing the SELECT policy above, so it can
-- look up a household the caller isn't a member of yet. Only exposes id/name (not invite_code
-- itself) for an exact code match, so it can't be used to enumerate other households.
create or replace function find_household_by_invite_code(p_code text)
returns table (id uuid, name text)
language sql
security definer
set search_path = public
as $$
  select households.id, households.name
  from households
  where invite_code = upper(trim(p_code));
$$;

revoke all on function find_household_by_invite_code(text) from public;
grant execute on function find_household_by_invite_code(text) to authenticated;

-- ── RLS: household_members ───────────────────────────────────────────────────
-- household_members' own SELECT policy can't query household_members inside its using()
-- clause — Postgres error 42P17 "infinite recursion detected in policy for relation
-- household_members" (hit and fixed during local testing). Fix: look up the caller's
-- household through a SECURITY DEFINER function, which runs with elevated privileges and so
-- bypasses RLS on its own internal select instead of re-triggering the policy.

create or replace function public.current_user_household_id()
returns uuid
language sql
security definer
set search_path = public
stable
as $$
  select household_id from household_members where user_id = auth.uid()
$$;

drop policy if exists "members readable within own household" on household_members;
create policy "members readable within own household" on household_members
  for select to authenticated using (
    household_id = public.current_user_household_id()
  );

drop policy if exists "members insertable as self" on household_members;
create policy "members insertable as self" on household_members
  for insert to authenticated with check (user_id = auth.uid());

-- ── RLS: shopping_items ───────────────────────────────────────────────────────
-- Drop every existing policy on shopping_items — including whatever permissive policy
-- currently allows any authenticated user full access to the single shared list — found
-- dynamically instead of hardcoding a guessed name, then replace with household scoping.

do $$
declare
  p record;
begin
  for p in
    select policyname
    from pg_policies
    where schemaname = 'public' and tablename = 'shopping_items'
  loop
    execute format('drop policy %I on public.shopping_items', p.policyname);
    raise notice 'Dropped old shopping_items policy: %', p.policyname;
  end loop;
end $$;

create policy "items scoped to own household" on shopping_items
  for all to authenticated using (
    household_id = public.current_user_household_id()
  ) with check (
    household_id = public.current_user_household_id()
  );

-- ── Realtime ──────────────────────────────────────────────────────────────────
-- shopping_items should already be in this publication (the pre-households list was already
-- realtime), but this is idempotent in case it isn't, rather than erroring the whole script.

do $$
begin
  execute 'alter publication supabase_realtime add table public.shopping_items';
exception when duplicate_object then
  raise notice 'shopping_items already in supabase_realtime publication, skipping';
end $$;

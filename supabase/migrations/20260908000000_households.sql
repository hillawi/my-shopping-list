-- Local dev schema for MyShoppingList, reconstructed to mirror production
-- (production has no tracked migrations; SQL is run manually via the Supabase
-- SQL editor). This file exists only under supabase/migrations
-- for the local Docker stack and is NOT applied to the production project.

create type measurement_unit as enum ('PACK', 'ML', 'L', 'G', 'KG', 'PCS');

create table shopping_items (
  id bigint generated always as identity primary key,
  name text not null,
  quantity text not null default '1',
  unit measurement_unit not null,
  category text not null default 'GENERAL',
  is_purchased boolean not null default false,
  created_at timestamptz not null default now(),
  purchased_at timestamptz,
  user_email text,
  is_important boolean not null default false
);

create table households (
  id uuid primary key default gen_random_uuid(),
  name text not null default 'My Household',
  invite_code text not null unique default upper(substr(md5(random()::text), 1, 6)),
  created_at timestamptz not null default now(),
  -- Lets the creator read back their own just-inserted row (RETURNING is subject to the SELECT
  -- policy below) before the household_members row that would otherwise grant that exists.
  created_by uuid not null default auth.uid() references auth.users(id)
);

create table household_members (
  user_id uuid primary key references auth.users(id) on delete cascade,
  household_id uuid not null references households(id) on delete cascade,
  role text not null default 'member' check (role in ('owner', 'member')),
  joined_at timestamptz not null default now()
);

alter table shopping_items add column household_id uuid references households(id);
alter table shopping_items add constraint shopping_items_household_name_key unique (household_id, name);

alter table shopping_items enable row level security;
alter table households enable row level security;
alter table household_members enable row level security;

-- Deliberately NOT "using (true)": that would let any authenticated user read every
-- household's invite_code by selecting the table directly, bypassing the invite-code gate
-- entirely. Only a member (or the row's own creator, needed for the insert's RETURNING —
-- see created_by above) can read a household; joining by code goes through the RPC below.
create policy "households readable by own membership or creator" on households
  for select to authenticated using (
    created_by = auth.uid()
    or id in (select household_id from household_members where user_id = auth.uid())
  );
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

create policy "members readable within own household" on household_members
  for select to authenticated using (
    household_id in (select household_id from household_members where user_id = auth.uid())
  );
create policy "members insertable as self" on household_members
  for insert to authenticated with check (user_id = auth.uid());

create policy "items scoped to own household" on shopping_items
  for all to authenticated using (
    household_id in (select household_id from household_members where user_id = auth.uid())
  ) with check (
    household_id in (select household_id from household_members where user_id = auth.uid())
  );

-- Realtime only streams tables explicitly added to this publication. In production this is
-- normally toggled via the dashboard's Replication UI when a table is created, so it's easy to
-- miss when reconstructing schema by hand — confirmed missing here because items inserted fine
-- but never appeared in the app's selectAsFlow-backed list.
alter publication supabase_realtime add table shopping_items;

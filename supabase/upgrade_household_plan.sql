-- Admin utility: manually upgrade (or downgrade) a household's plan.
-- There's no payment integration in this app — this is how a household actually moves from
-- free to paid today, after the owner pays you out-of-band (bank transfer, etc).
--
-- ⚠️ Replace REPLACE_WITH_INVITE_CODE before running. Find it via list_users_per_household.sql,
-- or ask the household's owner (it's visible to them in the app's overflow menu).

update households
set plan = 'paid'
where invite_code = 'REPLACE_WITH_INVITE_CODE';

-- Verify:
select name, invite_code, plan from households where invite_code = 'REPLACE_WITH_INVITE_CODE';

-- Admin utility: manually mark a user's email as confirmed, bypassing the confirmation link.
-- Useful when the confirmation email's redirect is broken (see the localhost-redirect issue) or
-- when testing sign-up without access to the real inbox.
--
-- Note: auth.users.confirmed_at is a GENERATED column
-- (LEAST(email_confirmed_at, phone_confirmed_at)) — it can't be set directly, but updating
-- email_confirmed_at below makes it follow automatically.
--
-- ⚠️ Replace REPLACE_WITH_EMAIL before running.

update auth.users
set email_confirmed_at = now()
where email = 'REPLACE_WITH_EMAIL'
  and email_confirmed_at is null;

-- Verify:
select id, email, email_confirmed_at, confirmed_at
from auth.users
where email = 'REPLACE_WITH_EMAIL';

// Deletes the calling user's own account. Never trusts a client-supplied user id -- the id
// always comes from ctx.userClaims, which withSupabase derives from the verified JWT.
//
// Two explicit steps, in order:
//   1. `delete_own_household_data()` (see supabase/migrations/20260913000003_delete_own_account.sql)
//      via ctx.supabase (RLS-scoped to the caller) -- deletes the whole household (cascading to
//      household_members and shopping_items) if the caller is its owner, or just the caller's
//      own household_members row if they're a member.
//   2. ctx.supabaseAdmin.auth.admin.deleteUser() -- removes the auth.users row itself, which the
//      `authenticated` role can never do for itself; only the service-role client (supabaseAdmin)
//      can. Kept as a separate step after (1) rather than relying on auth.users' own cascade
//      timing during deleteUser.
import "@supabase/functions-js/edge-runtime.d.ts";
import { withSupabase } from "@supabase/server";

export default {
  fetch: withSupabase({ auth: "user" }, async (_req, ctx) => {
    const userId = ctx.userClaims!.id;

    const { error: cleanupError } = await ctx.supabase.rpc("delete_own_household_data");
    if (cleanupError) {
      return Response.json({ error: cleanupError.message }, { status: 500 });
    }

    const { error: deleteError } = await ctx.supabaseAdmin.auth.admin.deleteUser(userId);
    if (deleteError) {
      return Response.json({ error: deleteError.message }, { status: 500 });
    }

    return Response.json({ success: true });
  }),
};

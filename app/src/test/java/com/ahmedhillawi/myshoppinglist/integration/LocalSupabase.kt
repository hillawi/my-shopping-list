package com.ahmedhillawi.myshoppinglist.integration

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue

// These are integration tests: they run against a REAL local Supabase stack (`supabase start`,
// see README.md), not a fake -- covering the RLS/data-shape behavior a mocked unit test can't
// (the household item cap trigger and the member cap RLS policy were both real bugs this session
// found precisely because they were tested against real Postgres, not a mock). They are NOT part
// of CI, which has no local Supabase stack to talk to -- `assumeLocalSupabaseRunning()` makes
// each test SKIP (not fail) when it's unreachable, so `./gradlew test` stays green in CI and these
// only actually run for a developer who has `supabase start` up locally.
private const val LOCAL_API_URL = "http://127.0.0.1:54321"

// Supabase's fixed local-dev demo key -- identical across every `supabase start` project (see
// SupabaseClient.kt's comment on why the real anon key is expected to be public), not a secret.
private const val LOCAL_ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"

// Same story as LOCAL_ANON_KEY -- Supabase's fixed local-dev demo service-role key, not a secret.
// Used only to bypass email confirmation for test users (see signUpRandomUser below); never used
// for anything that should be RLS-scoped.
private const val LOCAL_SERVICE_ROLE_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU"

fun assumeLocalSupabaseRunning() {
    val reachable = runBlocking {
        withTimeoutOrNull(2000) {
            runCatching {
                val client = HttpClient(OkHttp)
                val response: HttpResponse = client.get("$LOCAL_API_URL/rest/v1/") {
                    header("apikey", LOCAL_ANON_KEY)
                }
                client.close()
                response.status.value in 200..499 // any HTTP response at all means the stack is up
            }.getOrDefault(false)
        } ?: false
    }
    assumeTrue("Local Supabase isn't running (`supabase start`) -- skipping integration test", reachable)
}

fun newTestClient(): SupabaseClient = createSupabaseClient(
    supabaseUrl = LOCAL_API_URL,
    supabaseKey = LOCAL_ANON_KEY
) {
    httpEngine = OkHttp.create()
    install(Postgrest)
    install(Realtime)
    install(Auth) {
        // The default session manager and code verifier cache both need persistent platform
        // storage (SharedPreferences on Android, java.util.prefs elsewhere) that isn't available
        // in a plain JVM unit test -- these clients are short-lived (one per test), so in-memory
        // is all they need anyway.
        sessionManager = MemorySessionManager()
        codeVerifierCache = MemoryCodeVerifierCache()
        // This hooks a ProcessLifecycleOwner observer to pause/resume auto-refresh on
        // foreground/background, which needs a real Android main Looper -- not available (and
        // not useful) for a short-lived JVM test client.
        enableLifecycleCallbacks = false
    }
}

// Signs up a brand-new random user against the local stack and returns that user's id, with the
// receiver client left authenticated as them. Local dev requires email confirmation (matching
// production -- see supabase/config.toml's auth.email.enable_confirmations and
// SignUpOtpIntegrationTest, which covers the real OTP round-trip), so signUpWith alone doesn't
// establish a session here -- this bypasses that confirmation step server-side via the Admin API
// (the same service-role pattern the delete-account Edge Function uses) rather than making every
// other integration test read a real email, since confirming-for-real isn't what they're testing.
suspend fun SupabaseClient.signUpRandomUser(): String =
    signUpConfirmedUser("test-${System.nanoTime()}@example.com", "password123")

// Same bypass as signUpRandomUser(), but lets the caller supply (and thus keep) the email --
// needed by tests that sign in again afterward with a different password (e.g. after a reset),
// since signUpRandomUser() only ever hands back the user id, not the email it generated.
suspend fun SupabaseClient.signUpConfirmedUser(email: String, password: String): String {
    val userInfo = auth.signUpWith(Email) {
        this.email = email
        this.password = password
    } ?: error("Sign-up did not return a user")
    confirmEmailForTest(userInfo.id)
    auth.signInWith(Email) {
        this.email = email
        this.password = password
    }
    return userInfo.id
}

private suspend fun confirmEmailForTest(userId: String) {
    val adminClient = createSupabaseClient(
        supabaseUrl = LOCAL_API_URL,
        supabaseKey = LOCAL_SERVICE_ROLE_KEY
    ) {
        httpEngine = OkHttp.create()
        install(Auth) {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
            enableLifecycleCallbacks = false
        }
    }
    adminClient.auth.admin.updateUserById(userId) { emailConfirm = true }
}

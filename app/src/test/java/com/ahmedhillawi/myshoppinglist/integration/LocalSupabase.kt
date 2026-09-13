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

// Signs up a brand-new random user against the local stack (local dev has email confirmation
// disabled by default -- see supabase/config.toml -- so this returns an already-authenticated
// client) and returns the client plus that user's id.
suspend fun SupabaseClient.signUpRandomUser(): String {
    val email = "test-${System.nanoTime()}@example.com"
    auth.signUpWith(Email) {
        this.email = email
        this.password = "password123"
    }
    return auth.currentUserOrNull()?.id ?: error("Sign-up did not establish a session -- is email confirmation enabled locally?")
}

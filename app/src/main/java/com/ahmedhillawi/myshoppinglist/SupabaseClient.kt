package com.ahmedhillawi.myshoppinglist

import androidx.compose.runtime.mutableStateOf
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp

val supabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY
) {
    httpEngine = OkHttp.create()
    install(Postgrest)
    install(Realtime)
    install(Functions)
    install(Auth) {
        // Must match the myshoppinglist://login-callback intent-filter in AndroidManifest.xml
        // and the Site URL configured in Supabase Auth — handleDeeplinks() in MainActivity
        // compares the incoming redirect's scheme/host against these and silently no-ops
        // otherwise, so a mismatch here fails invisibly (no exception, no log).
        scheme = "myshoppinglist"
        host = "login-callback"
    }
}

// Set the moment a password-reset OTP is verified (which immediately establishes a real
// session, same as any other sign-in), cleared only once the user actually finishes setting a
// new password. MainActivity checks this before its normal session-based navigation, so a
// mid-recovery session doesn't jump straight into the app before the user ever changes their
// password -- there's no way to tell a recovery session apart from a normal one after the fact,
// so this is tracked explicitly instead. See LoginScreen.kt's password-reset flow.
val isPasswordRecoveryInProgress = mutableStateOf(false)
package com.ahmedhillawi.myshoppinglist

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
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
    install(Auth) {
        // Must match the myshoppinglist://login-callback intent-filter in AndroidManifest.xml
        // and the Site URL configured in Supabase Auth — handleDeeplinks() in MainActivity
        // compares the incoming redirect's scheme/host against these and silently no-ops
        // otherwise, so a mismatch here fails invisibly (no exception, no log).
        scheme = "myshoppinglist"
        host = "login-callback"
    }
}
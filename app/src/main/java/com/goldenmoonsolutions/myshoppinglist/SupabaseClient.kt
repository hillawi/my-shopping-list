package com.goldenmoonsolutions.myshoppinglist

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp

val supabase = createSupabaseClient(
    supabaseUrl = "https://comxreruiurkxjawwkie.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNvbXhyZXJ1aXVya3hqYXd3a2llIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjgwMzAwMzksImV4cCI6MjA4MzYwNjAzOX0.Pa-viBl4bIDoPUPPgcY__t375smzjCg8FY1t2lsldRg"
) {
    httpEngine = OkHttp.create()
    install(Postgrest)
    install(Realtime)
    install(Auth)
}
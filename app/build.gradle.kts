import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization")
}

val buildTimestamp: String = OffsetDateTime.now()
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

android {
    namespace = "com.ahmedhillawi.myshoppinglist"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.ahmedhillawi.myshoppinglist"
        minSdk = 34
        targetSdk = 36
        versionCode = 6
        versionName = "1.1.0"

        buildConfigField("String", "BUILD_TIMESTAMP", "\"$buildTimestamp\"")
        buildConfigField("String", "SUPABASE_URL", "\"https://comxreruiurkxjawwkie.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNvbXhyZXJ1aXVya3hqYXd3a2llIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjgwMzAwMzksImV4cCI6MjA4MzYwNjAzOX0.Pa-viBl4bIDoPUPPgcY__t375smzjCg8FY1t2lsldRg\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Points at `supabase start` (local Docker stack) so debug builds never touch
            // production data. Override host/key per machine in local.properties (gitignored) —
            // see README.md for the local.supabase.* keys. Defaults to the emulator's
            // loopback alias; a physical phone needs the dev machine's LAN IP instead.
            val localProps = Properties().apply {
                val f = rootProject.file("local.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            val localUrl = localProps.getProperty("local.supabase.url", "http://10.0.2.2:54321")
            val localAnonKey = localProps.getProperty(
                "local.supabase.anonKey",
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"
            )
            buildConfigField("String", "SUPABASE_URL", "\"$localUrl\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"$localAnonKey\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.postgrest.kt)
    implementation(libs.realtime.kt)
    implementation(libs.ktor.client.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.appcompat)
    implementation(libs.gotrue.kt)
    implementation(libs.postgrest.kt)
    implementation(libs.androidx.core.splashscreen)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
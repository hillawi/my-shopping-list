// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    kotlin("plugin.serialization") version "2.3.0" apply false
    id("org.sonarqube") version "7.5.0.8588"
}

// Sends coverage/analysis to a local SonarQube instance: `./gradlew testDebugUnitTest jacocoTestReport sonar`.
// The auth token is NEVER set here -- it's picked up automatically from the SONAR_TOKEN
// environment variable (this repo is public; a token in a committed file would be a live,
// public credential). Export it before running, e.g. `export SONAR_TOKEN=...`.
sonar {
    properties {
        property("sonar.projectKey", "MyShoppingList")
        property("sonar.projectName", "MyShoppingList")
        property("sonar.host.url", "http://localhost:9000")
    }
}

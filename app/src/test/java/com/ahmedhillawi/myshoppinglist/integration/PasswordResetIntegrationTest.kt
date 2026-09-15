package com.ahmedhillawi.myshoppinglist.integration

import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.OtpVerifyResult
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

private const val MAILPIT_URL = "http://127.0.0.1:54324"

// Exercises the real password-reset round trip against local Postgres/GoTrue -- not just that
// the API calls don't throw, but that the new password genuinely works to sign in afterward.
// Local dev captures outgoing mail in Mailpit, same technique as SignUpOtpIntegrationTest.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PasswordResetIntegrationTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        assumeLocalSupabaseRunning()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `requesting a reset, verifying the code, and updating the password all actually take effect`() = runTest {
        val client = newTestClient()
        val email = "test-reset-${System.nanoTime()}@example.com"
        client.signUpConfirmedUser(email, "password123")

        client.auth.resetPasswordForEmail(email)
        val code = fetchOtpCodeFromMailpit(email)

        val result = client.auth.verifyEmailOtp(OtpType.Email.RECOVERY, email, code)
        assertTrue("verifying the real recovery code should authenticate the user: $result", result is OtpVerifyResult.Authenticated)

        val newPassword = "new-password-${System.nanoTime()}"
        client.auth.updateUser { password = newPassword }
        client.auth.signOut()

        val signedInWithNewPassword = runCatching {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = newPassword
            }
        }
        assertTrue("the new password should actually work: ${signedInWithNewPassword.exceptionOrNull()}", signedInWithNewPassword.isSuccess)
    }

    @Test
    fun `resending a recovery code sends a genuinely new one that also works`() = runTest {
        val client = newTestClient()
        val email = "test-reset-${System.nanoTime()}@example.com"
        client.signUpConfirmedUser(email, "password123")

        client.auth.resetPasswordForEmail(email)
        fetchOtpCodeFromMailpit(email)

        // auth.email.max_frequency (local config.toml) throttles repeat recovery emails to the
        // same address -- without this, the second resetPasswordForEmail call below hits
        // over_email_send_rate_limit since it'd otherwise fire well under a second later.
        // Thread.sleep rather than delay(): runTest's virtual clock would otherwise fast-forward
        // straight through a bare delay() with no intervening real suspension to anchor it.
        Thread.sleep(1100)

        // Recovery resend reuses resetPasswordForEmail rather than the resend endpoint (see
        // LoginScreen.kt's handleResendOtpClick) -- confirm that actually produces a usable code.
        client.auth.resetPasswordForEmail(email)
        val secondCode = fetchOtpCodeFromMailpit(email, expectedCount = 2)

        val result = client.auth.verifyEmailOtp(OtpType.Email.RECOVERY, email, secondCode)
        assertTrue("the resent code should authenticate the user: $result", result is OtpVerifyResult.Authenticated)
    }

    // Polls Mailpit's search API for the most recent password-reset email sent to `email` and
    // extracts the OTP code from its plain-text body. `expectedCount` waits until at least that
    // many matching emails have arrived, so a resend test reads the *new* one, not the first.
    private suspend fun fetchOtpCodeFromMailpit(email: String, expectedCount: Int = 1): String {
        val http = HttpClient(OkHttp)
        try {
            repeat(15) { attempt ->
                val searchBody = http.get("$MAILPIT_URL/api/v1/search") {
                    url.parameters.append("query", "to:$email")
                }.bodyAsText()
                val messageIds = Regex(""""ID":"([^"]+)"""").findAll(searchBody).map { it.groupValues[1] }.toList()
                if (messageIds.size >= expectedCount) {
                    val messageBody = http.get("$MAILPIT_URL/api/v1/message/${messageIds.first()}").bodyAsText()
                    val code = Regex("""\b(\d{8})\b""").find(messageBody)?.groupValues?.get(1)
                    if (code != null) return code
                }
                if (attempt < 14) delay(300)
            }
        } finally {
            http.close()
        }
        fail("No password-reset email with an OTP code arrived in Mailpit for $email")
        error("unreachable")
    }
}

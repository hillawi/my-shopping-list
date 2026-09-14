package com.ahmedhillawi.myshoppinglist.integration

import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.OtpVerifyResult
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

private const val MAILPIT_URL = "http://127.0.0.1:54324"

// The other integration tests bypass email confirmation entirely for speed (see
// LocalSupabase.signUpRandomUser's Admin API shortcut) -- this test is the one place that
// exercises the real thing: does the "Confirm signup" template actually render {{ .Token }}, and
// does verifyEmailOtp really accept it? Local dev captures outgoing mail in Mailpit (part of the
// same `supabase start` stack), so the OTP code is read back the same way a real user would see
// it -- via the email itself, not a database shortcut.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SignUpOtpIntegrationTest {

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
    fun `verifyEmailOtp with the real emailed code authenticates the new user`() = runTest {
        val client = newTestClient()
        val email = "test-otp-${System.nanoTime()}@example.com"
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = "password123"
        }

        val code = fetchOtpCodeFromMailpit(email)

        val result = client.auth.verifyEmailOtp(OtpType.Email.SIGNUP, email, code)

        assertTrue("verifying the real code should authenticate the user: $result", result is OtpVerifyResult.Authenticated)
    }

    @Test
    fun `verifyEmailOtp with a wrong code is rejected as OtpExpired`() = runTest {
        val client = newTestClient()
        val email = "test-otp-${System.nanoTime()}@example.com"
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = "password123"
        }
        // Make sure the real email actually arrived (and thus a signup truly happened) before
        // asserting the wrong code is rejected -- otherwise this could pass for the wrong reason
        // (e.g. a broken signup that never even reaches GoTrue's OTP check).
        fetchOtpCodeFromMailpit(email)

        val rejected = runCatching { client.auth.verifyEmailOtp(OtpType.Email.SIGNUP, email, "000000") }

        assertTrue("a wrong code must be rejected", rejected.isFailure)
        val exception = rejected.exceptionOrNull()
        assertTrue("expected an AuthRestException, got $exception", exception is AuthRestException)
        assertEquals(AuthErrorCode.OtpExpired, (exception as AuthRestException).errorCode)
    }

    // Polls Mailpit's search API for the confirmation email sent to `email` and extracts the
    // 6-digit OTP code from its plain-text body. A few retries absorb the small async delay
    // between GoTrue sending the email and Mailpit indexing it for search.
    private suspend fun fetchOtpCodeFromMailpit(email: String): String {
        val http = HttpClient(OkHttp)
        try {
            repeat(10) { attempt ->
                val searchBody = http.get("$MAILPIT_URL/api/v1/search") {
                    url.parameters.append("query", "to:$email")
                }.bodyAsText()
                val messageId = Regex(""""ID":"([^"]+)"""").find(searchBody)?.groupValues?.get(1)
                if (messageId != null) {
                    val messageBody = http.get("$MAILPIT_URL/api/v1/message/$messageId").bodyAsText()
                    val code = Regex("""\b(\d{6})\b""").find(messageBody)?.groupValues?.get(1)
                    if (code != null) return code
                }
                if (attempt < 9) delay(300)
            }
        } finally {
            http.close()
        }
        fail("No confirmation email with an OTP code arrived in Mailpit for $email")
        error("unreachable")
    }
}

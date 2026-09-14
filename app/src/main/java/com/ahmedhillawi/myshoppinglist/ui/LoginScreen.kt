package com.ahmedhillawi.myshoppinglist.ui

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ahmedhillawi.myshoppinglist.R
import com.ahmedhillawi.myshoppinglist.supabase
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.OtpVerifyResult
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Matches production's real auth.email.otp_length (confirmed via `supabase config diff`; local
// config.toml is kept aligned to it -- see its comment there) -- the number of boxes the OTP
// entry UI renders.
private const val OTP_LENGTH = 8

// Maps a caught auth exception to a user-facing message, so a raw technical error (a Postgrest/
// GoTrue error code or a network exception's message) never reaches the screen. The original
// exception is still logged for debugging. New known cases can be added to the `when` below;
// anything else — including non-AuthRestException failures like a network error — falls back to
// a single generic message.
private fun resolveAuthErrorMessage(context: Context, e: Exception): String {
    Log.w("LoginScreen", "Auth request failed", e)
    return when ((e as? AuthRestException)?.errorCode) {
        AuthErrorCode.UserAlreadyExists -> context.getString(R.string.auth_user_already_exists_error)
        AuthErrorCode.InvalidCredentials -> context.getString(R.string.auth_invalid_credentials_error)
        AuthErrorCode.WeakPassword -> context.getString(R.string.auth_weak_password_error)
        // Also returned by production for a pre-existing but never-confirmed account, not just
        // placeholder domains like @example.com — worded to cover both without claiming either.
        AuthErrorCode.EmailAddressInvalid -> context.getString(R.string.auth_email_invalid_error)
        // GoTrue returns this single code for both an expired AND a wrong OTP -- there's no
        // separate "invalid" code, so the message covers both without claiming either.
        AuthErrorCode.OtpExpired -> context.getString(R.string.auth_otp_invalid_error)
        AuthErrorCode.OverEmailSendRateLimit -> context.getString(R.string.auth_otp_rate_limit_error)
        else -> context.getString(R.string.auth_generic_error)
    }
}

// Extracted out of LoginScreen's composable body (rather than inlined in the button onClick
// lambdas) to keep the composable's own cognitive complexity down -- Compose lambdas nested
// inside a composable count against that same function. Takes the raw MutableState so it can
// write isLoading/message/showValidationError without needing a ViewModel (LoginScreen
// deliberately doesn't have one -- see CLAUDE.md's coroutine-scope-split note).
private fun handleSignInClick(
    email: String,
    password: String,
    context: Context,
    scope: CoroutineScope,
    isLoading: MutableState<Boolean>,
    message: MutableState<String>,
    showValidationError: MutableState<Boolean>
) {
    // Supabase's own validation only kicks in once a request is sent -- with both fields blank
    // it reads as an anonymous sign-in attempt and comes back as an opaque
    // "anonymous_provider_disabled" error, so this is checked upfront instead of surfacing that
    // raw message to the user.
    if (email.isBlank() || password.isBlank()) {
        showValidationError.value = true
        message.value = context.getString(R.string.auth_missing_fields_error)
        return
    }
    showValidationError.value = false
    scope.launch {
        isLoading.value = true
        try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            // No need to navigate manually; MainActivity observes the session change
        } catch (e: Exception) {
            message.value = resolveAuthErrorMessage(context, e)
        } finally {
            isLoading.value = false
        }
    }
}

private fun handleSignUpClick(
    email: String,
    password: String,
    context: Context,
    scope: CoroutineScope,
    message: MutableState<String>,
    showValidationError: MutableState<Boolean>,
    pendingOtpEmail: MutableState<String?>
) {
    if (email.isBlank() || password.isBlank()) {
        showValidationError.value = true
        message.value = context.getString(R.string.auth_missing_fields_error)
        return
    }
    showValidationError.value = false
    scope.launch {
        try {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            // signUpWith never throws and never establishes a session when email confirmation is
            // required (confirmed against the SDK's behavior) -- it just returns, so the OTP step
            // always follows a successful call here rather than branching on session state.
            message.value = ""
            pendingOtpEmail.value = email
        } catch (e: Exception) {
            message.value = resolveAuthErrorMessage(context, e)
        }
    }
}

private fun handleVerifyOtpClick(
    email: String,
    code: String,
    context: Context,
    scope: CoroutineScope,
    isLoading: MutableState<Boolean>,
    message: MutableState<String>,
    pendingOtpEmail: MutableState<String?>,
    onInvalidCode: () -> Unit
) {
    if (code.isBlank()) {
        message.value = context.getString(R.string.auth_otp_missing_code_error)
        return
    }
    scope.launch {
        isLoading.value = true
        try {
            when (supabase.auth.verifyEmailOtp(OtpType.Email.SIGNUP, email, code)) {
                is OtpVerifyResult.Authenticated -> {
                    // No manual navigation needed -- MainActivity observes sessionStatus and
                    // switches off LoginScreen once it flips to Authenticated.
                }
                OtpVerifyResult.VerifiedNoSession -> {
                    // Not expected for the SIGNUP type (confirming a new account should always
                    // hand back a session), but handled defensively rather than assumed away.
                    pendingOtpEmail.value = null
                    message.value = context.getString(R.string.auth_invalid_credentials_error)
                }
            }
        } catch (e: Exception) {
            message.value = resolveAuthErrorMessage(context, e)
            onInvalidCode()
        } finally {
            isLoading.value = false
        }
    }
}

private fun handleResendOtpClick(
    email: String,
    context: Context,
    scope: CoroutineScope,
    message: MutableState<String>
) {
    scope.launch {
        try {
            supabase.auth.resendEmail(OtpType.Email.SIGNUP, email)
            message.value = context.getString(R.string.resend_code_sent)
        } catch (e: Exception) {
            message.value = resolveAuthErrorMessage(context, e)
        }
    }
}

// Mirrors the language toggle in ShoppingListScreen's overflow menu — same LocaleManager
// mechanism, surfaced directly here since this screen has no menu of its own and is the first
// thing a user sees, before any language has been chosen. Extracted out of LoginScreen to keep
// that composable's own cognitive complexity down.
@Composable
private fun LanguageToggleButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val localeManager = context.getSystemService(LocaleManager::class.java)
    val currentTag = if (!localeManager.applicationLocales.isEmpty) {
        localeManager.applicationLocales[0].toLanguageTag()
    } else "en"
    val targetLanguageLabel = if (currentTag.contains("ar")) "English" else "العربية"

    TextButton(
        onClick = {
            val newTag = if (currentTag.contains("ar")) "en" else "ar"
            localeManager.applicationLocales = LocaleList.forLanguageTags(newTag)
        },
        modifier = modifier
    ) {
        Icon(Icons.Default.Language, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text(targetLanguageLabel)
    }
}

// One digit of the OTP code. A box moves focus to the next box as soon as a digit is typed, and
// back to the previous box on backspace against an already-empty box (plain onValueChange never
// fires for that -- deleting nothing produces the same empty string -- so it's caught via a raw
// key event instead). Pasting/autofilling the whole code into one box is also handled: any extra
// characters beyond the first spill forward into the following boxes.
@Composable
private fun OtpCodeBoxes(
    digits: List<String>,
    onDigitsChange: (index: Int, value: String) -> Unit,
    focusRequesters: List<FocusRequester>,
    enabled: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        digits.forEachIndexed { index, digit ->
            OutlinedTextField(
                value = digit,
                onValueChange = { newValue ->
                    val typed = newValue.filter { it.isDigit() }
                    if (typed.length <= 1) {
                        onDigitsChange(index, typed)
                        if (typed.isNotEmpty() && index < digits.lastIndex) {
                            focusRequesters[index + 1].requestFocus()
                        }
                    } else {
                        // Pasted/autofilled multiple digits starting at this box.
                        typed.forEachIndexed { offset, c ->
                            val target = index + offset
                            if (target <= digits.lastIndex) onDigitsChange(target, c.toString())
                        }
                        focusRequesters[minOf(index + typed.length, digits.lastIndex)].requestFocus()
                    }
                },
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequesters[index])
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Backspace && digit.isEmpty() && index > 0) {
                            onDigitsChange(index - 1, "")
                            focusRequesters[index - 1].requestFocus()
                            true
                        } else {
                            false
                        }
                    }
            )
        }
    }
}

// The OTP code-entry step shown right after sign-up, in place of the email/password fields.
// Extracted out of LoginScreen to keep that composable's own cognitive complexity down.
@Composable
private fun OtpEntryForm(
    email: String,
    context: Context,
    scope: CoroutineScope,
    isLoadingState: MutableState<Boolean>,
    isLoading: Boolean,
    messageState: MutableState<String>,
    message: String,
    pendingOtpEmailState: MutableState<String?>
) {
    val digits = remember { mutableStateListOf(*Array(OTP_LENGTH) { "" }) }
    val focusRequesters = remember { List(OTP_LENGTH) { FocusRequester() } }
    val code = digits.joinToString("")
    var refocusFirstBox by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusRequesters[0].requestFocus() }

    // Auto-verify the moment every box is filled -- keyed on `code` so it only re-fires once the
    // user actually changes a digit, not on every unrelated recomposition (e.g. isLoading
    // flipping back to false after a failed attempt).
    LaunchedEffect(code) {
        if (code.length == OTP_LENGTH) {
            handleVerifyOtpClick(email, code, context, scope, isLoadingState, messageState, pendingOtpEmailState) {
                digits.indices.forEach { digits[it] = "" }
                refocusFirstBox = true
            }
        }
    }

    // Boxes are disabled (enabled = !isLoading) while a verify request is in flight, so
    // requesting focus right when the code fails would silently do nothing -- a disabled
    // composable can't take focus. Deferred until isLoading actually flips back to false, which
    // is when the boxes become focusable again.
    LaunchedEffect(isLoading) {
        if (!isLoading && refocusFirstBox) {
            focusRequesters[0].requestFocus()
            refocusFirstBox = false
        }
    }

    Text(stringResource(R.string.otp_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        stringResource(R.string.otp_description, email),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(24.dp))

    Text(stringResource(R.string.otp_code_label), style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(8.dp))
    OtpCodeBoxes(
        digits = digits,
        onDigitsChange = { index, value -> digits[index] = value },
        focusRequesters = focusRequesters,
        enabled = !isLoading
    )

    Spacer(modifier = Modifier.height(24.dp))

    if (message.isNotEmpty()) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
    }

    TextButton(onClick = { handleResendOtpClick(email, context, scope, messageState) }) {
        Text(stringResource(R.string.resend_code))
    }

    TextButton(onClick = {
        pendingOtpEmailState.value = null
        messageState.value = ""
    }) {
        Text(stringResource(R.string.use_different_email))
    }
}

@Composable
fun LoginScreen() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isLoadingState = remember { mutableStateOf(false) }
    val isLoading by isLoadingState
    val messageState = remember { mutableStateOf("") }
    val message by messageState
    val showValidationErrorState = remember { mutableStateOf(false) }
    val showValidationError by showValidationErrorState
    val pendingOtpEmailState = remember { mutableStateOf<String?>(null) }
    val pendingOtpEmail by pendingOtpEmailState
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        LanguageToggleButton(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val otpEmail = pendingOtpEmail
            if (otpEmail != null) {
                OtpEntryForm(otpEmail, context, scope, isLoadingState, isLoading, messageState, message, pendingOtpEmailState)
                return@Column
            }

            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.email_label)) },
                isError = showValidationError && email.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password_label)) },
                isError = showValidationError && password.isBlank(),
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                // KeyboardType.Password tells the IME not to cache this input for
                // suggestions/autofill -- a generic text keyboard would otherwise be free to
                // remember characters typed into this field.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (message.isNotEmpty()) {
                Text(message, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    handleSignInClick(email, password, context, scope, isLoadingState, messageState, showValidationErrorState)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(if (isLoading) "Loading..." else stringResource(R.string.sign_in))
            }

            // Sign Up Button
            TextButton(onClick = {
                handleSignUpClick(email, password, context, scope, messageState, showValidationErrorState, pendingOtpEmailState)
            }) {
                Text(stringResource(R.string.create_account))
            }
        }
    }
}
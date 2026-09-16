package com.ahmedhillawi.myshoppinglist.ui

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmedhillawi.myshoppinglist.BuildConfig
import com.ahmedhillawi.myshoppinglist.R
import com.ahmedhillawi.myshoppinglist.isPasswordRecoveryInProgress
import com.ahmedhillawi.myshoppinglist.supabase
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.OtpVerifyResult
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

// Matches production's real auth.email.otp_length (confirmed via `supabase config diff`; local
// config.toml is kept aligned to it -- see its comment there) -- the number of boxes the OTP
// entry UI renders.
private const val OTP_LENGTH = 8

// Bundles the status text shown below the form with whether it's actually an error -- most of
// what this screen reports is (a failed sign-in, a missing field), but "a new code was sent" is
// a confirmation, not a failure, and rendering it in the same error color was misleading.
private data class AuthMessage(val text: String, val isError: Boolean = true) {
    companion object {
        val Empty = AuthMessage("")
    }
}

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
        AuthErrorCode.SamePassword -> context.getString(R.string.auth_same_password_error)
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
    message: MutableState<AuthMessage>,
    showValidationError: MutableState<Boolean>
) {
    // Stray leading/trailing whitespace (e.g. pasted from another app) makes GoTrue reject an
    // otherwise-valid address outright rather than just ignoring it -- trim once here so every
    // downstream use (validation, the auth call itself, pendingOtpEmail) sees the same clean
    // value instead of scattering .trim() calls across each call site.
    @Suppress("NAME_SHADOWING") val email = email.trim()
    // Supabase's own validation only kicks in once a request is sent -- with both fields blank
    // it reads as an anonymous sign-in attempt and comes back as an opaque
    // "anonymous_provider_disabled" error, so this is checked upfront instead of surfacing that
    // raw message to the user.
    if (email.isBlank() || password.isBlank()) {
        showValidationError.value = true
        message.value = AuthMessage(context.getString(R.string.auth_missing_fields_error))
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
            message.value = AuthMessage(resolveAuthErrorMessage(context, e))
        } finally {
            isLoading.value = false
        }
    }
}

// SHA-256 of the raw nonce, hex-encoded -- Google's ID token embeds only the hashed form, but
// GoTrue's IDToken.Config expects the original raw value to hash and compare itself. Sending the
// same value to both sides would make GoTrue's check compare a hash against a raw string and
// always fail.
private fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }

// Native "Sign in with Google" via Credential Manager -- shows the system account picker
// in-process (no browser, no deep link) and hands the resulting Google ID token straight to
// Supabase's IDToken provider. GoTrue creates the user automatically on a first-time Google
// sign-in, same as it already does for a first-time email sign-up.
private fun handleGoogleSignInClick(
    context: Context,
    scope: CoroutineScope,
    isLoading: MutableState<Boolean>,
    message: MutableState<AuthMessage>
) {
    // GoTrue's id_token grant checks the ID token's nonce claim against what the client reports,
    // to stop a captured token from being replayed into a different sign-in attempt. Google's
    // API takes the hashed nonce (it goes into the token as-is); Supabase's config takes the raw
    // one and hashes it itself to compare -- see the Supabase docs on signInWithIdToken's nonce
    // handling for exactly this asymmetry.
    val rawNonce = UUID.randomUUID().toString()
    val hashedNonce = sha256Hex(rawNonce)

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .setNonce(hashedNonce)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        isLoading.value = true
        try {
            val result = CredentialManager.create(context).getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                supabase.auth.signInWith(IDToken) {
                    idToken = googleIdTokenCredential.idToken
                    provider = Google
                    nonce = rawNonce
                }
                // No need to navigate manually; MainActivity observes the session change
            } else {
                message.value = AuthMessage(context.getString(R.string.auth_google_sign_in_error))
            }
        } catch (e: GetCredentialException) {
            // By far the most common case here is the user backing out of the account picker --
            // also covers no Google account on the device / Play Services unavailable, none of
            // which are AuthRestExceptions resolveAuthErrorMessage knows how to classify.
            message.value = AuthMessage(context.getString(R.string.auth_google_sign_in_cancelled_error))
        } catch (e: Exception) {
            message.value = AuthMessage(resolveAuthErrorMessage(context, e))
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
    message: MutableState<AuthMessage>,
    showValidationError: MutableState<Boolean>,
    pendingOtpEmail: MutableState<String?>
) {
    @Suppress("NAME_SHADOWING") val email = email.trim()
    if (email.isBlank() || password.isBlank()) {
        showValidationError.value = true
        message.value = AuthMessage(context.getString(R.string.auth_missing_fields_error))
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
            message.value = AuthMessage.Empty
            pendingOtpEmail.value = email
        } catch (e: Exception) {
            message.value = AuthMessage(resolveAuthErrorMessage(context, e))
        }
    }
}

private fun handleVerifyOtpClick(
    email: String,
    code: String,
    purpose: OtpType.Email,
    context: Context,
    scope: CoroutineScope,
    isLoading: MutableState<Boolean>,
    message: MutableState<AuthMessage>,
    pendingOtpEmail: MutableState<String?>,
    onAuthenticated: () -> Unit,
    onInvalidCode: () -> Unit
) {
    if (code.isBlank()) {
        message.value = AuthMessage(context.getString(R.string.auth_otp_missing_code_error))
        return
    }
    scope.launch {
        isLoading.value = true
        try {
            when (supabase.auth.verifyEmailOtp(purpose, email, code)) {
                is OtpVerifyResult.Authenticated -> onAuthenticated()
                OtpVerifyResult.VerifiedNoSession -> {
                    // Not expected for either SIGNUP or RECOVERY (both should always hand back a
                    // session), but handled defensively rather than assumed away.
                    pendingOtpEmail.value = null
                    message.value = AuthMessage(context.getString(R.string.auth_invalid_credentials_error))
                }
            }
        } catch (e: Exception) {
            message.value = AuthMessage(resolveAuthErrorMessage(context, e))
            onInvalidCode()
        } finally {
            isLoading.value = false
        }
    }
}

private fun handleResendOtpClick(
    email: String,
    purpose: OtpType.Email,
    context: Context,
    scope: CoroutineScope,
    message: MutableState<AuthMessage>
) {
    scope.launch {
        try {
            // Recovery codes aren't a "pending signup" style state GoTrue's resend endpoint is
            // documented for -- resetPasswordForEmail is the certain way to get a fresh one,
            // and it's exactly the same call the initial "forgot password" tap already makes.
            if (purpose == OtpType.Email.RECOVERY) {
                supabase.auth.resetPasswordForEmail(email)
            } else {
                supabase.auth.resendEmail(purpose, email)
            }
            message.value = AuthMessage(context.getString(R.string.resend_code_sent), isError = false)
        } catch (e: Exception) {
            message.value = AuthMessage(resolveAuthErrorMessage(context, e))
        }
    }
}

private fun handleForgotPasswordClick(
    email: String,
    context: Context,
    scope: CoroutineScope,
    message: MutableState<AuthMessage>,
    pendingOtpEmail: MutableState<String?>,
    otpPurpose: MutableState<OtpType.Email>
) {
    @Suppress("NAME_SHADOWING") val email = email.trim()
    if (email.isBlank()) {
        message.value = AuthMessage(context.getString(R.string.auth_forgot_password_missing_email_error))
        return
    }
    scope.launch {
        try {
            supabase.auth.resetPasswordForEmail(email)
            message.value = AuthMessage.Empty
            otpPurpose.value = OtpType.Email.RECOVERY
            // Set now, not just after the code is verified -- belt-and-suspenders against
            // MainActivity ever seeing a session appear mid-flow before the guard further down
            // has a chance to run. See SupabaseClient.kt's doc comment on this flag.
            isPasswordRecoveryInProgress.value = true
            pendingOtpEmail.value = email
        } catch (e: Exception) {
            message.value = AuthMessage(resolveAuthErrorMessage(context, e))
        }
    }
}

private fun handleSetNewPasswordClick(
    newPassword: String,
    confirmPassword: String,
    context: Context,
    scope: CoroutineScope,
    isLoading: MutableState<Boolean>,
    message: MutableState<AuthMessage>,
    onSuccess: () -> Unit
) {
    if (newPassword.isBlank() || confirmPassword.isBlank()) {
        message.value = AuthMessage(context.getString(R.string.auth_missing_fields_error))
        return
    }
    if (newPassword != confirmPassword) {
        message.value = AuthMessage(context.getString(R.string.auth_password_mismatch_error))
        return
    }
    scope.launch {
        isLoading.value = true
        try {
            supabase.auth.updateUser { password = newPassword }
            onSuccess()
        } catch (e: Exception) {
            message.value = AuthMessage(resolveAuthErrorMessage(context, e))
        } finally {
            isLoading.value = false
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
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val clipboardScope = rememberCoroutineScope()

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        digits.forEachIndexed { index, digit ->
            // Material3's OutlinedTextField bakes in ~16dp of horizontal padding on each side
            // that the convenience overload doesn't expose -- at roughly 44dp per box (8 boxes
            // on a phone-width screen) that leaves almost no room for the glyph itself, clipping
            // it. BasicTextField with a manually drawn border sidesteps that entirely.
            val borderColor = when {
                !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                digit.isNotEmpty() -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
            BasicTextField(
                value = digit,
                onValueChange = { newValue ->
                    val typed = newValue.filter { it.isDigit() }
                    if (typed.length <= 1) {
                        onDigitsChange(index, typed)
                        if (typed.isNotEmpty() && index < digits.lastIndex) {
                            focusRequesters[index + 1].requestFocus()
                        }
                    } else {
                        // Pasted/autofilled multiple digits starting at this box. Some keyboards'
                        // paste implementations report this field's post-paste value to Compose
                        // with its first character silently missing (a platform IME quirk, not
                        // specific to this field) -- reading the clipboard directly sidesteps
                        // that instead of trusting `newValue`, which is only used as a fallback
                        // if the clipboard is empty or shorter than what was already reported.
                        clipboardScope.launch {
                            val clipboardDigits = clipboard.getClipEntry()
                                ?.clipData?.getItemAt(0)?.coerceToText(context)?.toString()
                                ?.filter { it.isDigit() }
                            val fullCode = if (!clipboardDigits.isNullOrEmpty() && clipboardDigits.length >= typed.length) {
                                clipboardDigits
                            } else {
                                typed
                            }
                            fullCode.forEachIndexed { offset, c ->
                                val target = index + offset
                                if (target <= digits.lastIndex) onDigitsChange(target, c.toString())
                            }
                            focusRequesters[minOf(index + fullCode.length, digits.lastIndex)].requestFocus()
                        }
                    }
                },
                enabled = enabled,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
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
    purpose: OtpType.Email,
    context: Context,
    scope: CoroutineScope,
    isLoadingState: MutableState<Boolean>,
    isLoading: Boolean,
    messageState: MutableState<AuthMessage>,
    message: AuthMessage,
    pendingOtpEmailState: MutableState<String?>,
    onAuthenticated: () -> Unit
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
            handleVerifyOtpClick(email, code, purpose, context, scope, isLoadingState, messageState, pendingOtpEmailState, onAuthenticated) {
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

    val titleRes = if (purpose == OtpType.Email.RECOVERY) R.string.reset_password_otp_title else R.string.otp_title
    val descriptionRes = if (purpose == OtpType.Email.RECOVERY) R.string.reset_password_otp_description else R.string.otp_description

    Text(stringResource(titleRes), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        stringResource(descriptionRes, email),
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

    // The boxes fill and disable themselves the instant the code is complete, with no button
    // press to visually anchor "something is happening" -- without this, a verify request in
    // flight (or a slow network) reads as the screen having frozen instead.
    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (message.text.isNotEmpty()) {
        Text(message.text, color = if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
    }

    TextButton(
        onClick = { handleResendOtpClick(email, purpose, context, scope, messageState) },
        enabled = !isLoading
    ) {
        Text(stringResource(R.string.resend_code))
    }

    TextButton(
        onClick = {
            pendingOtpEmailState.value = null
            messageState.value = AuthMessage.Empty
            // Abandoning a recovery flow mid-code -- release the guard so a normal sign-in works
            // immediately afterward instead of getting stuck showing LoginScreen forever.
            if (purpose == OtpType.Email.RECOVERY) isPasswordRecoveryInProgress.value = false
        },
        enabled = !isLoading
    ) {
        Text(stringResource(R.string.use_different_email))
    }
}

// The final step of the password-reset flow, shown after the recovery code is verified.
// Extracted out of LoginScreen to keep that composable's own cognitive complexity down.
@Composable
private fun SetNewPasswordForm(
    context: Context,
    scope: CoroutineScope,
    isLoadingState: MutableState<Boolean>,
    isLoading: Boolean,
    messageState: MutableState<AuthMessage>,
    message: AuthMessage,
    onSuccess: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Text(stringResource(R.string.new_password_label), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(24.dp))

    OutlinedTextField(
        value = newPassword,
        onValueChange = { newPassword = it },
        label = { Text(stringResource(R.string.new_password_label)) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = confirmPassword,
        onValueChange = { confirmPassword = it },
        label = { Text(stringResource(R.string.confirm_new_password_label)) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )

    Spacer(modifier = Modifier.height(24.dp))

    if (message.text.isNotEmpty()) {
        Text(message.text, color = if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
    }

    Button(
        onClick = {
            handleSetNewPasswordClick(newPassword, confirmPassword, context, scope, isLoadingState, messageState, onSuccess)
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(stringResource(R.string.update_password_button))
        }
    }
}

@Composable
fun LoginScreen() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isLoadingState = remember { mutableStateOf(false) }
    val isLoading by isLoadingState
    val messageState = remember { mutableStateOf(AuthMessage.Empty) }
    val message by messageState
    val showValidationErrorState = remember { mutableStateOf(false) }
    val showValidationError by showValidationErrorState
    val pendingOtpEmailState = remember { mutableStateOf<String?>(null) }
    val pendingOtpEmail by pendingOtpEmailState
    val otpPurposeState = remember { mutableStateOf(OtpType.Email.SIGNUP) }
    val otpPurpose by otpPurposeState
    var showSetNewPassword by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        LanguageToggleButton(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showSetNewPassword) {
                SetNewPasswordForm(context, scope, isLoadingState, isLoading, messageState, message) {
                    showSetNewPassword = false
                    messageState.value = AuthMessage.Empty
                    // Only now is it safe for MainActivity to act on the session verifyEmailOtp
                    // already established -- see SupabaseClient.kt's doc comment on this flag.
                    isPasswordRecoveryInProgress.value = false
                }
                return@Column
            }

            val otpEmail = pendingOtpEmail
            if (otpEmail != null) {
                OtpEntryForm(otpEmail, otpPurpose, context, scope, isLoadingState, isLoading, messageState, message, pendingOtpEmailState) {
                    if (otpPurpose == OtpType.Email.RECOVERY) {
                        pendingOtpEmailState.value = null
                        showSetNewPassword = true
                    }
                    // Nothing to do for SIGNUP -- MainActivity observes sessionStatus and
                    // switches off LoginScreen once it flips to Authenticated.
                }
                return@Column
            }

            // painterResource only supports plain VectorDrawables/rasters, not the <adaptive-icon>
            // XML R.mipmap.ic_launcher resolves to on API 26+ -- so this recreates the adaptive
            // icon's look (background color + foreground vector) by hand instead of loading it.
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorResource(R.color.ic_launcher_background)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
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

            if (message.text.isNotEmpty()) {
                Text(message.text, color = if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    handleSignInClick(email, password, context, scope, isLoadingState, messageState, showValidationErrorState)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isLoading
            ) {
                Text(if (isLoading) stringResource(R.string.loading_label) else stringResource(R.string.sign_in))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { handleGoogleSignInClick(context, scope, isLoadingState, messageState) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isLoading
            ) {
                // Google's brand guidelines call for their actual multi-color "G" mark on a sign-in
                // button, not a generic/tinted icon -- Material Icons Extended (already a dependency
                // here) has no brand logos, hence the dedicated drawable instead.
                Icon(
                    painter = painterResource(R.drawable.ic_google_logo),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.continue_with_google))
            }

            // Sign Up Button
            TextButton(onClick = {
                handleSignUpClick(email, password, context, scope, messageState, showValidationErrorState, pendingOtpEmailState)
            }) {
                Text(stringResource(R.string.create_account))
            }

            TextButton(onClick = {
                handleForgotPasswordClick(email, context, scope, messageState, pendingOtpEmailState, otpPurposeState)
            }) {
                Text(stringResource(R.string.forgot_password))
            }
        }
    }
}
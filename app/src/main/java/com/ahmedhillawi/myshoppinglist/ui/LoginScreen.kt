package com.ahmedhillawi.myshoppinglist.ui

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ahmedhillawi.myshoppinglist.R
import com.ahmedhillawi.myshoppinglist.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    showValidationError: MutableState<Boolean>
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
            message.value = context.getString(R.string.account_created_check_email)
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        LanguageToggleButton(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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
                handleSignUpClick(email, password, context, scope, messageState, showValidationErrorState)
            }) {
                Text(stringResource(R.string.create_account))
            }
        }
    }
}
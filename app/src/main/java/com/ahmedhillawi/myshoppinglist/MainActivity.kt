package com.ahmedhillawi.myshoppinglist

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ahmedhillawi.myshoppinglist.ui.HouseholdOnboardingScreen
import com.ahmedhillawi.myshoppinglist.ui.LoginScreen
import com.ahmedhillawi.myshoppinglist.ui.theme.MyShoppingListTheme
import com.ahmedhillawi.myshoppinglist.viewmodel.HouseholdViewModel
import com.ahmedhillawi.myshoppinglist.viewmodel.ShoppingListViewModel
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthDeeplink(intent)

        splashScreen.setKeepOnScreenCondition {
            val currentStatus = supabase.auth.sessionStatus.value
            currentStatus is SessionStatus.Initializing
        }

        setContent {
            val sessionStatus by supabase.auth.sessionStatus.collectAsState()

            MyShoppingListTheme() {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when(sessionStatus) {
                        is SessionStatus.Authenticated -> {
                            val householdViewModel: HouseholdViewModel by viewModels()
                            val household by householdViewModel.household.collectAsState()
                            val isLoadingHousehold by householdViewModel.isLoading.collectAsState()
                            val viewModel: ShoppingListViewModel by viewModels()

                            // Both ViewModels are retrieved via `by viewModels()` and survive a
                            // sign-out/sign-in within the same Activity, so re-resolve/reset their
                            // state whenever the authenticated user changes — otherwise a second
                            // account signing in on the same device would see the previous
                            // account's household and items.
                            val userId = (sessionStatus as SessionStatus.Authenticated).session.user?.id
                            LaunchedEffect(userId) {
                                viewModel.reset()
                                householdViewModel.resolveHousehold()
                            }

                            when {
                                isLoadingHousehold -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                                household == null -> {
                                    HouseholdOnboardingScreen(householdViewModel)
                                }
                                else -> {
                                    val currentHousehold = household!!
                                    LaunchedEffect(currentHousehold.id) {
                                        viewModel.start(currentHousehold.id!!)
                                    }
                                    ShoppingListScreen(viewModel, currentHousehold)
                                }
                            }
                        }
                        else -> {
                            LoginScreen()
                        }
                    }
                }
            }
        }
    }

    // singleTask (see AndroidManifest.xml) routes a deep link to the already-running instance
    // here instead of creating a new one.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeeplink(intent)
    }

    // Supabase's email confirmation link redirects to myshoppinglist://login-callback with the
    // session tokens attached — the confirmation itself already happened server-side by this
    // point, this just picks up the resulting session so the user lands signed in instead of
    // having to sign in manually. No-ops for any intent that isn't this redirect (e.g. the normal
    // launcher intent), so it's safe to call unconditionally.
    private fun handleAuthDeeplink(intent: Intent) {
        supabase.handleDeeplinks(
            intent,
            onSessionSuccess = { Log.i("MainActivity", "Session established from auth deeplink") },
            onError = { e -> Log.w("MainActivity", "Failed to handle auth deeplink", e) }
        )
    }
}
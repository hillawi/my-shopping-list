package com.ahmedhillawi.myshoppinglist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ahmedhillawi.myshoppinglist.ui.LoginScreen
import com.ahmedhillawi.myshoppinglist.ui.theme.MyShoppingListTheme
import com.ahmedhillawi.myshoppinglist.viewmodel.ShoppingListViewModel
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        splashScreen.setKeepOnScreenCondition {
            val currentStatus = supabase.auth.sessionStatus.value
            currentStatus is SessionStatus.Initializing
        }

        setContent {
            val sessionStatus by supabase.auth.sessionStatus.collectAsState()

            MyShoppingListTheme() {
                val viewModel: ShoppingListViewModel by viewModels()
                Surface(color = MaterialTheme.colorScheme.background) {
                    when(sessionStatus) {
                        is SessionStatus.Authenticated -> {
                            ShoppingListScreen(viewModel)
                        }
                        else -> {
                            LoginScreen()
                        }
                    }
                }
            }
        }
    }
}
package com.goldenmoonsolutions.myshoppinglist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.goldenmoonsolutions.myshoppinglist.ui.theme.MyShoppingListTheme
import com.goldenmoonsolutions.myshoppinglist.viewmodel.ShoppingListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ShoppingListViewModel()
        enableEdgeToEdge()
        setContent {
            MyShoppingListTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ShoppingListScreen(viewModel)
                }
            }
        }
    }
}
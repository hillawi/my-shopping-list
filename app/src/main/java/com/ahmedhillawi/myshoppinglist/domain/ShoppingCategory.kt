package com.ahmedhillawi.myshoppinglist.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KebabDining
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.ahmedhillawi.myshoppinglist.R

enum class ShoppingCategory(val resId: Int, val order: Int, val icon: ImageVector) {
    PRODUCE(R.string.cat_produce, 1, Icons.Default.EnergySavingsLeaf),
    DAIRY(R.string.cat_dairy, 2, Icons.Default.WaterDrop),
    MEAT(R.string.cat_meat, 3, Icons.Default.KebabDining),
    FROZEN(R.string.cat_frozen, 4, Icons.Default.AcUnit),
    PANTRY(R.string.cat_pantry, 5, Icons.Default.Kitchen),
    HOUSEHOLD(R.string.cat_household, 6, Icons.Default.Home),
    BAKERY(R.string.cat_bakery, 7, Icons.Default.BakeryDining),
    BEVERAGES(R.string.cat_beverages, 8, Icons.Default.LocalDrink),
    GENERAL(R.string.cat_general, 99, Icons.Default.ShoppingBasket);

    companion object {
        fun fromString(value: String?) = ShoppingCategory.entries.find { it.name == value } ?: GENERAL
    }
}
package com.goldenmoonsolutions.myshoppinglist.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KebabDining
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

enum class ShoppingCategory(val label: String, val order: Int, val icon: ImageVector) {
    PRODUCE("Produce", 1, Icons.Default.EnergySavingsLeaf),
    DAIRY("Dairy", 2, Icons.Default.WaterDrop),
    MEAT("Meat", 3, Icons.Default.KebabDining),
    FROZEN("Frozen", 4, Icons.Default.AcUnit),
    PANTRY("Pantry", 5, Icons.Default.Kitchen),
    HOUSEHOLD("Household", 6, Icons.Default.Home),
    GENERAL("General", 99, Icons.Default.ShoppingBasket);

    companion object {
        fun fromString(value: String?) = ShoppingCategory.entries.find { it.name == value } ?: GENERAL
    }
}
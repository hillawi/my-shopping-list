package com.ahmedhillawi.myshoppinglist.ui

import com.ahmedhillawi.myshoppinglist.domain.MeasurementUnit
import com.ahmedhillawi.myshoppinglist.domain.ShoppingCategory

// Shared by AddItemForm and EditItemDialog -- both hoist the same four fields (name, quantity,
// unit, category) from their caller, and bundling them keeps those composables' own parameter
// counts under SonarQube's threshold (7) instead of exposing each field/onChange pair separately.
data class ItemDraft(
    val name: String,
    val quantity: String,
    val unit: MeasurementUnit,
    val category: ShoppingCategory
)

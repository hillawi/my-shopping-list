package com.goldenmoonsolutions.myshoppinglist.domain

enum class ShoppingCategory(val label: String, val order: Int) {
    PRODUCE("Produce", 1),
    DAIRY("Dairy", 2),
    MEAT("Meat", 3),
    FROZEN("Frozen", 4),
    PANTRY("Pantry", 5),
    HOUSEHOLD("Household", 6),
    GENERAL("General", 99);

    companion object {
        fun fromString(value: String?) = ShoppingCategory.entries.find { it.name == value } ?: GENERAL
    }
}
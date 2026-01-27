package com.goldenmoonsolutions.myshoppinglist.domain

data class ShoppingItem(
    val id: Int,
    var name: String,
    var quantity: Int,
    var isChecked: Boolean = false
)